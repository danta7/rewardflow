package com.rewardflow.app.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Local module properties.
 *
 * uses it for risk-control validation (duration/time skew) and timezone.
 */
@Validated
@ConfigurationProperties(prefix = "rewardflow")
public class RewardFlowProperties {

  /**
   * Business date timezone. Default is UTC for consistency with docker-compose.
   */
  @NotBlank
  private String timezone = "UTC";

  // 只能配置内容不能更改
  private final Risk risk = new Risk();

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public Risk getRisk() {
    return risk;
  }

  public static class Risk {
    /**
     * 单次上报的播放时长最大允许60s
     */
    @Min(1)
    private int maxDurationPerReport = 60;

    /**
     * 客户端上报的 syncTime 与服务器当前时间允许差值，默认5分钟
     */
    @Min(0)
    private long maxClockSkewMs = 300_000L;

    /**
     * 基于 Redis 的短路去重 避免直接访问 Mysql 唯一索引
     * MySQL 唯一索引仍然是最终数据源
     */
    private boolean redisDedupEnabled = true;

    /** 去重键的 TTL（默认：2天） */
    @Min(1)
    private long redisDedupTtlSeconds = 172_800L;

    /** 最大每分钟上报次数 */
    @Min(1)
    private int maxReportsPerMinute = 120;

    /** 最大每分钟累计播放时长 */
    @Min(1)
    private int maxDurationPerMinute = 300;

    public int getMaxDurationPerReport() {
      return maxDurationPerReport;
    }

    public void setMaxDurationPerReport(int maxDurationPerReport) {
      this.maxDurationPerReport = maxDurationPerReport;
    }

    public long getMaxClockSkewMs() {
      return maxClockSkewMs;
    }

    public void setMaxClockSkewMs(long maxClockSkewMs) {
      this.maxClockSkewMs = maxClockSkewMs;
    }

    public boolean isRedisDedupEnabled() {
      return redisDedupEnabled;
    }

    public void setRedisDedupEnabled(boolean redisDedupEnabled) {
      this.redisDedupEnabled = redisDedupEnabled;
    }

    public long getRedisDedupTtlSeconds() {
      return redisDedupTtlSeconds;
    }

    public void setRedisDedupTtlSeconds(long redisDedupTtlSeconds) {
      this.redisDedupTtlSeconds = redisDedupTtlSeconds;
    }

    public int getMaxReportsPerMinute() {
      return maxReportsPerMinute;
    }

    public void setMaxReportsPerMinute(int maxReportsPerMinute) {
      this.maxReportsPerMinute = maxReportsPerMinute;
    }

    public int getMaxDurationPerMinute() {
      return maxDurationPerMinute;
    }

    public void setMaxDurationPerMinute(int maxDurationPerMinute) {
      this.maxDurationPerMinute = maxDurationPerMinute;
    }
  }
}
