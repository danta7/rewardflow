package com.rewardflow.app.job;

import com.rewardflow.app.config.OutboxProperties;
import com.rewardflow.app.config.RewardMqProperties;
import com.rewardflow.app.metrics.RewardFlowMetrics;
import com.rewardflow.app.service.FeatureCenterService;
import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Outbox publisher
 *
 * 从 MySQL 捞 pending 事件 outbox 并发布到 RabbitMQ，然后把 DB 里的 outbox 记录标记为 SENT
 * 通过重试保证 DB 提交 和 mq发布的可靠性（at-least-once）
 * 下游必须幂等消费（按照 outBizNo）
 * 状态机：pending -> sent/retry/failed
 */
@Service
public class OutboxPublishJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublishJob.class);

  private static final String RESULT_SENT = "SENT";
  private static final String RESULT_FAIL = "FAIL";
  private static final String RESULT_CONFLICT = "CONFLICT"; // 并发下：状态/版本不匹配，本次不落库

  private final RewardOutboxMapper outboxMapper;
  private final FeatureCenterService featureCenterService;
  private final RabbitTemplate rabbitTemplate;
  private final OutboxProperties outboxProps;
  private final RewardMqProperties mqProps;
  private final RewardFlowMetrics metrics;

  public OutboxPublishJob(RewardOutboxMapper outboxMapper,
                          FeatureCenterService featureCenterService,
                          RabbitTemplate rabbitTemplate,
                          OutboxProperties outboxProps,
                          RewardMqProperties mqProps,
                          RewardFlowMetrics metrics) {
    this.outboxMapper = outboxMapper;
    this.featureCenterService = featureCenterService;
    this.rabbitTemplate = rabbitTemplate;
    this.outboxProps = outboxProps;
    this.mqProps = mqProps;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${rewardflow.outbox.scan-delay-ms:2000}",
      initialDelayString = "${rewardflow.outbox.scan-initial-delay-ms:1000}"
  )
  public void scanAndPublish() {
    // feature flag 线上一键停投
    Boolean enabled = featureCenterService.currentConfig().getOutboxPublishEnabled();
    if (enabled != null && !enabled) {
      return;
    }

    // 捞 status = 0 且 nextRetry <= now 的记录都算 pending
    LocalDateTime now = LocalDateTime.now();
    List<RewardOutboxDO> list = outboxMapper.selectPending(now, outboxProps.getBatchSize());
    if (list == null || list.isEmpty()) {
      return;
    }

    // 逐条publish
    for (RewardOutboxDO e : list) {
      publishOne(e, now);
    }
  }

  private void publishOne(RewardOutboxDO e, LocalDateTime now) {
    final String eventType = (e.getEventType() == null || e.getEventType().isBlank()) ? "UNKNOWN" : e.getEventType();
    String result = RESULT_SENT;

    final var sample = metrics.startOutboxPublishTimer();

    try {
      MessagePostProcessor mpp = msg -> {
        msg.getMessageProperties().setHeader("eventId", e.getEventId());
        msg.getMessageProperties().setHeader("outBizNo", e.getOutBizNo());
        msg.getMessageProperties().setHeader("eventType", eventType);
        if (e.getTraceId() != null) {
          msg.getMessageProperties().setHeader("traceId", e.getTraceId());
        }
        return msg;
      };

      // 宁可重复发布，也不漏发 先发MQ在markSent

      // publish to MQ (at-least-once)
      rabbitTemplate.convertAndSend(mqProps.getExchange(), mqProps.getRoutingKey(), e.getPayload(), mpp);

      // mark SENT (only when still pending)
      int updated = outboxMapper.markSent(e.getEventId());
      if (updated <= 0) {
        // 并发正常情况：可能已经被其它 worker 标记 SENT/FAILED，不要再把它改回去
        result = RESULT_CONFLICT;
        log.debug("markSent skipped due to status conflict: eventId={}, outBizNo={}", e.getEventId(), e.getOutBizNo());
      }

    } catch (Exception ex) {
      result = RESULT_FAIL;

      int old = e.getRetryCount() == null ? 0 : e.getRetryCount();
      int next = old + 1;

      int status = 0;
      LocalDateTime nextRetry = null;

      if (next > outboxProps.getMaxRetry()) {
        status = 2; // FAILED
      } else {
        long base = Math.max(1L, (long) outboxProps.getBaseBackoffSeconds());

        // 指数退避：base * 2^(next-1)，并避免位移溢出
        int exp = Math.max(0, next - 1);
        exp = Math.min(exp, 10); // 2^10=1024，后面还有 cap 300s，足够了
        long backoffSec = base * (1L << exp);
        backoffSec = Math.min(backoffSec, 300L);

        nextRetry = now.plusSeconds(backoffSec);
      }

      // 带 expectedRetryCount + status=0 条件更新，避免并发把 status=1 的记录改回 0/2
      // 只有仍然是 pending 且 retryCount 未被改动才写回重试信息
      int wrote = outboxMapper.updateRetry(e.getEventId(), old, next, nextRetry, status);
      if (wrote <= 0) {
        result = RESULT_CONFLICT;
        log.debug("updateRetry skipped due to status/retryCount conflict: eventId={}, outBizNo={}, err={}",
            e.getEventId(), e.getOutBizNo(), ex.toString());
      } else {
        log.warn("outbox publish failed: eventId={}, outBizNo={}, retryCount={}, nextRetry={}, err={}",
            e.getEventId(), e.getOutBizNo(), next, nextRetry, ex.toString());
      }

    } finally {
      // Observability must never影响业务：独立 try/catch
      try {
        metrics.incOutboxPublish(eventType, result);
      } catch (Exception ignore) {
      }
      try {
        metrics.stopOutboxPublishTimer(sample, eventType, result);
      } catch (Exception ignore) {
      }
    }
  }
}
