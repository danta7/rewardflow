package com.rewardflow.domain.rule.model;

import java.util.List;
import java.util.Map;

/**
 * Rule-center configuration (loaded from Nacos as JSON).
 *
 * <p>Root object example:
 * <pre>
{
  "scenes": {
    "audio_play": {
      "activeRuleVersion": "v1",
      "gray": {
        "enabled": false,
        "expr": "uid < 10",
        "targetRuleVersion": "v2"
      },
      "rules": [
        {
          "ruleVersion": "v1",
          "stages": [
            { "stage": 1, "threshold": 160, "amount": 1, "prizeCode": "COIN" },
          ]
        },
        {
          "ruleVersion": "v2",
          "stages": [
            { "stage": 1, "threshold": 100, "amount": 2, "prizeCode": "COIN" },
          ]
        }
      ]
    }
  }
}
 * </pre>
 */
public class RuleCenterConfig {

  private Map<String, SceneRuleSet> scenes;

  public Map<String, SceneRuleSet> getScenes() {
    return scenes;
  }

  public void setScenes(Map<String, SceneRuleSet> scenes) {
    this.scenes = scenes;
  }

  public static class SceneRuleSet {
    private String activeRuleVersion;
    private GrayRule gray;
    private List<RuleVersion> rules;

    public String getActiveRuleVersion() {
      return activeRuleVersion;
    }

    public void setActiveRuleVersion(String activeRuleVersion) {
      this.activeRuleVersion = activeRuleVersion;
    }

    public GrayRule getGray() {
      return gray;
    }

    public void setGray(GrayRule gray) {
      this.gray = gray;
    }

    public List<RuleVersion> getRules() {
      return rules;
    }

    public void setRules(List<RuleVersion> rules) {
      this.rules = rules;
    }
  }

  public static class GrayRule {
    private boolean enabled;
    private String expr;
    private String targetRuleVersion;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getExpr() {
      return expr;
    }

    public void setExpr(String expr) {
      this.expr = expr;
    }

    public String getTargetRuleVersion() {
      return targetRuleVersion;
    }

    public void setTargetRuleVersion(String targetRuleVersion) {
      this.targetRuleVersion = targetRuleVersion;
    }
  }

  public static class RuleVersion {
    private String ruleVersion;
    private List<StageRule> stages;

    public String getRuleVersion() {
      return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
      this.ruleVersion = ruleVersion;
    }

    public List<StageRule> getStages() {
      return stages;
    }

    public void setStages(List<StageRule> stages) {
      this.stages = stages;
    }
  }

  public static class StageRule {
    private int stage;
    private int threshold;
    private int amount;
    /** 可选 prizeCode 如果为空 使用默认 */
    private String prizeCode;

    public int getStage() {
      return stage;
    }

    public void setStage(int stage) {
      this.stage = stage;
    }

    public int getThreshold() {
      return threshold;
    }

    public void setThreshold(int threshold) {
      this.threshold = threshold;
    }

    public int getAmount() {
      return amount;
    }

    public void setAmount(int amount) {
      this.amount = amount;
    }

    public String getPrizeCode() {
      return prizeCode;
    }

    public void setPrizeCode(String prizeCode) {
      this.prizeCode = prizeCode;
    }
  }
}
