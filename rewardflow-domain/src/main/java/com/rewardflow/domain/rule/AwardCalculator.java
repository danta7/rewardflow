package com.rewardflow.domain.rule;

import com.rewardflow.domain.rule.model.AwardPlan;
import com.rewardflow.domain.rule.model.RuleCenterConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据总时长和规则阈值计算分档奖励计划
 */
public class AwardCalculator {

  private static final Logger log = LoggerFactory.getLogger(AwardCalculator.class);

  /**
   * 计算奖励计划：挑出 totalDuration 已达到阈值、且该档位还没发过奖的 stages。
   */
  public List<AwardPlan> calculate(
      int totalDuration,
      RuleCenterConfig.RuleVersion ruleVersion,
      Map<String, Set<Integer>> alreadyAwardedByPrizeCode,
      String defaultPrizeCode) {

    if (ruleVersion == null || ruleVersion.getStages() == null || ruleVersion.getStages().isEmpty()) {
      return List.of();
    }

    // 兜底默认 prizeCode
    String safeDefaultPrizeCode = (defaultPrizeCode == null || defaultPrizeCode.isBlank())
        ? "DEFAULT"
        : defaultPrizeCode.trim();

    // 拷贝一份并排序：threshold 升序；相同 threshold 按 stage 升序
    List<RuleCenterConfig.StageRule> stages = new ArrayList<>(ruleVersion.getStages());
    stages.sort(
        Comparator
            .comparingInt((RuleCenterConfig.StageRule s) -> s == null ? Integer.MAX_VALUE : s.getThreshold())
            .thenComparingInt(s -> s == null ? Integer.MAX_VALUE : s.getStage())
    );

    List<AwardPlan> result = new ArrayList<>();

    for (RuleCenterConfig.StageRule sr : stages) {
      if (sr == null) {
        log.warn("skip null stageRule in ruleVersion={}", ruleVersion.getRuleVersion());
        continue;
      }

      // 非法值校验：stage/threshold 必须 > 0
      if (sr.getStage() <= 0 || sr.getThreshold() <= 0) {
        log.warn("skip invalid stageRule: ruleVersion={}, stage={}, threshold={}, amount={}, prizeCode={}",
            ruleVersion.getRuleVersion(), sr.getStage(), sr.getThreshold(), sr.getAmount(), sr.getPrizeCode());
        continue;
      }

      // optional amount 也做一下校验，避免发 0 或负数
      if (sr.getAmount() <= 0) {
        log.warn("skip invalid stageRule amount<=0: ruleVersion={}, stage={}, threshold={}, amount={}, prizeCode={}",
            ruleVersion.getRuleVersion(), sr.getStage(), sr.getThreshold(), sr.getAmount(), sr.getPrizeCode());
        continue;
      }

      // 没达到阈值，不发
      if (totalDuration < sr.getThreshold()) {
        // 因为已经按 threshold 升序排了，这里可以直接 break 提前结束（性能更好）
        break;
      }

      String prizeCode = resolvePrizeCode(sr, safeDefaultPrizeCode);

      // 去重：按 prizeCode + stage
      if (alreadyAwardedByPrizeCode != null) {
        Set<Integer> awarded = alreadyAwardedByPrizeCode.get(prizeCode);
        if (awarded != null && awarded.contains(sr.getStage())) {
          continue;
        }
      }

      result.add(new AwardPlan(sr.getStage(), sr.getThreshold(), sr.getAmount(), prizeCode));
    }

    return result;
  }

  private static String resolvePrizeCode(RuleCenterConfig.StageRule sr, String defaultPrizeCode) {
    String pc = sr.getPrizeCode();
    if (pc == null || pc.isBlank()) {
      return defaultPrizeCode;
    }
    return pc.trim();
  }
}
