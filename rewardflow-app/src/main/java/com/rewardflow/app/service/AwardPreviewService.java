package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.config.AwardProperties;
import com.rewardflow.domain.rule.AwardCalculator;
import com.rewardflow.domain.rule.model.AwardPlan;
import com.rewardflow.infra.mysql.entity.RewardFlowDO;
import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 根据用户当日累计播放时长+规则配置+已发奖记录，酸楚本次应该命中的分档奖励列表
 * @returns 命中的 rule 版本号、是否灰度命中、本次应该发哪些 stage（只是 preview）
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
    // 选择规则版本 含灰度
    RuleSelectionService.RuleSelectResult sel = ruleSelectionService.select(scene, userId);

    // 构建已发过的档位映射 prizeCode -> stages
    Map<String, Set<Integer>> already = new HashMap<>();
    List<RewardFlowDO> flows = rewardFlowMapper.selectAwardedFlows(userId, scene, bizDate);
    if (flows != null) {
      for (RewardFlowDO f : flows) {
        if (f == null) {
          continue;
        }
        // 这里如果流水没有存 prizeCode，就用全局默认的
        String pc = f.getPrizeCode() == null || f.getPrizeCode().isBlank() ? awardProps.getPrizeCode() : f.getPrizeCode();
        // 同一个 prizeCode 下，已发过的 stage 全部放进 Set
        already.computeIfAbsent(pc, k -> new HashSet<>()).add(f.getPrizeStage());
      }
    }

    // 调用计算器，算“新增命中的奖励计划”
    List<AwardPlan> plans = calculator.calculate(totalDuration, sel.getRuleVersion(), already, awardProps.getPrizeCode());

    List<PlayReportResponse.RewardPlanItem> items = new ArrayList<>();
    for (AwardPlan p : plans) {
      PlayReportResponse.RewardPlanItem it = new PlayReportResponse.RewardPlanItem();
      it.setStage(p.getStage());
      it.setThreshold(p.getThreshold());
      it.setAmount(p.getAmount());
      it.setPrizeCode(p.getPrizeCode());
      it.setOutBizNo(buildOutBizNo(userId, p.getPrizeCode(), scene, bizDate, p.getStage()));
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
  private static String buildOutBizNo(String userId, String prizeCode, String scene, LocalDate bizDate, int stage) {
    return userId + "|" + prizeCode + "|" + scene + "|" + bizDate + "|" + stage;
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
