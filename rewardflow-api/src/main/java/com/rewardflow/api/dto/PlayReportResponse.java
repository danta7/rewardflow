package com.rewardflow.api.dto;

public class PlayReportResponse {
  private boolean accepted;
  private boolean duplicate;
  private Long reportId;
  private String bizDate;
  private String traceId;

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
}
