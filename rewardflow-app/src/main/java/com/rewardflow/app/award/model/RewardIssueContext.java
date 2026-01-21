package com.rewardflow.app.award.model;

import java.time.LocalDate;

/** 特定用户+场景+业务日期发放奖励的context信息 */
public class RewardIssueContext {

  private String userId;
  private String scene;
  private LocalDate bizDate;
  private Integer totalDuration;
  private String prizeCode;
  private String ruleVersion;
  private String traceId;

  public String getUserId() {
    return userId;
  }

  public RewardIssueContext setUserId(String userId) {
    this.userId = userId;
    return this;
  }

  public String getScene() {
    return scene;
  }

  public RewardIssueContext setScene(String scene) {
    this.scene = scene;
    return this;
  }

  public LocalDate getBizDate() {
    return bizDate;
  }

  public RewardIssueContext setBizDate(LocalDate bizDate) {
    this.bizDate = bizDate;
    return this;
  }

  public Integer getTotalDuration() {
    return totalDuration;
  }

  public RewardIssueContext setTotalDuration(Integer totalDuration) {
    this.totalDuration = totalDuration;
    return this;
  }

  public String getPrizeCode() {
    return prizeCode;
  }

  public RewardIssueContext setPrizeCode(String prizeCode) {
    this.prizeCode = prizeCode;
    return this;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public RewardIssueContext setRuleVersion(String ruleVersion) {
    this.ruleVersion = ruleVersion;
    return this;
  }

  public String getTraceId() {
    return traceId;
  }

  public RewardIssueContext setTraceId(String traceId) {
    this.traceId = traceId;
    return this;
  }
}
