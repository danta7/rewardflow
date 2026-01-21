package com.rewardflow.infra.mongo.entity;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 风险控制事件，用于可观测性和事后分析 */
@Document(collection = "risk_events")
public class RiskEvent {
  @Id
  private String id;

  @Indexed
  private String userId;

  @Indexed
  private String scene;

  @Indexed
  private String traceId;

  /** e.g. CLOCK_SKEW / DURATION_INVALID / RATE_LIMIT / DEDUP_HIT */
  private String type;

  private Map<String, Object> detail;

  private Instant createdAt = Instant.now();

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

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getDetail() {
    return detail;
  }

  public void setDetail(Map<String, Object> detail) {
    this.detail = detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
