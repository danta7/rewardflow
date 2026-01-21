package com.rewardflow.app.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.app.config.FeatureCenterProperties;
import com.rewardflow.domain.feature.model.FeatureCenterConfig;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Loads and caches feature switches from Nacos (JSON)  Falls back to a local bundled JSON.
 * feature 开关中心的客户端
 * 缓存 + fallback + 监听热更新 + 解析失败保护
 */
@Service
public class FeatureCenterService {

  private static final Logger log = LoggerFactory.getLogger(FeatureCenterService.class);

  private final FeatureCenterProperties props;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  // 线程安全
  private final AtomicReference<FeatureCenterConfig> cache = new AtomicReference<>();
  private volatile ConfigService configService;

  public FeatureCenterService(FeatureCenterProperties props, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void init() {
    initInternal();
  }

  // 对外提供的能力
  public FeatureCenterConfig currentConfig() {
    FeatureCenterConfig cfg = cache.get();
    if (cfg == null) {
      cfg = loadFallback();
      cache.set(cfg);
    }
    return cfg;
  }

  /**
   * 合并策略：按 scene 合并最终开关
   * 先拿全局 config，再覆盖 scene 级别的配置
   * 最后对每个开关，采用优先级 scene 配置 > 全局配置 > 默认 true 的策略
   * 这样即使配置缺失，也能保证大部分功能是开启的，避免影响线上业务
   */
  public FeatureCenterConfig.SceneFeature effectiveForScene(String scene) {
    FeatureCenterConfig cfg = currentConfig();
    FeatureCenterConfig.SceneFeature eff = new FeatureCenterConfig.SceneFeature();
    FeatureCenterConfig.SceneFeature per = (cfg.getScenes() == null) ? null : cfg.getScenes().get(scene);

    eff.setAwardIssueEnabled(firstNonNull(per == null ? null : per.getAwardIssueEnabled(), cfg.getAwardIssueEnabled(), Boolean.TRUE));
    eff.setOutboxPublishEnabled(firstNonNull(per == null ? null : per.getOutboxPublishEnabled(), cfg.getOutboxPublishEnabled(), Boolean.TRUE));
    eff.setReconcileEnabled(firstNonNull(per == null ? null : per.getReconcileEnabled(), cfg.getReconcileEnabled(), Boolean.TRUE));
    eff.setRuleSnapshotEnabled(firstNonNull(per == null ? null : per.getRuleSnapshotEnabled(), cfg.getRuleSnapshotEnabled(), Boolean.TRUE));
    eff.setRuleSimulationEnabled(firstNonNull(per == null ? null : per.getRuleSimulationEnabled(), cfg.getRuleSimulationEnabled(), Boolean.TRUE));
    return eff;
  }

  private static Boolean firstNonNull(Boolean... vals) {
    if (vals == null) return null;
    for (Boolean v : vals) {
      if (v != null) return v;
    }
    return null;
  }

  private void initInternal() {
    // 先加载 fallback
    cache.set(loadFallback());

    // enabled=false 不会拉 Nacos
    if (!props.isEnabled()) {
      log.info("feature-center disabled, using fallback resource: {}", props.getFallbackResource());
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

      // 主动拉一次配置覆盖 fallback
      String configStr = configService.getConfig(props.getDataId(), props.getGroup(), props.getTimeoutMs());
      if (configStr != null && !configStr.isBlank()) {
        parseAndSet(configStr);
        log.info("feature-center loaded from nacos dataId={}, group={}", props.getDataId(), props.getGroup());
      } else {
        log.warn("feature-center empty in nacos (dataId={}, group={}), keep fallback", props.getDataId(), props.getGroup());
      }

      // 注册监听 热更新
      configService.addListener(props.getDataId(), props.getGroup(), new AbstractListener() {
        @Override
        public void receiveConfigInfo(String configInfo) {
          if (configInfo == null || configInfo.isBlank()) {
            log.warn("received empty feature config from nacos, ignoring");
            return;
          }
          parseAndSet(configInfo);
          log.info("feature-center updated from nacos dataId={}, group={}", props.getDataId(), props.getGroup());
        }
      });

    } catch (Exception e) {
      log.warn("feature-center init failed, keep fallback", e);
    }
  }

  private void parseAndSet(String configStr) {
    try {
      FeatureCenterConfig cfg = objectMapper.readValue(configStr, FeatureCenterConfig.class);
      cfg = validateAndSanitize(cfg);
      if (cfg == null) {
        // 校验不通过，保持当前 cache 不变
        log.warn("parsed feature config is null, ignoring");
        return;
      }
      cache.set(cfg);
    } catch (Exception e) {
      log.warn("failed to parse feature config json, ignoring update", e);
    }
  }

  private FeatureCenterConfig loadFallback() {
    try {
      Resource resource = resourceLoader.getResource(props.getFallbackResource());
      if (!resource.exists()) {
        log.warn("feature fallback resource not found: {}", props.getFallbackResource());
        return new FeatureCenterConfig();
      }
      try (InputStream is = resource.getInputStream()) {
        String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        FeatureCenterConfig cfg = objectMapper.readValue(s, FeatureCenterConfig.class);
        cfg = validateAndSanitize(cfg);
        return cfg == null ? new FeatureCenterConfig() : cfg;
      }
    } catch (Exception e) {
      log.warn("failed to load feature fallback resource, using defaults", e);
      return new FeatureCenterConfig();
    }
  }

  /**
   * 校验并清洗配置：
   * - 配置整体不能“全是空”(global 开关全 null 且 scenes 也为空)
   * - scenes 的 key 必须合法（不允许 blank / 过长）
   * - scenes 数量做上限保护（防止误推超大配置压垮内存）
   * - 过滤 value = null / 空 SceneFeature（全字段 null）的条目
   *
   * @return 清洗后的 cfg；若整体不合法则返回 null（表示忽略本次更新）
   */
  private FeatureCenterConfig validateAndSanitize(FeatureCenterConfig cfg) {
    if (cfg == null) {
      return null;
    }

    // 防止“全空配置”覆盖掉当前 cache
    boolean allGlobalNull =
        cfg.getAwardIssueEnabled() == null
            && cfg.getOutboxPublishEnabled() == null
            && cfg.getReconcileEnabled() == null
            && cfg.getRuleSnapshotEnabled() == null
            && cfg.getRuleSimulationEnabled() == null;

    Map<String, FeatureCenterConfig.SceneFeature> scenes = cfg.getScenes();
    boolean scenesEmpty = (scenes == null || scenes.isEmpty());

    if (allGlobalNull && scenesEmpty) {
      log.warn("feature-center config invalid: all global switches are null and scenes is empty. ignore update.");
      return null;
    }

    // scenes 上限保护（按业务规模调整）
    if (scenes != null && scenes.size() > 2000) {
      log.warn("feature-center config invalid: too many scenes={}, ignore update.", scenes.size());
      return null;
    }

    // 清洗 scenes：key 合法 + value 不为空 + 不是“空 SceneFeature”
    if (scenes != null && !scenes.isEmpty()) {
      Map<String, FeatureCenterConfig.SceneFeature> clean = new HashMap<>();
      for (Map.Entry<String, FeatureCenterConfig.SceneFeature> e : scenes.entrySet()) {
        String scene = e.getKey();
        FeatureCenterConfig.SceneFeature sf = e.getValue();

        if (scene == null || scene.isBlank() || scene.length() > 64) {
          log.warn("feature-center config contains invalid scene key='{}', skip it.", scene);
          continue;
        }
        if (sf == null) {
          log.warn("feature-center config scene='{}' value is null, skip it.", scene);
          continue;
        }
        if (isEmptySceneFeature(sf)) {
          // 空对象没意义：等同于不配置该 scene
          log.warn("feature-center config scene='{}' is empty (all fields null), skip it.", scene);
          continue;
        }

        clean.put(scene, sf);
      }
      cfg.setScenes(clean);
    }

    return cfg;
  }

  private boolean isEmptySceneFeature(FeatureCenterConfig.SceneFeature sf) {
    return sf.getAwardIssueEnabled() == null
        && sf.getOutboxPublishEnabled() == null
        && sf.getReconcileEnabled() == null
        && sf.getRuleSnapshotEnabled() == null
        && sf.getRuleSimulationEnabled() == null;
  }

}
