package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.app.service.ReconcileService;
import com.rewardflow.app.service.SceneNormalizer;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reconcile endpoints. */
@RestController
@RequestMapping("/api/v1/reconcile")
public class ReconcileController {

  private final ReconcileService reconcileService;

  public ReconcileController(ReconcileService reconcileService) {
    this.reconcileService = reconcileService;
  }

  @GetMapping("/flow-outbox")
  public ApiResponse<Map<String, Object>> flowOutbox(@RequestParam("scene") String scene,
                                                     @RequestParam("bizDate") String bizDate,
                                                     @RequestParam(value = "limit", defaultValue = "2000") int limit) {
    scene = SceneNormalizer.normalize(scene);
    return ApiResponse.ok(reconcileService.reconcileFlowOutbox(scene, bizDate, limit));
  }
}
