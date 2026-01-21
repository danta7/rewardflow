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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final FeatureCenterService featureCenterService;
  private final RuleSnapshotService ruleSnapshotService;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;
  // 并发安全的替换配置缓存
  private final AtomicReference<RuleCenterConfig> cache = new AtomicReference<>();

  // nacos 配置服务客户端
  private volatile ConfigService configService;

  public RuleCenterService(RuleCenterProperties props, ObjectMapper objectMapper, ResourceLoader resourceLoader,
      FeatureCenterService featureCenterService,
      RuleSnapshotService ruleSnapshotService) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
    this.featureCenterService = featureCenterService;
    this.ruleSnapshotService = ruleSnapshotService;
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
      // 规则快照 (best-effort)
      Boolean enabled = featureCenterService.currentConfig().getRuleSnapshotEnabled();
      if (enabled != null && enabled) {
        try {
          // cache was empty so we are using fallback here
          ruleSnapshotService.snapshotIfNew(objectMapper.writeValueAsString(cfg), "FALLBACK");
        } catch (Exception ignore) {
          // best-effort
        }
      }
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

      String err = validateRuleConfig(cfg);
      if (err != null) {
        log.warn("rule-center config invalid,ignoring update,reason={}", err);
        return;
      }
      
      cache.set(cfg);
      // 规则快照 (best-effort)
      Boolean enabled = featureCenterService.currentConfig().getRuleSnapshotEnabled();
      if (enabled != null && enabled) {
        ruleSnapshotService.snapshotIfNew(configStr, "NACOS");
      }
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

  /**
   * return null if ok; otherwise return error message.
   * 线上策略：校验不通过 -> 忽略本次更新，继续用旧缓存/兜底。
   */
  private static String validateRuleConfig(RuleCenterConfig cfg) {
    if (cfg == null) return "cfg is null";
    if (cfg.getScenes() == null || cfg.getScenes().isEmpty()) return "scenes is empty";

    for (Map.Entry<String, RuleCenterConfig.SceneRuleSet> e : cfg.getScenes().entrySet()) {
      String scene = e.getKey();
      RuleCenterConfig.SceneRuleSet set = e.getValue();

      if (scene == null || scene.isBlank()) return "scene key is blank";
      if (set == null) return "sceneRuleSet is null for scene=" + scene;

      String active = set.getActiveRuleVersion();
      if (active == null || active.isBlank()) return "activeRuleVersion is blank for scene=" + scene;

      List<RuleCenterConfig.RuleVersion> rules = set.getRules();
      if (rules == null || rules.isEmpty()) return "rules is empty for scene=" + scene;

      // ruleVersion 唯一 + 非空
      Set<String> versions = new HashSet<>();
      for (RuleCenterConfig.RuleVersion rv : rules) {
        if (rv == null) return "ruleVersion item is null for scene=" + scene;
        String v = rv.getRuleVersion();
        if (v == null || v.isBlank()) return "ruleVersion is blank for scene=" + scene;
        if (!versions.add(v)) return "duplicate ruleVersion=" + v + " for scene=" + scene;

        String stageErr = validateStages(scene, v, rv.getStages());
        if (stageErr != null) return stageErr;
      }

      // active 必须存在
      if (!versions.contains(active)) {
        return "activeRuleVersion=" + active + " not found in rules for scene=" + scene;
      }

      // gray 规则校验（enabled 才强校验）
      RuleCenterConfig.GrayRule gray = set.getGray();
      if (gray != null && gray.isEnabled()) {
        String target = gray.getTargetRuleVersion();
        if (target == null || target.isBlank()) return "gray.targetRuleVersion is blank for scene=" + scene;
        if (!versions.contains(target)) {
          return "gray.targetRuleVersion=" + target + " not found in rules for scene=" + scene;
        }
        String expr = gray.getExpr();
        if (expr == null || expr.isBlank()) return "gray.expr is blank for scene=" + scene;
      }
    }

    return null;
  }

  private static String validateStages(String scene, String ruleVersion, List<RuleCenterConfig.StageRule> stages) {
    if (stages == null || stages.isEmpty()) {
      return "stages is empty for scene=" + scene + ", ruleVersion=" + ruleVersion;
    }

    // stage 去重 + 基本合法性
    Set<Integer> stageSet = new HashSet<>();
    // 为了校验 threshold 随 stage 递增：把 stages 按 stage 排序后检查
    List<RuleCenterConfig.StageRule> sorted = new ArrayList<>(stages);
    sorted.sort(java.util.Comparator.comparingInt(RuleCenterConfig.StageRule::getStage));

    int lastStage = -1;
    int lastThreshold = -1;

    for (RuleCenterConfig.StageRule s : sorted) {
      if (s == null) {
        return "stageRule is null for scene=" + scene + ", ruleVersion=" + ruleVersion;
      }

      int stage = s.getStage();
      int threshold = s.getThreshold();
      int amount = s.getAmount();

      if (stage <= 0) return "stage <= 0 for scene=" + scene + ", ruleVersion=" + ruleVersion;
      if (threshold <= 0) return "threshold <= 0 for stage=" + stage + ", scene=" + scene + ", ruleVersion=" + ruleVersion;
      if (amount < 0) return "amount < 0 for stage=" + stage + ", scene=" + scene + ", ruleVersion=" + ruleVersion;

      if (!stageSet.add(stage)) {
        return "duplicate stage=" + stage + " for scene=" + scene + ", ruleVersion=" + ruleVersion;
      }

      // stage 递增（排序后天然满足），这里主要校验 threshold 递增
      if (lastStage != -1) {
        if (threshold <= lastThreshold) {
          return "threshold not increasing: stage=" + stage + " threshold=" + threshold +
              " <= prevThreshold=" + lastThreshold + " for scene=" + scene + ", ruleVersion=" + ruleVersion;
        }
      }
      lastStage = stage;
      lastThreshold = threshold;
    }

    // 非致命：stage 不连续只 warn（不返回 err）
    // 如果想严格要求连续，可以把 warn 换成 return
    int min = sorted.get(0).getStage();
    int max = sorted.get(sorted.size() - 1).getStage();
    if ((max - min + 1) != sorted.size()) {
      // 这里拿不到 log（static），就不记录；也可以把 validateStages 改成非 static 用 log.warn
      // 或者返回一个特殊值让上层 log.warn
    }

    return null;
  }

}
