package com.rewardflow.app.award.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.award.model.IssueResult;
import com.rewardflow.app.award.model.RewardIssueContext;
import com.rewardflow.infra.mysql.entity.RewardFlowDO;
import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

/**
 * 奖励发放模版方法
 *
 * <p> 提供统一的事务方法体，并将自定义点（payload/eventType）留给子类
 */
public abstract class AbstractRewardHandler implements RewardHandler {

  protected final RewardFlowMapper rewardFlowMapper;
  protected final RewardOutboxMapper outboxMapper;
  protected final ObjectMapper objectMapper;

  protected AbstractRewardHandler(RewardFlowMapper rewardFlowMapper,
                                 RewardOutboxMapper outboxMapper,
                                 ObjectMapper objectMapper) {
    this.rewardFlowMapper = rewardFlowMapper;
    this.outboxMapper = outboxMapper;
    this.objectMapper = objectMapper;
  }

  /** 此 handler 对应的 outbox 事件类型 */
  protected abstract String eventType();

  @Override
  public IssueResult issue(RewardIssueContext ctx, PlayReportResponse.RewardPlanItem plan) {
    IssueResult r = new IssueResult();
    r.setOutBizNo(plan.getOutBizNo());
    r.setStage(plan.getStage());
    r.setAmount(plan.getAmount());

    // 确保 reward_flow 存在
    RewardFlowDO flow = buildFlow(ctx, plan);
    try {
      rewardFlowMapper.insert(flow);
      r.setIssued(true);
      r.setFlowId(flow.getId());
    } catch (DuplicateKeyException dupFlow) {
      RewardFlowDO existed = rewardFlowMapper.selectByOutBizNo(plan.getOutBizNo());
      r.setIssued(false);
      r.setFlowId(existed == null ? null : existed.getId());
      // 即是 flow 已经存在，也要继续走下去确保 outbox 存在 因为上一次肯能在写 outbox 时失败了
    } catch (Exception ex) {
      r.setIssued(false);
      r.setIssueStatus("FAILED");
      r.setError(ex.getClass().getSimpleName());
      return r;
    }

    // 确保 reward_outbox 存在
    String eventId = UUID.randomUUID().toString().replace("-", "");
    RewardOutboxDO ob = new RewardOutboxDO();
    ob.setEventId(eventId);
    ob.setOutBizNo(plan.getOutBizNo());
    ob.setEventType(eventType());
    ob.setStatus(0);
    ob.setRetryCount(0);
    ob.setNextRetryTime(null);
    ob.setTraceId(ctx.getTraceId());
    ob.setPayload(buildPayload(eventId, ctx, plan));

    try {
      outboxMapper.insert(ob);
      r.setEventId(eventId);
      r.setIssueStatus("CREATED");
    } catch (DuplicateKeyException dupOutbox) {
      // (outBizNo,eventType) already exists, 不能把刚生成的 enentID 返回
      RewardOutboxDO existed = outboxMapper.selectByOutBizNoAndEventType(plan.getOutBizNo(), eventType());
      r.setEventId(existed == null ? null : existed.getEventId());
      r.setIssueStatus("CREATED");
    } catch (Exception ex) {
      r.setIssueStatus("FAILED");
      r.setError(ex.getClass().getSimpleName());
    }
    return r;
  }

  protected RewardFlowDO buildFlow(RewardIssueContext ctx, PlayReportResponse.RewardPlanItem plan) {
    RewardFlowDO flow = new RewardFlowDO();
    flow.setUserId(ctx.getUserId());
    flow.setBizScene(ctx.getScene());
    flow.setPrizeCode(ctx.getPrizeCode());
    flow.setPrizeDate(ctx.getBizDate());
    flow.setPrizeStage(plan.getStage());
    flow.setPrizeAmount(plan.getAmount());
    flow.setOutBizNo(plan.getOutBizNo());
    flow.setRuleVersion(ctx.getRuleVersion());
    flow.setTraceId(ctx.getTraceId());
    return flow;
  }

  // 可以选择覆盖，决定 outBox 的 payload 内容
  protected String buildPayload(String eventId, RewardIssueContext ctx, PlayReportResponse.RewardPlanItem plan) {
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("eventId", eventId);
      payload.put("eventType", eventType());
      payload.put("outBizNo", plan.getOutBizNo());
      payload.put("userId", ctx.getUserId());
      payload.put("scene", ctx.getScene());
      payload.put("bizDate", ctx.getBizDate().toString());
      payload.put("prizeCode", ctx.getPrizeCode());
      payload.put("stage", plan.getStage());
      payload.put("amount", plan.getAmount());
      payload.put("threshold", plan.getThreshold());
      payload.put("ruleVersion", ctx.getRuleVersion());
      payload.put("traceId", ctx.getTraceId());
      payload.put("createdAt", LocalDateTime.now().toString());
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      return "{\"eventId\":\"" + eventId + "\",\"outBizNo\":\"" + plan.getOutBizNo() + "\"}";
    }
  }
}
