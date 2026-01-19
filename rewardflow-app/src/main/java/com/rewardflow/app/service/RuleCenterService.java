package com.rewardflow.app.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.app.config.RuleCenterProperties;
import com.rewardflow.domain.rule.model.RuleCenterConfig;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 加载本地兜底规则 →（如果 enabled=true）再去 Nacos 拉最新 JSON 规则覆盖 → 并注册监听器，Nacos 配置变了就自动热更新到内存缓存里
 */
@Service
public class RuleCenterService {

  private static final Logger log = LoggerFactory.getLogger(RuleCenterService.class);

  private final RuleCenterProperties props;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;
  // 并发安全的替换配置缓存
  private final AtomicReference<RuleCenterConfig> cache = new AtomicReference<>();

  // nacos 配置服务客户端
  private volatile ConfigService configService;

  public RuleCenterService(RuleCenterProperties props, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void init() {
    initInternal();
  }

  public RuleCenterConfig currentConfig() {
    RuleCenterConfig cfg = cache.get();
    if (cfg == null) {
      // 不会发生
      cfg = loadFallback();
      cache.set(cfg);
    }
    return cfg;
  }

  private void initInternal() {
    // Always load fallback first so the service is usable even when Nacos is down
    RuleCenterConfig fallback = loadFallback();
    cache.set(fallback);

    // 如果没启用 Nacos 直接用 fullback
    if (!props.isEnabled()) {
      log.info("rule-center disabled, using fallback resource: {}", props.getFallbackResource());
      return;
    }
    try {
      Properties nacosProps = new Properties();
      nacosProps.put("serverAddr", props.getServerAddr());
      if (props.getUsername() != null && !props.getUsername().isBlank()) {
        nacosProps.put("username", props.getUsername());
      }
      if (props.getPassword() != null && !props.getPassword().isBlank()) {
        nacosProps.put("password", props.getPassword());
      }
      this.configService = NacosFactory.createConfigService(nacosProps);
      // 启动加载
      String configStr = configService.getConfig(props.getDataId(), props.getGroup(), props.getTimeoutMs());
      if (configStr != null && !configStr.isBlank()) {
        parseAndSet(configStr);
        log.info("rule-center loaded from nacos dataId={}, group={}", props.getDataId(), props.getGroup());
      } else {
        log.warn("rule-center empty in nacos (dataId={}, group={}), keep fallback", props.getDataId(), props.getGroup());
      }

      // 注册监听器 hot update
      configService.addListener(props.getDataId(), props.getGroup(), new AbstractListener() {
        @Override
        public void receiveConfigInfo(String configInfo) {
          if (configInfo == null || configInfo.isBlank()) {
            log.warn("received empty rule config from nacos, ignoring");
            return;
          }
          parseAndSet(configInfo);
          log.info("rule-center refreshed from nacos dataId={}, group={}", props.getDataId(), props.getGroup());
        }
      });
    } catch (Exception e) {
      log.warn("rule-center init failed, keep fallback. serverAddr={}", props.getServerAddr(), e);
    }
  }

  private void parseAndSet(String configStr) {
    try {
      RuleCenterConfig cfg = objectMapper.readValue(configStr, RuleCenterConfig.class);
      if (cfg == null || cfg.getScenes() == null || cfg.getScenes().isEmpty()) {
        log.warn("parsed rule config is empty, ignoring");
        return;
      }
      cache.set(cfg);
    } catch (Exception e) {
      log.warn("failed to parse rule config json, ignoring update", e);
    }
  }

  private RuleCenterConfig loadFallback() {
    try {
      Resource res = resourceLoader.getResource(props.getFallbackResource());
      try (InputStream in = res.getInputStream()) {
        byte[] bytes = in.readAllBytes();
        String json = new String(bytes, StandardCharsets.UTF_8);
        RuleCenterConfig cfg = objectMapper.readValue(json, RuleCenterConfig.class);
        if (cfg != null) {
          return cfg;
        }
      }
    } catch (Exception e) {
      log.error("failed to load fallback rule resource: {}", props.getFallbackResource(), e);
    }
    return new RuleCenterConfig();
  }
}
