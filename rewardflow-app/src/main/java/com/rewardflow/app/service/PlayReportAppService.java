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
import java.util.Map;
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
  private final AwardIssueService awardIssueService;
  private final Tracer tracer;

  public PlayReportAppService(PlayDurationReportMapper reportMapper,
                              UserPlayDailyMapper dailyMapper,
                              PlayDailyAggService aggService,
                              RewardFlowProperties props,
                              AwardPreviewService awardPreviewService,
                              AwardIssueService awardIssueService,
                              Tracer tracer) {
    this.reportMapper = reportMapper;
    this.dailyMapper = dailyMapper;
    this.aggService = aggService;
    this.props = props;
    this.awardPreviewService = awardPreviewService;
    this.awardIssueService = awardIssueService;
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
      // 写发奖业务状态+outbox
      Map<String, AwardIssueService.IssueResult> issued = awardIssueService.issue(
          req.getUserId(), req.getScene(), bizDate, out.totalDuration,
          preview.getHitRuleVersion(), preview.isGrayHit(), preview.getItems(), resp.getTraceId());

      applyIssueResult(preview.getItems(), issued);

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

        Map<String, AwardIssueService.IssueResult> issued = awardIssueService.issue(
            req.getUserId(), req.getScene(), bizDate, daily.getTotalDuration(),
            preview.getHitRuleVersion(), preview.isGrayHit(), preview.getItems(), resp.getTraceId());
        applyIssueResult(preview.getItems(), issued);
        resp.setAwardPlans(preview.getItems());
      }
      return resp;
    }
  }

  // 把issue 返回的 Map 里的发奖结果回填到 plans 列表中
  private void applyIssueResult(java.util.List<PlayReportResponse.RewardPlanItem> items,
                                java.util.Map<String, AwardIssueService.IssueResult> issued) {
    if (items == null || items.isEmpty() || issued == null || issued.isEmpty()) {
      return;
    }
    for (PlayReportResponse.RewardPlanItem it : items) {
      AwardIssueService.IssueResult r = issued.get(it.getOutBizNo());
      if (r == null) {
        continue;
      }
      it.setIssued(r.getIssued());
      it.setFlowId(r.getFlowId());
      it.setEventId(r.getEventId());
      it.setIssueStatus(r.getIssueStatus());
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
