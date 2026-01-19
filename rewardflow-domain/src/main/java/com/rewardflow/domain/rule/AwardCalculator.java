package com.rewardflow.domain.rule;

import com.rewardflow.domain.rule.model.AwardPlan;
import com.rewardflow.domain.rule.model.RuleCenterConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 根据总时长和规则阈值计算分档奖励计划
 */
public class AwardCalculator {

  /**
   * 计算奖励计划：挑出 totalDuration 已达到阈值、且该档位还没发过奖的 stages。
   * @param totalDuration 用户累计播放时长
   * @param ruleVersion 规则版本
   * @param alreadyAwardedStages 已发过奖的档位集合
   * @return 本次要发奖励计划列表
   */
  public List<AwardPlan> calculate(int totalDuration,
                                  RuleCenterConfig.RuleVersion ruleVersion,
                                  Set<Integer> alreadyAwardedStages) {
    if (ruleVersion == null || ruleVersion.getStages() == null || ruleVersion.getStages().isEmpty()) {
      return List.of();
    }
    List<RuleCenterConfig.StageRule> stages = new ArrayList<>(ruleVersion.getStages());
    stages.sort(Comparator.comparingInt(RuleCenterConfig.StageRule::getThreshold));

    List<AwardPlan> plans = new ArrayList<>();
    for (RuleCenterConfig.StageRule s : stages) {
      if (
          totalDuration >= s.getThreshold() 
          && (alreadyAwardedStages == null || !alreadyAwardedStages.contains(s.getStage()))
        ) {
        plans.add(new AwardPlan(s.getStage(), s.getThreshold(), s.getAmount()));
      }
    }
    return plans;
  }
}
