package com.rewardflow.app.award.handler;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.award.model.IssueResult;
import com.rewardflow.app.award.model.RewardIssueContext;

/**
 * 发奖处理器的抽奖接口
 *
 * 希望 hander 高内聚低耦合 一个 handler 只处理一种奖品类型
 * 新增奖品类型时，只需要新增对应的 handler 实现类即可
 * 调用链：
 *  AwardIssueService 里拿到 plans
 *  循环 plans
 *  handler = factory.getHandler(prizeCode)
 *  handler.issue(ctx, plan)
 *  handler 内部写 reward_flow 表和 reward_outbox 
 */
public interface RewardHandler {

  /** 这个 handler 支持的奖品码, e.g. COIN / COUPON / VIP 
   * “COIN” -> CoinRewardHandler
  */
  String prizeCode();

  /** 发放一个奖励计划itme（也就是一个 stage）必须基于 outBizNo 做幂等 */
  IssueResult issue(RewardIssueContext ctx, PlayReportResponse.RewardPlanItem plan);
}
