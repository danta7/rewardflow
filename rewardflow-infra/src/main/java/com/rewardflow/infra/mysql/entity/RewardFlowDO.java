package com.rewardflow.infra.mysql.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Data object mapped to table: reward_flow. */
public class RewardFlowDO {
  private Long id;
  private String userId;
  private String bizScene;
  private String prizeCode;
  private LocalDate prizeDate;
  private Integer prizeStage;
  private Integer prizeAmount;
  private String outBizNo;
  private String ruleVersion;
  private String traceId;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getBizScene() {
    return bizScene;
  }

  public void setBizScene(String bizScene) {
    this.bizScene = bizScene;
  }

  public String getPrizeCode() {
    return prizeCode;
  }

  public void setPrizeCode(String prizeCode) {
    this.prizeCode = prizeCode;
  }

  public LocalDate getPrizeDate() {
    return prizeDate;
  }

  public void setPrizeDate(LocalDate prizeDate) {
    this.prizeDate = prizeDate;
  }

  public Integer getPrizeStage() {
    return prizeStage;
  }

  public void setPrizeStage(Integer prizeStage) {
    this.prizeStage = prizeStage;
  }

  public Integer getPrizeAmount() {
    return prizeAmount;
  }

  public void setPrizeAmount(Integer prizeAmount) {
    this.prizeAmount = prizeAmount;
  }

  public String getOutBizNo() {
    return outBizNo;
  }

  public void setOutBizNo(String outBizNo) {
    this.outBizNo = outBizNo;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public void setRuleVersion(String ruleVersion) {
    this.ruleVersion = ruleVersion;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }
}
