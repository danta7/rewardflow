package com.rewardflow.infra.mysql.entity;

/**
 * Aggregation result for incremental play duration.
 */
public class PlayReportAggResult {
  private Integer deltaDuration;
  private Long maxSyncTime;

  public Integer getDeltaDuration() {
    return deltaDuration;
  }

  public void setDeltaDuration(Integer deltaDuration) {
    this.deltaDuration = deltaDuration;
  }

  public Long getMaxSyncTime() {
    return maxSyncTime;
  }

  public void setMaxSyncTime(Long maxSyncTime) {
    this.maxSyncTime = maxSyncTime;
  }
}
