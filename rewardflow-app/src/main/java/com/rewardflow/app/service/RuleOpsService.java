package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.api.dto.RuleSimulateRequest;
import com.rewardflow.api.dto.RuleSimulateResponse;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.app.exception.BizException;
import com.rewardflow.domain.rule.AwardCalculator;
import com.rewardflow.domain.rule.model.AwardPlan;
import com.rewardflow.domain.rule.model.RuleCenterConfig;
import com.rewardflow.infra.mongo.entity.RuleSimulationLog;
import com.rewardflow.infra.mongo.repo.RuleSimulationLogRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 规则模拟/演练（不真正发奖）
 * 给运营/研发一个接口，用某个 user/scene/totalDuration 跑一遍规则计算并只把结果记录存到mongo
 */
@Service
public class RuleOpsService {

  private final RuleSelectionService ruleSelectionService;
  private final AwardProperties awardProperties;
  private final RewardFlowProperties rewardFlowProperties;
  private final FeatureCenterService featureCenterService;
  private final RuleSimulationLogRepository simulationRepo;

  private final AwardCalculator awardCalculator = new AwardCalculator();

  public RuleOpsService(RuleSelectionService ruleSelectionService,
                        AwardProperties awardProperties,
                        RewardFlowProperties rewardFlowProperties,
                        FeatureCenterService featureCenterService,
                        RuleSimulationLogRepository simulationRepo) {
    this.ruleSelectionService = ruleSelectionService;
    this.awardProperties = awardProperties;
    this.rewardFlowProperties = rewardFlowProperties;
    this.featureCenterService = featureCenterService;
    this.simulationRepo = simulationRepo;
  }

  public RuleSimulateResponse simulate(RuleSimulateRequest req) {
    Boolean enabled = featureCenterService.currentConfig().getRuleSimulationEnabled();
    if (enabled != null && !enabled) {
      throw new BizException(4031, "rule simulation disabled");
    }

    String traceId = UUID.randomUUID().toString().replace("-", "");
    String bizDate = (req.getBizDate() == null || req.getBizDate().isBlank())
        ? LocalDate.now(ZoneId.of(rewardFlowProperties.getTimezone())).toString()
        : req.getBizDate();

    RuleSelectionService.RuleSelectResult sel = ruleSelectionService.select(req.getScene(), req.getUserId());
    RuleCenterConfig.RuleVersion rv = sel.getRuleVersion();
    if (rv == null) {
      throw new BizException(4041, "rule version not found: " + sel.getHitRuleVersion());
    }

    List<AwardPlan> plans = awardCalculator.calculate(
        req.getTotalDuration(),
        rv,
        Map.of(),
        awardProperties.getPrizeCode()
    );

    List<PlayReportResponse.RewardPlanItem> items = new ArrayList<>();
    for (AwardPlan p : plans) {
      PlayReportResponse.RewardPlanItem it = new PlayReportResponse.RewardPlanItem();
      it.setStage(p.getStage());
      it.setThreshold(p.getThreshold());
      it.setAmount(p.getAmount());
      it.setPrizeCode(p.getPrizeCode());
      it.setOutBizNo(buildOutBizNo(req.getUserId(), p.getPrizeCode(), req.getScene(), bizDate, p.getStage()));
      it.setIssued(false);
      it.setIssueStatus("SIMULATED");
      items.add(it);
    }

    RuleSimulateResponse resp = new RuleSimulateResponse();
    resp.setTraceId(traceId);
    resp.setScene(req.getScene());
    resp.setUserId(req.getUserId());
    resp.setBizDate(bizDate);
    resp.setTotalDuration(req.getTotalDuration());
    resp.setHitRuleVersion(sel.getHitRuleVersion());
    resp.setGrayHit(sel.isGrayHit());
    resp.setAwardPlans(items);

    // Best-effort save
    try {
      // 模拟日志存 mongo
      RuleSimulationLog log = new RuleSimulationLog();
      log.setTraceId(traceId);
      log.setScene(req.getScene());
      log.setUserId(req.getUserId());
      log.setBizDate(bizDate);
      log.setTotalDuration(req.getTotalDuration());
      log.setHitRuleVersion(sel.getHitRuleVersion());
      log.setGrayHit(sel.isGrayHit());
      Map<String, Object> summary = new HashMap<>();
      summary.put("awardPlanCount", items.size());
      summary.put("stages", items.stream().map(PlayReportResponse.RewardPlanItem::getStage).toList());
      summary.put("prizeCodes", items.stream().map(PlayReportResponse.RewardPlanItem::getPrizeCode).distinct().toList());
      log.setResult(summary);
      simulationRepo.save(log);
    } catch (Exception ignore) {
    }

    return resp;
  }

  public List<RuleSimulationLog> latestSimulations() {
    return simulationRepo.findTop50ByOrderByCreatedAtDesc();
  }

  private static String buildOutBizNo(String userId, String prizeCode, String scene, String bizDate, int stage) {
    return userId + "|" + prizeCode + "|" + scene + "|" + bizDate + "|" + stage;
  }
}
