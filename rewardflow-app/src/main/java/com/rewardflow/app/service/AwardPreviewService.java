package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.domain.rule.AwardCalculator;
import com.rewardflow.domain.rule.model.AwardPlan;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 计算分阶段发放的奖励
 */
@Service
public class AwardPreviewService {

  private final AwardProperties awardProps;
  private final RewardFlowMapper rewardFlowMapper;
  private final RuleSelectionService ruleSelectionService;  // 按灰度选择 ruleVersion
  private final AwardCalculator calculator = new AwardCalculator();

  public AwardPreviewService(AwardProperties awardProps,
                             RewardFlowMapper rewardFlowMapper,
                             RuleSelectionService ruleSelectionService) {
    this.awardProps = awardProps;
    this.rewardFlowMapper = rewardFlowMapper;
    this.ruleSelectionService = ruleSelectionService;
  }

  public PreviewResult preview(String userId, String scene, LocalDate bizDate, int totalDuration, String traceId) {
    RuleSelectionService.RuleSelectResult sel = ruleSelectionService.select(scene, userId);

    // 已经发过奖的阶段集合
    List<Integer> awardedStages = rewardFlowMapper.selectAwardedStages(userId, scene, bizDate, awardProps.getPrizeCode());
    Set<Integer> already = awardedStages == null ? Set.of() : new HashSet<>(awardedStages);

    // 计算哪些 stage 达标但还没发奖
    List<AwardPlan> plans = calculator.calculate(totalDuration, sel.getRuleVersion(), already);

    List<PlayReportResponse.RewardPlanItem> items = new ArrayList<>();
    for (AwardPlan p : plans) {
      PlayReportResponse.RewardPlanItem it = new PlayReportResponse.RewardPlanItem();
      it.setStage(p.getStage());
      it.setThreshold(p.getThreshold());
      it.setAmount(p.getAmount());
      it.setOutBizNo(buildOutBizNo(userId, scene, bizDate, p.getStage()));
      items.add(it);
    }

    PreviewResult r = new PreviewResult();
    r.hitRuleVersion = sel.getHitRuleVersion();
    r.grayHit = sel.isGrayHit();
    r.items = items;
    return r;
  }

  // “u1|COIN|audio_play|2026-01-18|4”
  // userId|prizeCode|scene|bizDate|stage
  private String buildOutBizNo(String userId, String scene, LocalDate bizDate, int stage) {
    return userId + "|" + awardProps.getPrizeCode() + "|" + scene + "|" + bizDate + "|" + stage;
  }

  public static class PreviewResult {
    private String hitRuleVersion;
    private boolean grayHit;
    private List<PlayReportResponse.RewardPlanItem> items;

    public String getHitRuleVersion() {
      return hitRuleVersion;
    }

    public boolean isGrayHit() {
      return grayHit;
    }

    public List<PlayReportResponse.RewardPlanItem> getItems() {
      return items;
    }
  }
}
