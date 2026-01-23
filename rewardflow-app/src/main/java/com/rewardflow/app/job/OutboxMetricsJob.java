package com.rewardflow.app.job;

import com.rewardflow.app.metrics.RewardFlowMetrics;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 指标采集 Job：周期性采集 outbox 积压/失败数量，写入 gauge。
 *
 * 注意：指标采集不影响主链路，失败只打 debug 日志。
 */
@Component
public class OutboxMetricsJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxMetricsJob.class);

  private final RewardOutboxMapper outboxMapper;
  private final RewardFlowMetrics metrics;

  public OutboxMetricsJob(RewardOutboxMapper outboxMapper, RewardFlowMetrics metrics) {
    this.outboxMapper = outboxMapper;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${rewardflow.outbox.metrics-interval-ms:2000}",
      initialDelayString = "${rewardflow.outbox.metrics-initial-delay-ms:1000}"
  )
  public void collect() {
    LocalDateTime now = LocalDateTime.now();
    try {
      long pending = outboxMapper.countPending(now);
      metrics.setOutboxPending(pending);
    } catch (Exception e) {
      log.debug("count outbox pending failed", e);
    }

    try {
      long failed = outboxMapper.countFailed();
      metrics.setOutboxFailed(failed);
    } catch (Exception e) {
      log.debug("count outbox failed failed", e);
    }
  }
}
