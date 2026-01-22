package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.award.handler.RewardHandler;
import com.rewardflow.app.award.handler.RewardHandlerFactory;
import com.rewardflow.app.award.model.IssueResult;
import com.rewardflow.app.award.model.RewardIssueContext;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.infra.mongo.entity.AwardAuditLog;
import com.rewardflow.infra.mongo.repo.AwardAuditLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将“可发奖计划(awardPlans)”落地为：
 * 1) reward_flow（幂等：outBizNo 唯一）
 * 2) outbox 事件（幂等：(outBizNo,eventType) 唯一）
 * 3) Mongo audit 日志（best-effort）
 *
 * <p>多奖品扩展点：每个 plan 自带 prizeCode（为空则使用默认奖品），
 * 通过 RewardHandlerFactory 路由到对应的 Handler。
 */
@Service
public class AwardIssueService {

  private static final Logger log = LoggerFactory.getLogger(AwardIssueService.class);

  private final AwardProperties awardProps;
  private final RewardHandlerFactory handlerFactory;
  private final AwardAuditLogRepository auditRepo;

  public AwardIssueService(AwardProperties awardProps,
                           RewardHandlerFactory handlerFactory,
                           AwardAuditLogRepository auditRepo) {
    this.awardProps = awardProps;
    this.handlerFactory = handlerFactory;
    this.auditRepo = auditRepo;
  }

  /**
   * 对一批 awardPlans 逐个 stage 进行“发奖落地”。
   *
   * @return key=outBizNo，value=发奖结果（用于回填到接口返回的 awardPlans 中）
   */
  public Map<String, IssueResult> issue(String userId,
                                        String scene,
                                        LocalDate bizDate,
                                        int totalDuration,
                                        String ruleVersion,
                                        boolean grayHit,
                                        List<PlayReportResponse.RewardPlanItem> plans,
                                        String traceId) {
    Map<String, IssueResult> resultMap = new HashMap<>();
    if (plans == null || plans.isEmpty()) {
      return resultMap;
    }

    // build base ctx
    RewardIssueContext baseCtx = new RewardIssueContext();
    baseCtx.setUserId(userId);
    baseCtx.setScene(scene);
    baseCtx.setBizDate(bizDate);
    baseCtx.setRuleVersion(ruleVersion);
    baseCtx.setGrayHit(grayHit);
    baseCtx.setTotalDuration(totalDuration);
    baseCtx.setTraceId(traceId);

    // build audit log (best-effort)
    AwardAuditLog audit = new AwardAuditLog();
    audit.setUserId(userId);
    audit.setScene(scene);
    audit.setBizDate(bizDate.toString());
    audit.setTraceId(traceId);
    audit.setTotalDuration(totalDuration);
    audit.setRuleVersion(ruleVersion);
    audit.setGrayHit(grayHit);
    audit.setCreatedAt(LocalDateTime.now());
    List<AwardAuditLog.AuditStage> auditStages = new ArrayList<>();

    for (PlayReportResponse.RewardPlanItem plan : plans) {
      String prizeCode = normalizePrizeCode(plan.getPrizeCode());
      // 路由 handler
      RewardHandler handler = handlerFactory.get(prizeCode);

      RewardIssueContext ctx = new RewardIssueContext();
      ctx.setUserId(baseCtx.getUserId());
      ctx.setScene(baseCtx.getScene());
      ctx.setBizDate(baseCtx.getBizDate());
      ctx.setRuleVersion(baseCtx.getRuleVersion());
      ctx.setGrayHit(baseCtx.getGrayHit());
      ctx.setTotalDuration(baseCtx.getTotalDuration());
      ctx.setTraceId(baseCtx.getTraceId());
      ctx.setPrizeCode(prizeCode);

      IssueResult r;
      try {
        // 调用 handler 发奖
        r = handler.issue(ctx, plan);
      } catch (Exception ex) {
        r = new IssueResult();
        r.setOutBizNo(plan.getOutBizNo());
        r.setStage(plan.getStage());
        r.setAmount(plan.getAmount());
        r.setIssued(false);
        r.setIssueStatus("FAILED");
        r.setError(ex.getClass().getSimpleName());
        log.warn("award handler crashed: prizeCode={}, outBizNo={}, ex={}", prizeCode, plan.getOutBizNo(), ex.toString());
      }

      // 写回 resultMap 和 auditStage
      resultMap.put(plan.getOutBizNo(), r);

      AwardAuditLog.AuditStage as = new AwardAuditLog.AuditStage();
      as.setStage(plan.getStage());
      as.setThreshold(plan.getThreshold());
      as.setAmount(plan.getAmount());
      as.setOutBizNo(plan.getOutBizNo());
      as.setStatus(r.getIssueStatus());
      auditStages.add(as);
    }

    audit.setStages(auditStages);
    try {
      auditRepo.save(audit);
    } catch (Exception ignore) {
      // best-effort
    }
    return resultMap;
  }

  private String normalizePrizeCode(String planPrizeCode) {
    if (planPrizeCode == null || planPrizeCode.isBlank()) {
      return awardProps.getPrizeCode();
    }
    return planPrizeCode.trim();
  }
}
