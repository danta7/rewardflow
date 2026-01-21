package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.api.dto.RuleSimulateRequest;
import com.rewardflow.api.dto.RuleSimulateResponse;
import com.rewardflow.app.service.RuleOpsService;
import com.rewardflow.app.service.RuleSnapshotService;
import com.rewardflow.infra.mongo.entity.RuleSnapshot;
import com.rewardflow.infra.mongo.entity.RuleSimulationLog;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ops endpoints: rule simulate/snapshot. */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleOpsController {

  private final RuleOpsService ruleOpsService;
  private final RuleSnapshotService ruleSnapshotService;

  public RuleOpsController(RuleOpsService ruleOpsService, RuleSnapshotService ruleSnapshotService) {
    this.ruleOpsService = ruleOpsService;
    this.ruleSnapshotService = ruleSnapshotService;
  }

  @PostMapping("/simulate")
  public ApiResponse<RuleSimulateResponse> simulate(@Valid @RequestBody RuleSimulateRequest req) {
    return ApiResponse.ok(ruleOpsService.simulate(req));
  }

  @GetMapping("/simulations")
  public ApiResponse<List<RuleSimulationLog>> simulations() {
    return ApiResponse.ok(ruleOpsService.latestSimulations());
  }

  @GetMapping("/snapshots")
  public ApiResponse<List<RuleSnapshot>> snapshots() {
    return ApiResponse.ok(ruleSnapshotService.latest());
  }
}
