package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportRequest;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.award.model.IssueResult;
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
import java.util.List;
import java.util.Map;
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
  private final AwardIssueService awardIssueService;
  private final FeatureCenterService featureCenterService;
  private final RedisDedupService redisDedupService;
  private final RiskControlService riskControlService;
  private final Tracer tracer;

  public PlayReportAppService(PlayDurationReportMapper reportMapper,
      UserPlayDailyMapper dailyMapper,
      PlayDailyAggService aggService,
      RewardFlowProperties props,
      AwardPreviewService awardPreviewService,
      AwardIssueService awardIssueService,
      FeatureCenterService featureCenterService,
      RedisDedupService redisDedupService,
      RiskControlService riskControlService,
      Tracer tracer) {
    this.reportMapper = reportMapper;
    this.dailyMapper = dailyMapper;
    this.aggService = aggService;
    this.props = props;
    this.awardPreviewService = awardPreviewService;
    this.awardIssueService = awardIssueService;
    this.featureCenterService = featureCenterService;
    this.redisDedupService = redisDedupService;
    this.riskControlService = riskControlService;
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

    // Redis 去重短路
    if (props.getRisk().isRedisDedupEnabled()) {
      boolean first = redisDedupService.tryAcquire(
          req.getScene(), req.getUserId(), req.getSoundId(), req.getSyncTime(),
          props.getRisk().getRedisDedupTtlSeconds());
      if (!first) {
        resp.setDuplicate(true);
        fillFromDailyBestEffort(resp, req.getUserId(), req.getScene(), bizDate);
        return resp;
      }
    }

    // 分钟级风控
    riskControlService.checkMinuteLimits(req.getUserId(), req.getScene(), req.getDuration(), nowMs);

    // 落库 + 聚合 + 预览/发奖 （主流程）
    PlayDurationReportDO record = new PlayDurationReportDO();
    record.setUserId(req.getUserId());
    record.setSoundId(req.getSoundId());
    record.setBizScene(req.getScene());
    record.setDuration(req.getDuration());
    record.setSyncTime(req.getSyncTime());
    record.setBizDate(bizDate);

    try {
      reportMapper.insert(record);  // 明细表插入
      resp.setDuplicate(false);
      resp.setReportId(record.getId());

      // 更新 user_play_daily（总时长）
      PlayDailyAggService.AggOutcome out = aggService.aggregate(req.getUserId(), req.getScene(), bizDate, req.getSyncTime());
      resp.setTotalDuration(out.totalDuration);
      resp.setDeltaDuration(out.deltaDuration);

      // 发奖预览 + 发奖
      previewAndIssue(resp, req.getUserId(), req.getScene(), bizDate, out.totalDuration);
      return resp;

    } catch (DuplicateKeyException dup) {
      // weak-network retry -> duplicate ok
      resp.setDuplicate(true);
      resp.setReportId(null);
      fillFromDailyBestEffort(resp, req.getUserId(), req.getScene(), bizDate);
      return resp;
    }
  }

  // best effort 填充当天累计播放时长 + 发奖预览/发奖结果
  private void fillFromDailyBestEffort(PlayReportResponse resp, String userId, String scene, LocalDate bizDate) {
    UserPlayDailyDO daily = dailyMapper.selectOne(userId, scene, bizDate);
    if (daily == null) {
      resp.setTotalDuration(0);
      resp.setDeltaDuration(0);
      resp.setAwardPlans(List.of());
      return;
    }
    resp.setTotalDuration(daily.getTotalDuration());
    resp.setDeltaDuration(0);
    previewAndIssue(resp, userId, scene, bizDate, daily.getTotalDuration());
  }

  // 预览并发奖
  private void previewAndIssue(PlayReportResponse resp, String userId, String scene, LocalDate bizDate, int totalDuration) {
    // 有哪些奖励命中
    AwardPreviewService.PreviewResult preview =
        awardPreviewService.preview(userId, scene, bizDate, totalDuration, resp.getTraceId());

    resp.setHitRuleVersion(preview.getHitRuleVersion());
    resp.setGrayHit(preview.isGrayHit());

    // 验证feature开关
    boolean issueEnabled = featureCenterService.effectiveForScene(scene).getAwardIssueEnabled();
    if (!issueEnabled) {
      applyDisabled(preview.getItems());
      resp.setAwardPlans(preview.getItems());
      return;
    }

    // 真正发奖 结果回填
    Map<String, IssueResult> issued = awardIssueService.issue(
        userId, scene, bizDate, totalDuration,
        preview.getHitRuleVersion(), preview.isGrayHit(), preview.getItems(), resp.getTraceId());

    applyIssueResult(preview.getItems(), issued);
    resp.setAwardPlans(preview.getItems());
  }

  private void applyDisabled(List<PlayReportResponse.RewardPlanItem> items) {
    if (items == null) {
      return;
    }
    for (PlayReportResponse.RewardPlanItem it : items) {
      if (it == null) {
        continue;
      }
      it.setIssued(false);
      it.setFlowId(null);
      it.setEventId(null);
      it.setIssueStatus("DISABLED");
    }
  }

  private void applyIssueResult(List<PlayReportResponse.RewardPlanItem> items, Map<String, IssueResult> issued) {
    if (items == null || items.isEmpty() || issued == null || issued.isEmpty()) {
      return;
    }
    for (PlayReportResponse.RewardPlanItem it : items) {
      IssueResult r = issued.get(it.getOutBizNo());
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
