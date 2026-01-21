package com.rewardflow.infra.mongo.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 规则快照，用于审计和调试 */
@Document(collection = "rule_snapshots")
public class RuleSnapshot {

  @Id
  private String id;

  /** SHA-256 对原始json 去重 */
  @Indexed(unique = true)
  private String contentHash;

  private String source; // NACOS / FALLBACK / MANUAL
  private String rawJson;

  private Instant createdAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getRawJson() {
    return rawJson;
  }

  public void setRawJson(String rawJson) {
    this.rawJson = rawJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
