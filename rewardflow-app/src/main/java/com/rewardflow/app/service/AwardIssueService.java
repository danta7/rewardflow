package com.rewardflow.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.infra.mongo.entity.AwardAuditLog;
import com.rewardflow.infra.mongo.repo.AwardAuditLogRepository;
import com.rewardflow.infra.mysql.entity.RewardFlowDO;
import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 持久化 reward_flow 和事务性记录 outbox(reward_outbox)
 *
 * 下游奖励机制尚未实现，目前仅生成 outbox 事件
 */
@Service
public class AwardIssueService {

  public static final String EVENT_TYPE_AWARD_CREATED = "AWARD_CREATED";

  private final AwardProperties awardProps;
  private final RewardFlowMapper rewardFlowMapper;    // 业务发奖流水表（强一致，事务性）
  private final RewardOutboxMapper outboxMapper;    // 消息 outbox
  private final AwardAuditLogRepository auditLogRepository; // Mongo: award audit log
  private final ObjectMapper objectMapper;

  public AwardIssueService(AwardProperties awardProps,
                           RewardFlowMapper rewardFlowMapper,
                           RewardOutboxMapper outboxMapper,
                           AwardAuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper) {
    this.awardProps = awardProps;
    this.rewardFlowMapper = rewardFlowMapper;
    this.outboxMapper = outboxMapper;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
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

    for (PlayReportResponse.RewardPlanItem it : plans) {
      String outBizNo = it.getOutBizNo();
      IssueResult r = new IssueResult();
      r.outBizNo = outBizNo;
      r.stage = it.getStage();
      r.amount = it.getAmount();

      try {
        // 1) 业务落库 reward_flow
        RewardFlowDO flow = new RewardFlowDO();
        flow.setUserId(userId);
        flow.setBizScene(scene);
        flow.setPrizeCode(awardProps.getPrizeCode());
        flow.setPrizeDate(bizDate);
        flow.setPrizeStage(it.getStage());
        flow.setPrizeAmount(it.getAmount());
        flow.setOutBizNo(outBizNo);
        flow.setRuleVersion(hitRuleVersion);
        flow.setTraceId(traceId);

        rewardFlowMapper.insert(flow);
        r.issued = true;
        r.flowId = flow.getId();

        // 2) 写 reward_outbox 生成要发MQ的事件
        String eventId = UUID.randomUUID().toString().replace("-", "");
        RewardOutboxDO ob = new RewardOutboxDO();
        ob.setEventId(eventId);
        ob.setOutBizNo(outBizNo);
        ob.setEventType(EVENT_TYPE_AWARD_CREATED);
        ob.setStatus(0);    // PENGING
        ob.setRetryCount(0);
        ob.setNextRetryTime(null);
        ob.setTraceId(traceId);
        ob.setPayload(buildPayload(eventId, userId, scene, bizDate, it, hitRuleVersion, traceId));

        try {
          outboxMapper.insert(ob);
          r.eventId = eventId;
          r.issueStatus = "CREATED";
        } catch (DuplicateKeyException dupOutbox) {
          // 幂等：同一（outBizNo，enevtType）已存在，查回真实 eventId 
          RewardOutboxDO exis = outboxMapper.selectByOutBizNoAndEventType(outBizNo, EVENT_TYPE_AWARD_CREATED);
          r.eventId = eventId == null ? null : exis.getEventId();
          r.issueStatus = "CREATED";
        }

      } catch (DuplicateKeyException dup) {
        // reward_flow 重复
        r.issued = false;
        r.issueStatus = "DUPLICATE";
      } catch (Exception ex) {
        r.issued = false;
        r.issueStatus = "FAILED";
        r.error = ex.getClass().getSimpleName();
      }

      results.put(outBizNo, r);
      // 每个 stage 都写入审计 最后统一写 Mongo
      auditStages.add(new AwardAuditLog.AuditStage(it.getStage(), it.getThreshold(), it.getAmount(), outBizNo, r.issueStatus));
    }

    writeAudit(userId, scene, bizDate, totalDuration, hitRuleVersion, grayHit, auditStages, traceId);
    return results;
  }

  private String buildPayload(String eventId,
                              String userId,
                              String scene,
                              LocalDate bizDate,
                              PlayReportResponse.RewardPlanItem it,
                              String ruleVersion,
                              String traceId) {
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("eventId", eventId);
      payload.put("eventType", EVENT_TYPE_AWARD_CREATED);
      payload.put("outBizNo", it.getOutBizNo());
      payload.put("userId", userId);
      payload.put("scene", scene);
      payload.put("bizDate", bizDate.toString());
      payload.put("prizeCode", awardProps.getPrizeCode());
      payload.put("stage", it.getStage());
      payload.put("amount", it.getAmount());
      payload.put("threshold", it.getThreshold());
      payload.put("ruleVersion", ruleVersion);
      payload.put("traceId", traceId);
      payload.put("createdAt", LocalDateTime.now().toString());
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      // fall back to a minimal payload
      return "{\"eventId\":\"" + eventId + "\",\"outBizNo\":\"" + it.getOutBizNo() + "\"}";
    }
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

  public static class IssueResult {
    private String outBizNo;
    private Integer stage;
    private Integer amount;
    private Boolean issued;
    private Long flowId;
    private String eventId;
    private String issueStatus;
    private String error;
    
    public Integer getStage() {
      return stage;
    }
    public Integer getAmount() {
      return amount;
    }

    public String getOutBizNo() {
      return outBizNo;
    }

    public Boolean getIssued() {
      return issued;
    }

    public Long getFlowId() {
      return flowId;
    }

    public String getEventId() {
      return eventId;
    }

    public String getIssueStatus() {
      return issueStatus;
    }

    public String getError() {
      return error;
    }
  }
}
