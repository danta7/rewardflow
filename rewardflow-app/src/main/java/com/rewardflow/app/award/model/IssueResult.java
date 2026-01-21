package com.rewardflow.app.award.model;

/** 单个阶段的发送结果 (由 outBizNo 键制定) */
public class IssueResult {

  private String outBizNo;
  private Integer stage;
  private Integer amount;
  /** 此次是否创建 reward_flow 记录（true）或遇幂等重复（false） */
  private Boolean issued;
  private Long flowId;
  private String eventId;
  /** CREATED | DUPLICATE | FAILED */
  private String issueStatus;
  private String error;

  public String getOutBizNo() {
    return outBizNo;
  }

  public void setOutBizNo(String outBizNo) {
    this.outBizNo = outBizNo;
  }

  public Integer getStage() {
    return stage;
  }

  public void setStage(Integer stage) {
    this.stage = stage;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public Boolean getIssued() {
    return issued;
  }

  public void setIssued(Boolean issued) {
    this.issued = issued;
  }

  public Long getFlowId() {
    return flowId;
  }

  public void setFlowId(Long flowId) {
    this.flowId = flowId;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getIssueStatus() {
    return issueStatus;
  }

  public void setIssueStatus(String issueStatus) {
    this.issueStatus = issueStatus;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}
