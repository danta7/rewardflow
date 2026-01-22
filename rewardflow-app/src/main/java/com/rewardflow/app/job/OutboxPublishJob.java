package com.rewardflow.app.job;

import com.rewardflow.app.config.OutboxProperties;
import com.rewardflow.app.config.RewardMqProperties;
import com.rewardflow.app.service.FeatureCenterService;
import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox publisher
 *
 * 从 MySQL 读取待处理的事件 outbox 并发布到 RabbitMQ
 * 通过重试保证 DB 提交 和 mq发布的可靠性
 */
@Service
public class OutboxPublishJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublishJob.class);

  private final RewardOutboxMapper outboxMapper;
  private final FeatureCenterService featureCenterService;
  private final RabbitTemplate rabbitTemplate;
  private final OutboxProperties outboxProps;
  private final RewardMqProperties mqProps;

  public OutboxPublishJob(RewardOutboxMapper outboxMapper,
                          FeatureCenterService featureCenterService,
                          RabbitTemplate rabbitTemplate,
                          OutboxProperties outboxProps,
                          RewardMqProperties mqProps) {
    this.outboxMapper = outboxMapper;
    this.featureCenterService = featureCenterService;
    this.rabbitTemplate = rabbitTemplate;
    this.outboxProps = outboxProps;
    this.mqProps = mqProps;
  }

  @Scheduled(fixedDelayString = "${rewardflow.outbox.scan-delay-ms:2000}")
  public void scanAndPublish() {
    Boolean enabled = featureCenterService.currentConfig().getOutboxPublishEnabled();
    if (enabled != null && !enabled) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    List<RewardOutboxDO> list = outboxMapper.selectPending(now, outboxProps.getBatchSize());
    if (list == null || list.isEmpty()) {
      return;
    }

    for (RewardOutboxDO e : list) {
      publishOne(e, now);
    }
  }

  private void publishOne(RewardOutboxDO e, LocalDateTime now) {
    try {
      MessagePostProcessor mpp = msg -> {
        msg.getMessageProperties().setHeader("eventId", e.getEventId());
        msg.getMessageProperties().setHeader("outBizNo", e.getOutBizNo());
        msg.getMessageProperties().setHeader("eventType", e.getEventType());
        if (e.getTraceId() != null) {
          msg.getMessageProperties().setHeader("traceId", e.getTraceId());
        }
        return msg;
      };

      rabbitTemplate.convertAndSend(mqProps.getExchange(), mqProps.getRoutingKey(), e.getPayload(), mpp);
      // 成功后标记 SENT （status=1）
      outboxMapper.markSent(e.getEventId());
    } catch (Exception ex) {
      // 计算下一次重试的次数
      int old = e.getRetryCount() == null ? 0 : e.getRetryCount();
      int next = old + 1;

      int status = 0;
      LocalDateTime nextRetry = null;
      if (next > outboxProps.getMaxRetry()) {
        status = 2; // 标记 FAILED （status=2）
      } else {
        long backoffSec = (long) outboxProps.getBaseBackoffSeconds() * (1L << Math.max(0, next - 1));
        backoffSec = Math.min(backoffSec, 300L);
        nextRetry = now.plusSeconds(backoffSec);
      }

      // 写回数据库并打日志
      outboxMapper.updateRetry(e.getEventId(), next, nextRetry, status);
      log.warn("outbox publish failed: eventId={}, outBizNo={}, retryCount={}, nextRetry={}, err={}",
          e.getEventId(), e.getOutBizNo(), next, nextRetry, ex.toString());
    }
  }
}
