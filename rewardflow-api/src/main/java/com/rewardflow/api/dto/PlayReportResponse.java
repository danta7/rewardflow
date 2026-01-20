package com.rewardflow.api.dto;

/**
 * 播放时长上报接口响应对象
 *
 */
public class PlayReportResponse {
  private boolean accepted;
  private boolean duplicate;
  private Long reportId;
  private String bizDate;
  private String traceId;

  // 聚合得到的当日累计总时长
  private Integer totalDuration;

  // 本次请求中聚合出来的新增时长（单位：秒）如果重复上报或上报乱序，可能为0
  private Integer deltaDuration;

  /** 命中的规则版本 */
  private String hitRuleVersion;

  /** 是否命中灰度规则 (true/false). */
  private Boolean grayHit;

  /** 奖励计划列表（仅预览，暂无下游） */
  private java.util.List<RewardPlanItem> awardPlans;  // 计划发表列表

  /** 每个阶段的奖励计划项 */
  public static class RewardPlanItem {
    private Integer stage;
    private Integer threshold;
    private Integer amount;
    private String outBizNo;

    /** 检查奖励是否已经持久化（已插入 reward_flow） */
    private Boolean issued;

    /** 如果已持久化，则为 reward_flow 主键 */
    private Long flowId;

    /** 如果已持久化，则为 outbox event id */
    private String eventId;

    /** 奖励发放状态 */
    private String issueStatus;

    public Integer getStage() {
      return stage;
    }

    public void setStage(Integer stage) {
      this.stage = stage;
    }

    public Integer getThreshold() {
      return threshold;
    }

    public void setThreshold(Integer threshold) {
      this.threshold = threshold;
    }

    public Integer getAmount() {
      return amount;
    }

    public void setAmount(Integer amount) {
      this.amount = amount;
    }

    public String getOutBizNo() {
      return outBizNo;
    }

    public void setOutBizNo(String outBizNo) {
      this.outBizNo = outBizNo;
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
  }

  public boolean isAccepted() {
    return accepted;
  }

  public void setAccepted(boolean accepted) {
    this.accepted = accepted;
  }

  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(boolean duplicate) {
    this.duplicate = duplicate;
  }

  public Long getReportId() {
    return reportId;
  }

  public void setReportId(Long reportId) {
    this.reportId = reportId;
  }

  public String getBizDate() {
    return bizDate;
  }

  public void setBizDate(String bizDate) {
    this.bizDate = bizDate;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public Integer getTotalDuration() {
    return totalDuration;
  }

  public void setTotalDuration(Integer totalDuration) {
    this.totalDuration = totalDuration;
  }

  public Integer getDeltaDuration() {
    return deltaDuration;
  }

  public void setDeltaDuration(Integer deltaDuration) {
    this.deltaDuration = deltaDuration;
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

  public java.util.List<RewardPlanItem> getAwardPlans() {
    return awardPlans;
  }

  public void setAwardPlans(java.util.List<RewardPlanItem> awardPlans) {
    this.awardPlans = awardPlans;
  }
}
