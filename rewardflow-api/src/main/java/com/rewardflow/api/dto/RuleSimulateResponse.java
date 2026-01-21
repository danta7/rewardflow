package com.rewardflow.api.dto;

import java.util.List;

public class RuleSimulateResponse {

  private String traceId;
  private String scene;
  private String userId;
  private String bizDate;

  private Integer totalDuration;

  private String hitRuleVersion;
  private Boolean grayHit;

  private List<PlayReportResponse.RewardPlanItem> awardPlans;

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getScene() {
    return scene;
  }

  public void setScene(String scene) {
    this.scene = scene;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getBizDate() {
    return bizDate;
  }

  public void setBizDate(String bizDate) {
    this.bizDate = bizDate;
  }

  public Integer getTotalDuration() {
    return totalDuration;
  }

  public void setTotalDuration(Integer totalDuration) {
    this.totalDuration = totalDuration;
  }

  public String getHitRuleVersion() {
    return hitRuleVersion;
  }

  public void setHitRuleVersion(String hitRuleVersion) {
    this.hitRuleVersion = hitRuleVersion;
  }

  public Boolean getGrayHit() {
    return grayHit;
  }

  public void setGrayHit(Boolean grayHit) {
    this.grayHit = grayHit;
  }

  public List<PlayReportResponse.RewardPlanItem> getAwardPlans() {
    return awardPlans;
  }

  public void setAwardPlans(List<PlayReportResponse.RewardPlanItem> awardPlans) {
    this.awardPlans = awardPlans;
  }
}
