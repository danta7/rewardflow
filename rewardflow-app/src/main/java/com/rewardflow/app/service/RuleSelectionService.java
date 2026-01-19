package com.rewardflow.app.service;

import com.rewardflow.domain.rule.model.RuleCenterConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.JexlExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从规则中心拿到某个 scene 的规则 → 按灰度表达式（JEXL）决定使用版本 → 返回命中的版本 + 具体规则内容
 */
@Service
public class RuleSelectionService {

  private static final Logger log = LoggerFactory.getLogger(RuleSelectionService.class);

  private final RuleCenterService ruleCenterService;
  private final JexlEngine jexl;

  public RuleSelectionService(RuleCenterService ruleCenterService) {
    this.ruleCenterService = ruleCenterService;
    this.jexl = new JexlBuilder()
        .strict(true)
        .silent(true)
        .cache(256)
        .create();
  }

  public RuleSelectResult select(String scene, String userId) {
    RuleCenterConfig cfg = ruleCenterService.currentConfig();
    if (cfg.getScenes() == null || cfg.getScenes().get(scene) == null) {
      throw new IllegalArgumentException("no rules for scene: " + scene);
    }
    RuleCenterConfig.SceneRuleSet sceneRules = cfg.getScenes().get(scene);

    String active = sceneRules.getActiveRuleVersion();
    // 默认命中 active 版本
    String hit = active;
    boolean grayHit = false;
    RuleCenterConfig.GrayRule gray = sceneRules.getGray();
    
    if (gray != null && gray.isEnabled() && gray.getExpr() != null && !gray.getExpr().isBlank()) {
      int uidBucket = bucketOf(userId);
      Map<String, Object> vars = new HashMap<>();
      vars.put("uid", uidBucket);
      vars.put("userId", userId);
      if (evalBool(gray.getExpr(), vars)) {
        String target = gray.getTargetRuleVersion();
        if (target != null && !target.isBlank()) {
          hit = target;
          grayHit = true;
        }
      }
    }

    RuleCenterConfig.RuleVersion rv = findRuleVersion(sceneRules, hit);
    if (rv == null && !hit.equals(active)) {
      // 灰度版本没有找到 退回active
      rv = findRuleVersion(sceneRules, active);
      hit = active;
      grayHit = false;
    }
    if (rv == null) {
      throw new IllegalArgumentException("ruleVersion not found for scene=" + scene + ", version=" + hit);
    }
    RuleSelectResult r = new RuleSelectResult();
    r.scene = scene;
    r.hitRuleVersion = hit;
    r.grayHit = grayHit;
    r.ruleVersion = rv;
    return r;
  }

  private RuleCenterConfig.RuleVersion findRuleVersion(RuleCenterConfig.SceneRuleSet sceneRules, String version) {
    if (sceneRules.getRules() == null) return null;
    for (RuleCenterConfig.RuleVersion rv : sceneRules.getRules()) {
      if (rv != null && version != null && version.equals(rv.getRuleVersion())) {
        return rv;
      }
    }
    return null;
  }

  private boolean evalBool(String expr, Map<String, Object> vars) {
    try {
      JexlExpression e = jexl.createExpression(expr);
      JexlContext ctx = new MapContext(vars);
      Object v = e.evaluate(ctx);
      if (v instanceof Boolean b) return b;
      if (v == null) return false;
      if (v instanceof Number n) return n.intValue() != 0;
      return Boolean.parseBoolean(v.toString());
    } catch (Exception ex) {
      log.warn("gray expr evaluate failed, expr={} vars={}", expr, vars, ex);
      return false;
    }
  }

  /**
   * 业务灰度分桶：尽可能使用最后两位数字，否则进行哈希处理。
   */
  private int bucketOf(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    String s = userId.trim();
    int len = s.length();
    if (len >= 2) {
      String tail2 = s.substring(len - 2);
      if (tail2.chars().allMatch(Character::isDigit)) {
        return Integer.parseInt(tail2);
      }
    }
    return Math.abs(s.hashCode()) % 100;
  }

  public static class RuleSelectResult {
    private String scene;
    private String hitRuleVersion;
    private boolean grayHit;
    private RuleCenterConfig.RuleVersion ruleVersion;

    public String getScene() {
      return scene;
    }

    public String getHitRuleVersion() {
      return hitRuleVersion;
    }

    public boolean isGrayHit() {
      return grayHit;
    }

    public RuleCenterConfig.RuleVersion getRuleVersion() {
      return ruleVersion;
    }
  }
}
