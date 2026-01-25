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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayReportAppService {

  private static final Logger log = LoggerFactory.getLogger(PlayReportAppService.class);

  private final PlayDurationReportMapper reportMapper;
  private final UserPlayDailyMapper dailyMapper;
  private final PlayDailyAggService aggService;
  private final PlayDailyRedisAggService redisAggService;
  private final PlayDailyAggRoutingService aggRoutingService;
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
      PlayDailyRedisAggService redisAggService,
      PlayDailyAggRoutingService aggRoutingService,
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
    this.redisAggService = redisAggService;
    this.aggRoutingService = aggRoutingService;
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
    long startMs = System.currentTimeMillis();
    Objects.requireNonNull(req, "req");
    String scene = SceneNormalizer.normalize(req.getScene());
    String userId = req.getUserId();
    String soundId = req.getSoundId();
    String traceId = currentTraceId();

    // 风控校验：处理用户手动改设备时间 + 异常时间上报
    int maxDur = props.getRisk().getMaxDurationPerReport();
    if (req.getDuration() == null || req.getDuration() <= 0 || req.getDuration() > maxDur) {
      log.warn("play report rejected: traceId={}, userId={}, scene={}, duration={}, reason=invalid_duration",
          traceId, userId, scene, req.getDuration());
      throw new BizException(4001, "invalid duration: must be 1.." + maxDur);
    }

    // 风控校验：客户端 syncTime 与服务器时间差值不能过大
    long nowMs = Instant.now().toEpochMilli();
    long maxSkew = props.getRisk().getMaxClockSkewMs();
    if (req.getSyncTime() == null || Math.abs(nowMs - req.getSyncTime()) > maxSkew) {
      log.warn("play report rejected: traceId={}, userId={}, scene={}, syncTime={}, reason=invalid_sync_time",
          traceId, userId, scene, req.getSyncTime());
      throw new BizException(4002, "invalid syncTime: clock skew too large");
    }

    // 计算业务日期
    ZoneId zoneId = ZoneId.of(props.getTimezone());
    LocalDate bizDate = LocalDate.now(zoneId);

    PlayReportResponse resp = new PlayReportResponse();
    resp.setAccepted(true);
    resp.setBizDate(bizDate.toString());
    resp.setTraceId(traceId);

    log.info("play report received: traceId={}, userId={}, scene={}, soundId={}, duration={}, syncTime={}, bizDate={}",
        traceId, userId, scene, soundId, req.getDuration(), req.getSyncTime(), bizDate);

    try {
      // Redis 去重短路
      if (props.getRisk().isRedisDedupEnabled()) {
        boolean first = redisDedupService.tryAcquire(
            scene, userId, soundId, req.getSyncTime(),
            props.getRisk().getRedisDedupTtlSeconds());
        if (!first) {
          resp.setDuplicate(true);
          fillFromDailyBestEffort(resp, userId, scene, bizDate);
          AwardPlanStats stats = calcAwardPlanStats(resp.getAwardPlans());
          log.info("play report duplicate(redis): traceId={}, userId={}, scene={}, bizDate={}, totalDuration={}, awardPlans={}, issued={}, failed={}, disabled={}, costMs={}",
              traceId, userId, scene, bizDate, resp.getTotalDuration(),
              stats.planCount, stats.issuedCount, stats.failedCount, stats.disabledCount,
              System.currentTimeMillis() - startMs);
          return resp;
        }
      }

      // 分钟级风控
      riskControlService.checkMinuteLimits(userId, scene, req.getDuration(), nowMs);

      // 落库 + 聚合 + 预览/发奖 （主流程）
      PlayDurationReportDO record = new PlayDurationReportDO();
      record.setUserId(userId);
      record.setSoundId(soundId);
      record.setBizScene(scene);
      record.setDuration(req.getDuration());
      record.setSyncTime(req.getSyncTime());
      record.setBizDate(bizDate);

      reportMapper.insert(record);  // 明细表插入
      resp.setDuplicate(false);
      resp.setReportId(record.getId());

      // 更新 user_play_daily（总时长）
      int totalDuration;
      int deltaDuration;
      
      /*路由判定：是否使用 Redis 聚合
      */
      boolean useRedisAgg = redisAggService.enabled()
          && aggRoutingService.shouldUseRedis(userId, scene, nowMs);
      if (useRedisAgg) {
        try {
          // 增量写入 Redis，返回总时长与本次增量
          PlayDailyRedisAggService.AggOutcome out =
              redisAggService.recordAndGetTotal(userId, scene, bizDate, req.getDuration(), req.getSyncTime());
          totalDuration = out.totalDuration;
          deltaDuration = out.deltaDuration;
        } catch (Exception ex) {
          // fallback 到 Mysql 聚合
          log.warn("redis agg failed, fallback to mysql agg: traceId={}, userId={}, scene={}, bizDate={}, err={}",
              traceId, userId, scene, bizDate, ex.toString());
          PlayDailyAggService.AggOutcome out =
              aggService.aggregate(userId, scene, bizDate, req.getSyncTime());
          totalDuration = out.totalDuration;
          deltaDuration = out.deltaDuration;
        }
      } else {
        PlayDailyAggService.AggOutcome out =
            aggService.aggregate(userId, scene, bizDate, req.getSyncTime());
        totalDuration = out.totalDuration;
        deltaDuration = out.deltaDuration;
      }
      resp.setTotalDuration(totalDuration);
      resp.setDeltaDuration(deltaDuration);

      // 发奖预览 + 发奖
      previewAndIssue(resp, userId, scene, bizDate, totalDuration);
      AwardPlanStats stats = calcAwardPlanStats(resp.getAwardPlans());
      log.info("play report success: traceId={}, userId={}, scene={}, bizDate={}, reportId={}, totalDuration={}, deltaDuration={}, awardPlans={}, issued={}, failed={}, disabled={}, costMs={}",
          traceId, userId, scene, bizDate, resp.getReportId(), resp.getTotalDuration(), resp.getDeltaDuration(),
          stats.planCount, stats.issuedCount, stats.failedCount, stats.disabledCount,
          System.currentTimeMillis() - startMs);
      return resp;

    } catch (DuplicateKeyException dup) {
      // weak-network retry -> duplicate ok
      resp.setDuplicate(true);
      resp.setReportId(null);
      fillFromDailyBestEffort(resp, userId, scene, bizDate);
      AwardPlanStats stats = calcAwardPlanStats(resp.getAwardPlans());
      log.info("play report duplicate(db): traceId={}, userId={}, scene={}, bizDate={}, totalDuration={}, awardPlans={}, issued={}, failed={}, disabled={}, costMs={}",
          traceId, userId, scene, bizDate, resp.getTotalDuration(),
          stats.planCount, stats.issuedCount, stats.failedCount, stats.disabledCount,
          System.currentTimeMillis() - startMs);
      return resp;
    } catch (BizException be) {
      log.warn("play report rejected: traceId={}, userId={}, scene={}, code={}, msg={}, costMs={}",
          traceId, userId, scene, be.getCode(), be.getMessage(), System.currentTimeMillis() - startMs);
      throw be;
    } catch (Exception ex) {
      log.error("play report failed: traceId={}, userId={}, scene={}, costMs={}",
          traceId, userId, scene, System.currentTimeMillis() - startMs, ex);
      throw ex;
    }
  }

  // best effort 填充当天累计播放时长 + 发奖预览/发奖结果
  private void fillFromDailyBestEffort(PlayReportResponse resp, String userId, String scene, LocalDate bizDate) {
    if (redisAggService.enabled() && aggRoutingService.isHot(userId, scene)) {
      try {
        PlayDailyRedisAggService.AggOutcome out = redisAggService.getTotalBestEffort(userId, scene, bizDate);
        resp.setTotalDuration(out.totalDuration);
        resp.setDeltaDuration(0);
        previewAndIssue(resp, userId, scene, bizDate, out.totalDuration);
        return;
      } catch (Exception ex) {
        log.warn("redis agg best-effort failed, fallback to mysql: traceId={}, userId={}, scene={}, bizDate={}, err={}",
            resp.getTraceId(), userId, scene, bizDate, ex.toString());
      }
    }

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
    int planCount = preview.getItems() == null ? 0 : preview.getItems().size();
    log.debug("award preview: traceId={}, userId={}, scene={}, bizDate={}, totalDuration={}, ruleVersion={}, grayHit={}, planCount={}",
        resp.getTraceId(), userId, scene, bizDate, totalDuration, preview.getHitRuleVersion(), preview.isGrayHit(), planCount);

    // 验证feature开关
    boolean issueEnabled = featureCenterService.effectiveForScene(scene).getAwardIssueEnabled();
    if (!issueEnabled) {
      applyDisabled(preview.getItems());
      resp.setAwardPlans(preview.getItems());
      AwardPlanStats stats = calcAwardPlanStats(resp.getAwardPlans());
      log.info("award issue disabled: traceId={}, userId={}, scene={}, bizDate={}, planCount={}, ruleVersion={}, grayHit={}",
          resp.getTraceId(), userId, scene, bizDate, stats.planCount, preview.getHitRuleVersion(), preview.isGrayHit());
      return;
    }

    // 真正发奖 结果回填
    Map<String, IssueResult> issued = awardIssueService.issue(
        userId, scene, bizDate, totalDuration,
        preview.getHitRuleVersion(), preview.isGrayHit(), preview.getItems(), resp.getTraceId());

    applyIssueResult(preview.getItems(), issued);
    resp.setAwardPlans(preview.getItems());
    AwardPlanStats stats = calcAwardPlanStats(resp.getAwardPlans());
    log.info("award issue completed: traceId={}, userId={}, scene={}, bizDate={}, planCount={}, issued={}, failed={}, ruleVersion={}, grayHit={}",
        resp.getTraceId(), userId, scene, bizDate, stats.planCount, stats.issuedCount, stats.failedCount,
        preview.getHitRuleVersion(), preview.isGrayHit());
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

  private AwardPlanStats calcAwardPlanStats(List<PlayReportResponse.RewardPlanItem> items) {
    AwardPlanStats stats = new AwardPlanStats();
    if (items == null || items.isEmpty()) {
      return stats;
    }
    for (PlayReportResponse.RewardPlanItem it : items) {
      if (it == null) {
        continue;
      }
      stats.planCount++;
      if (Boolean.TRUE.equals(it.getIssued())) {
        stats.issuedCount++;
      }
      String status = it.getIssueStatus();
      if (status != null) {
        if ("FAILED".equalsIgnoreCase(status)) {
          stats.failedCount++;
        } else if ("DISABLED".equalsIgnoreCase(status)) {
          stats.disabledCount++;
        }
      }
    }
    return stats;
  }

  private static class AwardPlanStats {
    private int planCount;
    private int issuedCount;
    private int failedCount;
    private int disabledCount;
  }
}
