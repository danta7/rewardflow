package com.rewardflow.infra.mongo.entity;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 用于运维/审计的规则模拟日志（无业务影响） */
@Document(collection = "rule_simulations")
public class RuleSimulationLog {

  @Id
  private String id;

  @Indexed
  private String traceId;

  @Indexed
  private String scene;

  @Indexed
  private String userId;

  private String bizDate;
  private Integer totalDuration;

  private String hitRuleVersion;
  private Boolean grayHit;

  /** 规则模拟结果 */
  private Map<String, Object> result;

  private Instant createdAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

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

  public String getHitRuleVersion() {
    return hitRuleVersion;
  }

  public void setHitRuleVersion(String hitRuleVersion) {
    this.hitRuleVersion = hitRuleVersion;
  }

  public Boolean getGrayHit() {
    return grayHit;
  }

  public void setGrayHit(Boolean grayHit) {
    this.grayHit = grayHit;
  }

  public Map<String, Object> getResult() {
    return result;
  }

  public void setResult(Map<String, Object> result) {
    this.result = result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
