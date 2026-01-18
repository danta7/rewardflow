package com.rewardflow.app.service;

import com.rewardflow.api.dto.PlayReportRequest;
import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.app.exception.BizException;
import com.rewardflow.infra.mysql.entity.PlayDurationReportDO;
import com.rewardflow.infra.mysql.mapper.PlayDurationReportMapper;
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
  private final RewardFlowProperties props;
  private final Tracer tracer;

  public PlayReportAppService(PlayDurationReportMapper reportMapper,
                              RewardFlowProperties props,
                              Tracer tracer) {
    this.reportMapper = reportMapper;
    this.props = props;
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

    PlayDurationReportDO record = new PlayDurationReportDO();
    record.setUserId(req.getUserId());
    record.setSoundId(req.getSoundId());
    record.setBizScene(req.getScene());
    record.setDuration(req.getDuration());
    record.setSyncTime(req.getSyncTime());
    record.setBizDate(bizDate);

    PlayReportResponse resp = new PlayReportResponse();
    resp.setAccepted(true);
    resp.setBizDate(bizDate.toString());
    resp.setTraceId(currentTraceId());

    try {
      reportMapper.insert(record);
      resp.setDuplicate(false);
      resp.setReportId(record.getId());
      return resp;
    } catch (DuplicateKeyException dup) {
      // Weak network retry → duplicate report, idempotent ok
      resp.setDuplicate(true);
      resp.setReportId(null);
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
