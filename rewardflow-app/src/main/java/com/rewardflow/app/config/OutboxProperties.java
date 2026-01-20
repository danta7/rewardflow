package com.rewardflow.app.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rewardflow.outbox")
public class OutboxProperties {

  /** 扫描间隔毫秒数 */
  @Min(100)
  private long scanDelayMs = 2000L;

  /** 最大批量大小 */
  @Min(1)
  private int batchSize = 100;

  /** 最大重试次数 */
  @Min(0)
  private int maxRetry = 10;

  /** 指数退避基础秒数 */
  @Min(1)
  private int baseBackoffSeconds = 2;

  public long getScanDelayMs() {
    return scanDelayMs;
  }

  public void setScanDelayMs(long scanDelayMs) {
    this.scanDelayMs = scanDelayMs;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxRetry() {
    return maxRetry;
  }

  public void setMaxRetry(int maxRetry) {
    this.maxRetry = maxRetry;
  }

  public int getBaseBackoffSeconds() {
    return baseBackoffSeconds;
  }

  public void setBaseBackoffSeconds(int baseBackoffSeconds) {
    this.baseBackoffSeconds = baseBackoffSeconds;
  }
}
