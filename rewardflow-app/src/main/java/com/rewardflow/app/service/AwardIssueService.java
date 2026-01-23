package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.award.handler.RewardHandler;
import com.rewardflow.app.award.handler.RewardHandlerFactory;
import com.rewardflow.app.award.model.IssueResult;
import com.rewardflow.app.award.model.RewardIssueContext;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.app.metrics.RewardFlowMetrics;
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

@Service
public class AwardIssueService {

  private static final Logger log = LoggerFactory.getLogger(AwardIssueService.class);

  private final AwardProperties awardProps;
  private final RewardHandlerFactory handlerFactory;
  private final AwardAuditLogRepository auditRepo;
  private final RewardFlowMetrics metrics;

  public AwardIssueService(AwardProperties awardProps,
                           RewardHandlerFactory handlerFactory,
                           AwardAuditLogRepository auditRepo,
                           RewardFlowMetrics metrics) {
    this.awardProps = awardProps;
    this.handlerFactory = handlerFactory;
    this.auditRepo = auditRepo;
    this.metrics = metrics;
  }

  public Map<String, IssueResult> issue(String userId,
                                        String scene,
                                        LocalDate bizDate,
                                        int totalDuration,
                                        String ruleVersion,
                                        boolean grayHit,
                                        List<PlayReportResponse.RewardPlanItem> plans,
                                        String traceId) {
    Map<String, IssueResult> resultMap = new HashMap<>(plans == null ? 16 : (plans.size() * 2));
    if (plans == null || plans.isEmpty()) {
      return resultMap;
    }

    // base ctx
    RewardIssueContext baseCtx = new RewardIssueContext();
    baseCtx.setUserId(userId);
    baseCtx.setScene(scene);
    baseCtx.setBizDate(bizDate);
    baseCtx.setRuleVersion(ruleVersion);
    baseCtx.setGrayHit(grayHit);
    baseCtx.setTotalDuration(totalDuration);
    baseCtx.setTraceId(traceId);

    // audit log (best-effort)
    AwardAuditLog audit = new AwardAuditLog();
    audit.setUserId(userId);
    audit.setScene(scene);
    audit.setBizDate(bizDate.toString());
    audit.setTraceId(traceId);
    audit.setTotalDuration(totalDuration);
    audit.setRuleVersion(ruleVersion);
    audit.setGrayHit(grayHit);
    audit.setCreatedAt(LocalDateTime.now());

    List<AwardAuditLog.AuditStage> auditStages = new ArrayList<>(plans.size());

    for (PlayReportResponse.RewardPlanItem plan : plans) {
      if (plan == null) continue;

      String prizeCode = normalizePrizeCode(plan.getPrizeCode());
      // 路由 handler
      RewardHandler handler = handlerFactory.get(prizeCode);

      RewardIssueContext ctx = copyCtx(baseCtx, prizeCode);

      IssueResult r;
      try {
        r = handler.issue(ctx, plan);
      } catch (Exception ex) {
        r = new IssueResult();
        r.setOutBizNo(plan.getOutBizNo());
        r.setStage(plan.getStage());
        r.setAmount(plan.getAmount());
        r.setIssued(false);
        r.setIssueStatus("FAILED");
        r.setError(ex.getClass().getSimpleName());
        log.warn("award handler crashed: prizeCode={}, outBizNo={}, ex={}",
            prizeCode, plan.getOutBizNo(), ex.toString());
      }

      resultMap.put(plan.getOutBizNo(), r);

      // metrics
      try {
        metrics.incAwardIssueAttempt(scene, prizeCode, plan.getStage(), r.getIssueStatus(), r.getIssued());
      } catch (Exception ignore) {
        // best-effort
      }

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

  private RewardIssueContext copyCtx(RewardIssueContext base, String prizeCode) {
    RewardIssueContext ctx = new RewardIssueContext();
    ctx.setUserId(base.getUserId());
    ctx.setScene(base.getScene());
    ctx.setBizDate(base.getBizDate());
    ctx.setRuleVersion(base.getRuleVersion());
    ctx.setGrayHit(base.getGrayHit());
    ctx.setTotalDuration(base.getTotalDuration());
    ctx.setTraceId(base.getTraceId());
    ctx.setPrizeCode(prizeCode);
    return ctx;
  }

  private String normalizePrizeCode(String planPrizeCode) {
    if (planPrizeCode == null || planPrizeCode.isBlank()) {
      return awardProps.getPrizeCode();
    }
    return planPrizeCode.trim();
  }
}
