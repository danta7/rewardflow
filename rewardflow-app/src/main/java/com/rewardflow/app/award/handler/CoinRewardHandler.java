package com.rewardflow.app.award.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import org.springframework.stereotype.Component;

/** COIN 奖品的默认 handler */
@Component
public class CoinRewardHandler extends AbstractRewardHandler {

  public static final String PRIZE_CODE = "COIN";
  public static final String EVENT_TYPE_AWARD_CREATED = "AWARD_CREATED";

  public CoinRewardHandler(RewardFlowMapper rewardFlowMapper,
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
    return EVENT_TYPE_AWARD_CREATED;
  }
}
