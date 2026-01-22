package com.rewardflow.domain.rule.model;

/** 单个阶段的计划奖励（预览）*/
public class AwardPlan {
  private int stage;
  private int threshold;
  private int amount;
  private String prizeCode;

  public AwardPlan() {}

  public AwardPlan(int stage, int threshold, int amount, String prizeCode) {
    this.stage = stage;
    this.threshold = threshold;
    this.amount = amount;
    this.prizeCode = prizeCode;
  }

  public int getStage() {
    return stage;
  }

  public void setStage(int stage) {
    this.stage = stage;
  }

  public int getThreshold() {
    return threshold;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getPrizeCode() {
    return prizeCode;
  }

  public void setPrizeCode(String prizeCode) {
    this.prizeCode = prizeCode;
  }
}
