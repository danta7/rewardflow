package com.rewardflow.infra.mongo.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
* 用于奖项决策和颁发的 MongoDB 审计日志
*
* MySQL 用于存储事务状态（强一致），MongoDB 存储调试/跟踪信息（best effort）
*/
@Document(collection = "award_audit_log")
public class AwardAuditLog {
  @Id
  private String id;

  // 业务定位信息
  private String userId;
  private String scene;
  private String bizDate;
  // 当天累计时长
  private int totalDuration;
  private String ruleVersion;
  private boolean grayHit;
  // 链路追踪
  private String traceId;
  // 每个 stage 的决策与状态
  private List<AuditStage> stages;
  private LocalDateTime createdAt;

  public static class AuditStage {
    private Integer stage;    // stage 第几档
    private Integer threshold;  // 达标阈值
    private Integer amount;   // 奖励金额
    private String outBizNo;  // 外部幂等号
    private String status;    // 奖励发放状态

    public AuditStage() {}

    public AuditStage(Integer stage, Integer threshold, Integer amount, String outBizNo, String status) {
      this.stage = stage;
      this.threshold = threshold;
      this.amount = amount;
      this.outBizNo = outBizNo;
      this.status = status;
    }

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

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  public int getTotalDuration() {
    return totalDuration;
  }

  public void setTotalDuration(int totalDuration) {
    this.totalDuration = totalDuration;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public void setRuleVersion(String ruleVersion) {
    this.ruleVersion = ruleVersion;
  }

  public boolean isGrayHit() {
    return grayHit;
  }

  public void setGrayHit(boolean grayHit) {
    this.grayHit = grayHit;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public List<AuditStage> getStages() {
    return stages;
  }

  public void setStages(List<AuditStage> stages) {
    this.stages = stages;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
