package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.app.service.FeatureCenterService;
import com.rewardflow.domain.feature.model.FeatureCenterConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Debug endpoints for feature-center. */
@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

  private final FeatureCenterService featureCenterService;

  public FeatureController(FeatureCenterService featureCenterService) {
    this.featureCenterService = featureCenterService;
  }

  @GetMapping("/config")
  public ApiResponse<FeatureCenterConfig> config() {
    return ApiResponse.ok(featureCenterService.currentConfig());
  }

  @GetMapping("/effective")
  public ApiResponse<Map<String, Object>> effective(@RequestParam("scene") String scene) {
    FeatureCenterConfig.SceneFeature eff = featureCenterService.effectiveForScene(scene);
    return ApiResponse.ok(Map.of(
        "scene", scene,
        "awardIssueEnabled", eff.getAwardIssueEnabled(),
        "outboxPublishEnabled", eff.getOutboxPublishEnabled(),
        "reconcileEnabled", eff.getReconcileEnabled(),
        "ruleSnapshotEnabled", eff.getRuleSnapshotEnabled(),
        "ruleSimulationEnabled", eff.getRuleSimulationEnabled()
    ));
  }
}
