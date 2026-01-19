package com.rewardflow.infra.mysql.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object mapped to table: user_play_daily.
 */
public class UserPlayDailyDO {
  private Long id;
  private String userId;
  private String bizScene;
  private LocalDate bizDate;
  private Integer totalDuration;
  private Long lastSyncTime;
  private Integer version;
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

  public LocalDate getBizDate() {
    return bizDate;
  }

  public void setBizDate(LocalDate bizDate) {
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
