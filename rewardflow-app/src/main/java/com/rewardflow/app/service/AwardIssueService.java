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
import org.springframework.stereotype.Service;

/**
 * 持久化 reward_flow 和事务性记录 outbox(reward_outbox)
 *
 * 下游奖励机制尚未实现，目前仅生成 outbox 事件
 */
@Service
public class AwardIssueService {

  private final AwardProperties awardProps;
  private final RewardHandlerFactory handlerFactory;
  private final AwardAuditLogRepository auditLogRepository;

  public AwardIssueService(AwardProperties awardProps,
                           RewardHandlerFactory handlerFactory,
                           AwardAuditLogRepository auditLogRepository) {
    this.awardProps = awardProps;
    this.handlerFactory = handlerFactory;
    this.auditLogRepository = auditLogRepository;
  }

  /**
   * 持久化每个计划阶段，幂等性由唯一键(out_biz_no)保证
   *
   * @return map keyed by outBizNo
   */
  public Map<String, IssueResult> issue(String userId,
                                       String scene,
                                       LocalDate bizDate,
                                       Integer totalDuration,
                                       String hitRuleVersion,
                                       Boolean grayHit,
                                       List<PlayReportResponse.RewardPlanItem> plans,
                                       String traceId) {

    Map<String, IssueResult> results = new HashMap<>();
    List<AwardAuditLog.AuditStage> auditStages = new ArrayList<>();

    if (plans == null || plans.isEmpty()) {
      writeAudit(userId, scene, bizDate, totalDuration, hitRuleVersion, grayHit, auditStages, traceId);
      return results;
    }

    RewardIssueContext ctx = new RewardIssueContext()
        .setUserId(userId)
        .setScene(scene)
        .setBizDate(bizDate)
        .setTotalDuration(totalDuration)
        .setPrizeCode(awardProps.getPrizeCode())
        .setRuleVersion(hitRuleVersion)
        .setTraceId(traceId);

    RewardHandler handler = handlerFactory.get(awardProps.getPrizeCode());

    for (PlayReportResponse.RewardPlanItem it : plans) {
      IssueResult r = handler.issue(ctx, it);
      results.put(it.getOutBizNo(), r);
      auditStages.add(new AwardAuditLog.AuditStage(it.getStage(), it.getThreshold(), it.getAmount(), it.getOutBizNo(), r.getIssueStatus()));
    }

    writeAudit(userId, scene, bizDate, totalDuration, hitRuleVersion, grayHit, auditStages, traceId);
    return results;
  }

  private void writeAudit(String userId,
                          String scene,
                          LocalDate bizDate,
                          Integer totalDuration,
                          String ruleVersion,
                          Boolean grayHit,
                          List<AwardAuditLog.AuditStage> stages,
                          String traceId) {
    try {
      AwardAuditLog log = new AwardAuditLog();
      log.setUserId(userId);
      log.setScene(scene);
      log.setBizDate(bizDate.toString());
      log.setTotalDuration(totalDuration == null ? 0 : totalDuration);
      log.setRuleVersion(ruleVersion);
      log.setGrayHit(grayHit != null && grayHit);
      log.setTraceId(traceId);
      log.setStages(stages);
      log.setCreatedAt(LocalDateTime.now());
      auditLogRepository.save(log);
    } catch (Exception ignore) {
      // audit is best-effort
    }
  }

}
