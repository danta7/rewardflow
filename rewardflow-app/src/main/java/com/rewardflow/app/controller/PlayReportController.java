package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.api.dto.PlayReportRequest;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.service.PlayReportAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlayReportController {

  private final PlayReportAppService service;

  public PlayReportController(PlayReportAppService service) {
    this.service = service;
  }

  /**
   * 接收播放时长上报，并将原始上报记录落库保存
   *
   * 幂等性：在 play_duration_report 表上对 (userId, soundId, syncTime) 的唯一索引保证
   */
  @PostMapping("/play/report")
  public ApiResponse<PlayReportResponse> report(@Valid @RequestBody PlayReportRequest req) {
    return ApiResponse.ok(service.report(req));
  }
}
