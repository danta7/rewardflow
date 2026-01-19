package com.rewardflow.app.controller;

import com.rewardflow.api.dto.ApiResponse;
import com.rewardflow.api.dto.PlayReportRequest;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.api.dto.UserPlayDailyResponse;
import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.app.service.PlayReportAppService;
import com.rewardflow.infra.mysql.entity.UserPlayDailyDO;
import com.rewardflow.infra.mysql.mapper.UserPlayDailyMapper;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlayReportController {

  private final PlayReportAppService service;
  private final UserPlayDailyMapper dailyMapper;
  private final RewardFlowProperties props;

  public PlayReportController(PlayReportAppService service,
                              UserPlayDailyMapper dailyMapper,
                              RewardFlowProperties props) {
    this.service = service;
    this.dailyMapper = dailyMapper;
    this.props = props;
  }

  /**
   * 接收播放时长上报，并将原始上报记录落库保存
   *
   * 幂等性：在 play_duration_report 表上对 (userId, soundId, syncTime) 的唯一索引保证
   * 把当天的播放时长按天汇总然后存入 play_duration_daily 表
   */
  @PostMapping("/play/report")
  public ApiResponse<PlayReportResponse> report(@Valid @RequestBody PlayReportRequest req) {
    return ApiResponse.ok(service.report(req));
  }

  /**
   * debug 查询接口，直接查某用户某天累计播放时长
   */
  @GetMapping("/play/daily")
  public ApiResponse<UserPlayDailyResponse> daily(@RequestParam("userId") String userId,
                                                  @RequestParam("scene") String scene,
                                                  @RequestParam(value = "bizDate", required = false) String bizDate) {
    // 解析日期参数，空值就用当前日期                                                
    LocalDate date;
    if (bizDate == null || bizDate.isBlank()) {
      date = LocalDate.now(ZoneId.of(props.getTimezone()));
    } else {
      date = LocalDate.parse(bizDate);
    }

    // 查询汇总表
    UserPlayDailyDO daily = dailyMapper.selectOne(userId, scene, date);
    if (daily == null) {
      return ApiResponse.ok(null);
    }

    UserPlayDailyResponse resp = new UserPlayDailyResponse();
    resp.setId(daily.getId());
    resp.setUserId(daily.getUserId());
    resp.setScene(daily.getBizScene());
    resp.setBizDate(daily.getBizDate() == null ? null : daily.getBizDate().toString());
    resp.setTotalDuration(daily.getTotalDuration());
    resp.setLastSyncTime(daily.getLastSyncTime());
    resp.setVersion(daily.getVersion());
    return ApiResponse.ok(resp);
  }
}
