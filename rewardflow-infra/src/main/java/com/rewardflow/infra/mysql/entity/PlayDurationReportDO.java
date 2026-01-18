package com.rewardflow.infra.mysql.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object mapped to table: play_duration_report.
 */
public class PlayDurationReportDO {

  private Long id;
  private String userId;
  private String soundId;
  private String bizScene;
  private Integer duration;
  private Long syncTime;
  private LocalDate bizDate;
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

  public String getSoundId() {
    return soundId;
  }

  public void setSoundId(String soundId) {
    this.soundId = soundId;
  }

  public String getBizScene() {
    return bizScene;
  }

  public void setBizScene(String bizScene) {
    this.bizScene = bizScene;
  }

  public Integer getDuration() {
    return duration;
  }

  public void setDuration(Integer duration) {
    this.duration = duration;
  }

  public Long getSyncTime() {
    return syncTime;
  }

  public void setSyncTime(Long syncTime) {
    this.syncTime = syncTime;
  }

  public LocalDate getBizDate() {
    return bizDate;
  }

  public void setBizDate(LocalDate bizDate) {
    this.bizDate = bizDate;
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
