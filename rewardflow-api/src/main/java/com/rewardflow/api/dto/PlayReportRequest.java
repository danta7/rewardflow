package com.rewardflow.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PlayReportRequest {

  @NotBlank
  private String userId;

  @NotBlank
  private String soundId;

  @NotNull
  @Min(1)
  private Integer duration;

  @NotNull
  private Long syncTime;

  @NotBlank
  private String scene;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSoundId() { return soundId; }
  public void setSoundId(String soundId) { this.soundId = soundId; }
  public Integer getDuration() { return duration; }
  public void setDuration(Integer duration) { this.duration = duration; }
  public Long getSyncTime() { return syncTime; }
  public void setSyncTime(Long syncTime) { this.syncTime = syncTime; }
  public String getScene() { return scene; }
  public void setScene(String scene) { this.scene = scene; }
}
