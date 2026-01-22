package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.app.service.RuleCenterService;
import com.rewardflow.app.service.RuleSelectionService;
import com.rewardflow.domain.rule.model.RuleCenterConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用于规则中心和灰度路由 debug */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

  private final RuleCenterService ruleCenterService;
  private final RuleSelectionService ruleSelectionService;

  public RuleController(RuleCenterService ruleCenterService, RuleSelectionService ruleSelectionService) {
    this.ruleCenterService = ruleCenterService;
    this.ruleSelectionService = ruleSelectionService;
  }

  @GetMapping("/config")
  public ApiResponse<RuleCenterConfig> config() {
    return ApiResponse.ok(ruleCenterService.currentConfig());
  }

  @GetMapping("/select")
  public ApiResponse<Map<String, Object>> select(@RequestParam("scene") String scene, @RequestParam("userId") String userId) {
    RuleSelectionService.RuleSelectResult r = ruleSelectionService.select(scene, userId);
    return ApiResponse.ok(Map.of(
        "scene", r.getScene(),
        "hitRuleVersion", r.getHitRuleVersion(),
        "grayHit", r.isGrayHit(),
        "stageCount", r.getRuleVersion() == null || r.getRuleVersion().getStages() == null ? 0 : r.getRuleVersion().getStages().size()
    ));
  }
}
