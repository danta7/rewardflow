package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportRequest;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.app.exception.BizException;
import com.rewardflow.infra.mysql.entity.PlayDurationReportDO;
import com.rewardflow.infra.mysql.entity.UserPlayDailyDO;
import com.rewardflow.infra.mysql.mapper.PlayDurationReportMapper;
import com.rewardflow.infra.mysql.mapper.UserPlayDailyMapper;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayReportAppService {

  private final PlayDurationReportMapper reportMapper;
  private final UserPlayDailyMapper dailyMapper;
  private final PlayDailyAggService aggService;
  private final RewardFlowProperties props;
  private final AwardPreviewService awardPreviewService;
  private final Tracer tracer;

  public PlayReportAppService(PlayDurationReportMapper reportMapper,
                              UserPlayDailyMapper dailyMapper,
                              PlayDailyAggService aggService,
                              RewardFlowProperties props,
                              AwardPreviewService awardPreviewService,
                              Tracer tracer) {
    this.reportMapper = reportMapper;
    this.dailyMapper = dailyMapper;
    this.aggService = aggService;
    this.props = props;
    this.awardPreviewService = awardPreviewService;
    this.tracer = tracer;
  }

  @Transactional
  public PlayReportResponse report(PlayReportRequest req) {
    Objects.requireNonNull(req, "req");

    // 风控校验：处理用户手动改设备时间 + 异常时间上报
    int maxDur = props.getRisk().getMaxDurationPerReport();
    if (req.getDuration() == null || req.getDuration() <= 0 || req.getDuration() > maxDur) {
      throw new BizException(4001, "invalid duration: must be 1.." + maxDur);
    }

    // 风控校验：客户端 syncTime 与服务器时间差值不能过大
    long nowMs = Instant.now().toEpochMilli();
    long maxSkew = props.getRisk().getMaxClockSkewMs();
    if (req.getSyncTime() == null || Math.abs(nowMs - req.getSyncTime()) > maxSkew) {
      throw new BizException(4002, "invalid syncTime: clock skew too large");
    }

    // 计算业务日期
    ZoneId zoneId = ZoneId.of(props.getTimezone());
    LocalDate bizDate = LocalDate.now(zoneId);

    PlayReportResponse resp = new PlayReportResponse();
    resp.setAccepted(true);
    resp.setBizDate(bizDate.toString());
    resp.setTraceId(currentTraceId());

    PlayDurationReportDO record = new PlayDurationReportDO();
    record.setUserId(req.getUserId());
    record.setSoundId(req.getSoundId());
    record.setBizScene(req.getScene());
    record.setDuration(req.getDuration());
    record.setSyncTime(req.getSyncTime());
    record.setBizDate(bizDate);

    try {
      reportMapper.insert(record);
      resp.setDuplicate(false);
      resp.setReportId(record.getId());

      // 按天聚合播放时长
      PlayDailyAggService.AggOutcome out = aggService.aggregate(req.getUserId(), req.getScene(), bizDate, req.getSyncTime());
      resp.setTotalDuration(out.totalDuration);
      resp.setDeltaDuration(out.deltaDuration);

      // 选择规则 + 预览奖励计划
      AwardPreviewService.PreviewResult preview = awardPreviewService.preview(req.getUserId(), req.getScene(), bizDate, out.totalDuration, resp.getTraceId());
      resp.setHitRuleVersion(preview.getHitRuleVersion());
      resp.setGrayHit(preview.isGrayHit());
      resp.setAwardPlans(preview.getItems());
      return resp;

    } catch (DuplicateKeyException dup) {
      // 弱网重试：幂等
      resp.setDuplicate(true);
      resp.setReportId(null);
      // 最好effort返回当前的 totalDuration 方便 Debug
      UserPlayDailyDO daily = dailyMapper.selectOne(req.getUserId(), req.getScene(), bizDate);
      if (daily != null) {
        resp.setTotalDuration(daily.getTotalDuration());
        resp.setDeltaDuration(0);
        // best effort
        AwardPreviewService.PreviewResult preview = awardPreviewService.preview(req.getUserId(), req.getScene(), bizDate, daily.getTotalDuration(), resp.getTraceId());
        resp.setHitRuleVersion(preview.getHitRuleVersion());
        resp.setGrayHit(preview.isGrayHit());
        resp.setAwardPlans(preview.getItems());
      }
      return resp;
    }
  }

  private String currentTraceId() {
    try {
      if (tracer != null && tracer.currentSpan() != null) {
        return tracer.currentSpan().context().traceId();
      }
    } catch (Exception ignore) {
      // no-op
    }
    return null;
  }
}
