package com.rewardflow.domain.feature.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature switches loaded from Nacos.
 *
 * <p>运行时回滚、降级 + 灰度发布；全局开关 + 场景覆盖
 */
public class FeatureCenterConfig {

  /** 全局开关 */
  private Boolean awardIssueEnabled = true;   // 是否允许发奖、核心链路开关
  private Boolean outboxPublishEnabled = true;  // 是否允许 outbox 扫描并发 MQ（防止消息爆炸/下游扛不住）
  private Boolean reconcileEnabled = true;    // 是否开启对账/补偿任务
  private Boolean ruleSnapshotEnabled = true; // 是否把规则快照落 mongo
  private Boolean ruleSimulationEnabled = true; // 是否允许规则模拟（运营）

  /** 按场景覆盖 (optional) 
   * e.g. {"audio_play": { "awardIssueEnabled": false }, "SCENE_B": { "outboxPublishEnabled": false } }
  */
  private Map<String, SceneFeature> scenes = new HashMap<>();

  public Boolean getAwardIssueEnabled() {
    return awardIssueEnabled;
  }

  public void setAwardIssueEnabled(Boolean awardIssueEnabled) {
    this.awardIssueEnabled = awardIssueEnabled;
  }

  public Boolean getOutboxPublishEnabled() {
    return outboxPublishEnabled;
  }

  public void setOutboxPublishEnabled(Boolean outboxPublishEnabled) {
    this.outboxPublishEnabled = outboxPublishEnabled;
  }

  public Boolean getReconcileEnabled() {
    return reconcileEnabled;
  }

  public void setReconcileEnabled(Boolean reconcileEnabled) {
    this.reconcileEnabled = reconcileEnabled;
  }

  public Boolean getRuleSnapshotEnabled() {
    return ruleSnapshotEnabled;
  }

  public void setRuleSnapshotEnabled(Boolean ruleSnapshotEnabled) {
    this.ruleSnapshotEnabled = ruleSnapshotEnabled;
  }

  public Boolean getRuleSimulationEnabled() {
    return ruleSimulationEnabled;
  }

  public void setRuleSimulationEnabled(Boolean ruleSimulationEnabled) {
    this.ruleSimulationEnabled = ruleSimulationEnabled;
  }

  public Map<String, SceneFeature> getScenes() {
    return scenes;
  }

  public void setScenes(Map<String, SceneFeature> scenes) {
    this.scenes = scenes;
  }

  public static class SceneFeature {
    private Boolean awardIssueEnabled;
    private Boolean outboxPublishEnabled;
    private Boolean reconcileEnabled;
    private Boolean ruleSnapshotEnabled;
    private Boolean ruleSimulationEnabled;

    public Boolean getAwardIssueEnabled() {
      return awardIssueEnabled;
    }

    public void setAwardIssueEnabled(Boolean awardIssueEnabled) {
      this.awardIssueEnabled = awardIssueEnabled;
    }

    public Boolean getOutboxPublishEnabled() {
      return outboxPublishEnabled;
    }

    public void setOutboxPublishEnabled(Boolean outboxPublishEnabled) {
      this.outboxPublishEnabled = outboxPublishEnabled;
    }

    public Boolean getReconcileEnabled() {
      return reconcileEnabled;
    }

    public void setReconcileEnabled(Boolean reconcileEnabled) {
      this.reconcileEnabled = reconcileEnabled;
    }

    public Boolean getRuleSnapshotEnabled() {
      return ruleSnapshotEnabled;
    }

    public void setRuleSnapshotEnabled(Boolean ruleSnapshotEnabled) {
      this.ruleSnapshotEnabled = ruleSnapshotEnabled;
    }

    public Boolean getRuleSimulationEnabled() {
      return ruleSimulationEnabled;
    }

    public void setRuleSimulationEnabled(Boolean ruleSimulationEnabled) {
      this.ruleSimulationEnabled = ruleSimulationEnabled;
    }
  }
}
