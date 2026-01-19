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
}
