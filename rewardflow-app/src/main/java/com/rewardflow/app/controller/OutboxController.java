package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 观测/调试接口
 * 查看当前 outbox 表里“待发送（PENDING）”的事件有哪些、重试了几次、下次重试时间等信息
*/
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {

  private final RewardOutboxMapper outboxMapper;

  public OutboxController(RewardOutboxMapper outboxMapper) {
    this.outboxMapper = outboxMapper;
  }

  @GetMapping("/pending")
  public ApiResponse<List<Object>> pending(@RequestParam(value = "limit", defaultValue = "20") int limit) {
    List<RewardOutboxDO> list = outboxMapper.selectPending(LocalDateTime.now(), Math.max(1, Math.min(limit, 200)));
    List<Object> view = list.stream().map(e -> java.util.Map.of(
        "eventId", e.getEventId(),
        "outBizNo", e.getOutBizNo(),
        "eventType", e.getEventType(),
        "status", e.getStatus(),
        "retryCount", e.getRetryCount(),
        "nextRetryTime", e.getNextRetryTime(),
        "createTime", e.getCreateTime()
    )).collect(Collectors.toList());
    return ApiResponse.ok(view);
  }
}
