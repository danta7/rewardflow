package com.rewardflow.infra.mysql.entity;

import java.time.LocalDateTime;

/** 数据模型: reward_outbox. */
public class RewardOutboxDO {
  /** 主键: event_id. */
  private String eventId;
  private String outBizNo;    // 业务幂等号
  private String eventType;   
  private String payload;
  /** 奖励发放状态：0=PENDING, 1=SENT, 2=FAILED. */
  private Integer status;
  private Integer retryCount;   // 已重试次数
  private LocalDateTime nextRetryTime;  // 下次允许重试时间
  private String traceId;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getOutBizNo() {
    return outBizNo;
  }

  public void setOutBizNo(String outBizNo) {
    this.outBizNo = outBizNo;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public LocalDateTime getNextRetryTime() {
    return nextRetryTime;
  }

  public void setNextRetryTime(LocalDateTime nextRetryTime) {
    this.nextRetryTime = nextRetryTime;
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
