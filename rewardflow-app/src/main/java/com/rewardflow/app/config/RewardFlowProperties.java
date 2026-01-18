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
  }
}
