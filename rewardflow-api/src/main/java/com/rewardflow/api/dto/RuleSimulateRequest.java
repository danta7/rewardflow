package com.rewardflow.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RuleSimulateRequest {

  @NotBlank
  private String scene;

  @NotBlank
  private String userId;

  /** 已累计总时长 */
  @NotNull
  @Min(0)
  private Integer totalDuration;

  /** YYYY-MM-DD */
  private String bizDate;

  public String getScene() {
    return scene;
  }

  public void setScene(String scene) {
    this.scene = scene;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public Integer getTotalDuration() {
    return totalDuration;
  }

  public void setTotalDuration(Integer totalDuration) {
    this.totalDuration = totalDuration;
  }

  public String getBizDate() {
    return bizDate;
  }

  public void setBizDate(String bizDate) {
    this.bizDate = bizDate;
  }
}
