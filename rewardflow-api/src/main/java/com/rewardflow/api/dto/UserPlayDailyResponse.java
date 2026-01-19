package com.rewardflow.api.dto;

// 播放时长每日汇总响应对象
public class UserPlayDailyResponse {
  private Long id;
  private String userId;
  private String scene;
  private String bizDate;
  private Integer totalDuration;
  private Long lastSyncTime;
  private Integer version;

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

  public String getScene() {
    return scene;
  }

  public void setScene(String scene) {
    this.scene = scene;
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

  public Long getLastSyncTime() {
    return lastSyncTime;
  }

  public void setLastSyncTime(Long lastSyncTime) {
    this.lastSyncTime = lastSyncTime;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
