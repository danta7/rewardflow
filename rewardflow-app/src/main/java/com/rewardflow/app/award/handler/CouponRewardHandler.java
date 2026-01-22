package com.rewardflow.app.award.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import org.springframework.stereotype.Component;

/**
 * 示例：发券奖励处理器。
 *
 * <p>当前项目上下游先不管，因此这里的“发券”仍然走 Outbox 事件落库，后续由 MQ/消费者去真正调用下游发券。
 */
@Component
public class CouponRewardHandler extends AbstractRewardHandler {

  /** prizeCode 必须与规则配置中的 prizeCode 一致。 */
  public static final String PRIZE_CODE = "COUPON";

  /** Outbox 事件类型：用于与 COIN 区分，便于下游路由不同 Topic/消费者。 */
  public static final String EVENT_TYPE_COUPON_CREATED = "COUPON_CREATED";

  public CouponRewardHandler(RewardFlowMapper rewardFlowMapper,
                             RewardOutboxMapper outboxMapper,
                             ObjectMapper objectMapper) {
    super(rewardFlowMapper, outboxMapper, objectMapper);
  }

  @Override
  public String prizeCode() {
    return PRIZE_CODE;
  }

  @Override
  protected String eventType() {
    return EVENT_TYPE_COUPON_CREATED;
  }
}
