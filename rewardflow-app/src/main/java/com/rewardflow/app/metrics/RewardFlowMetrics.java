package com.rewardflow.app.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 业务指标埋点（Micrometer -> Prometheus）。
 *
 * 生产化要点：
 * - tag 维度严格低基数（scene/prize_code/stage/result/event_type）
 * - 对“易膨胀字段”（如 issueStatus）做归一化，避免 time-series 爆炸
 * - meter 复用（缓存 Counter/Timer），避免压测内存/CPU抖动
 * - best-effort：metrics 任何异常不影响主业务
 */
@Component
public class RewardFlowMetrics {

  // ---- Metric names (keep stable) ----
  private static final String M_PLAY_REPORT_TOTAL = "rewardflow_play_report_total";
  private static final String M_PLAY_REPORT_LATENCY = "rewardflow_play_report_latency";

  private static final String M_AWARD_ISSUE_TOTAL = "rewardflow_award_issue_total";

  // outbox gauges
  private static final String M_OUTBOX_PENDING = "rewardflow_outbox_pending";
  private static final String M_OUTBOX_FAILED = "rewardflow_outbox_failed";

  // outbox counters/timers
  private static final String M_OUTBOX_PUBLISHED_TOTAL = "rewardflow_outbox_published_total";
  private static final String M_OUTBOX_PUBLISH_TOTAL = "rewardflow_outbox_publish_total";
  private static final String M_OUTBOX_PUBLISH_LATENCY = "rewardflow_outbox_publish_latency";

  private final MeterRegistry registry;

  // -------- Play report --------
  private final ConcurrentHashMap<String, Counter> playReportCounter = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> playReportTimer = new ConcurrentHashMap<>();

  // -------- Award issue --------
  private final ConcurrentHashMap<String, Counter> awardIssueCounter = new ConcurrentHashMap<>();

  // -------- Outbox gauges --------
  private final AtomicLong outboxPending = new AtomicLong(0);
  private final AtomicLong outboxFailed = new AtomicLong(0);

  // -------- Outbox counters --------
  private final Counter outboxPublishedCounter;

  // outbox publish: low-cardinality tags (event_type + result)
  private final ConcurrentHashMap<String, Counter> outboxPublishCounter = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> outboxPublishTimer = new ConcurrentHashMap<>();

  public RewardFlowMetrics(MeterRegistry registry) {
    this.registry = registry;

    // Gauges (instant values)
    registry.gauge(M_OUTBOX_PENDING, outboxPending);
    registry.gauge(M_OUTBOX_FAILED, outboxFailed);

    // Counters (monotonic)
    this.outboxPublishedCounter = Counter.builder(M_OUTBOX_PUBLISHED_TOTAL)
        .description("outbox published total (counter)")
        .register(registry);
  }

  // ---------------------------
  // Play Report
  // ---------------------------

  public Timer.Sample startTimer() {
    try {
      return Timer.start(registry);
    } catch (Exception ignore) {
      return null;
    }
  }

  public void recordReport(String scene, String result, Timer.Sample sample) {
    try {
      String s = safe(scene);
      String r = safe(result);
      String key = "scene=" + s + ",result=" + r;

      Counter c = playReportCounter.computeIfAbsent(key, k ->
          Counter.builder(M_PLAY_REPORT_TOTAL)
              .description("play report requests")
              .tag("scene", s)
              .tag("result", r)
              .register(registry));
      c.increment();

      Timer t = playReportTimer.computeIfAbsent(key, k ->
          Timer.builder(M_PLAY_REPORT_LATENCY)
              .description("play report latency")
              .tag("scene", s)
              .tag("result", r)
              .publishPercentileHistogram()
              .register(registry));

      if (sample != null) {
        sample.stop(t);
      }
    } catch (Exception ignore) {
      // best-effort
    }
  }

  @Deprecated
  public void recordPlayReport(String scene, String result, long costNs) {
    try {
      String s = safe(scene);
      String r = safe(result);
      String key = "scene=" + s + ",result=" + r;

      Counter c = playReportCounter.computeIfAbsent(key, k ->
          Counter.builder(M_PLAY_REPORT_TOTAL)
              .description("play report requests")
              .tag("scene", s)
              .tag("result", r)
              .register(registry));
      c.increment();

      Timer t = playReportTimer.computeIfAbsent(key, k ->
          Timer.builder(M_PLAY_REPORT_LATENCY)
              .description("play report latency")
              .tag("scene", s)
              .tag("result", r)
              .publishPercentileHistogram()
              .register(registry));
      t.record(costNs, TimeUnit.NANOSECONDS);
    } catch (Exception ignore) {
      // best-effort
    }
  }

  // ---------------------------
  // Award Issue (Production)
  // ---------------------------

  /**
   * 把 issueStatus 归一化，避免 tag 基数膨胀。
   *
   * result（稳定低基数）：
   * - CREATED_NEW  : 首次创建 reward_flow（issued=true）
   * - CREATED_DUP  : 幂等命中/重入（issued=false 且 status=CREATED）
   * - DISABLED
   * - FAILED
   * - UNKNOWN
   */
  public void incAwardIssueAttempt(String scene,
                                   String prizeCode,
                                   int stage,
                                   String issueStatus,
                                   Boolean issued) {
    try {
      String s = safe(scene);
      String p = safe(prizeCode);
      String st = safeStage(stage);
      String r = normalizeAwardIssueResult(issueStatus, issued);

      String key = "scene=" + s + ",prize=" + p + ",stage=" + st + ",result=" + r;

      Counter c = awardIssueCounter.computeIfAbsent(key, k ->
          Counter.builder(M_AWARD_ISSUE_TOTAL)
              .description("award issue attempts")
              .tag("scene", s)
              .tag("prize_code", p)
              .tag("stage", st)
              .tag("result", r)
              .register(registry));
      c.increment();
    } catch (Exception ignore) {
      // best-effort
    }
  }

  /**
   * 兼容旧调用：只传 result 的场景（不推荐生产使用）。
   */
  public void incAwardIssue(String scene, String prizeCode, int stage, String result) {
    incAwardIssueAttempt(scene, prizeCode, stage, result, null);
  }

  // ---------------------------
  // Outbox
  // ---------------------------

  public void setOutboxPending(long n) {
    try {
      outboxPending.set(Math.max(0, n));
    } catch (Exception ignore) {
      // best-effort
    }
  }

  public void setOutboxFailed(long n) {
    try {
      outboxFailed.set(Math.max(0, n));
    } catch (Exception ignore) {
      // best-effort
    }
  }

  /**
   * 累计发布数（Counter 单调递增）。
   */
  public void incOutboxPublished(long n) {
    if (n <= 0) return;
    try {
      outboxPublishedCounter.increment(n);
    } catch (Exception ignore) {
      // best-effort
    }
  }

  /**
   * outbox publish attempts counter.
   * result: SENT | FAIL（稳定低基数）
   */
  public void incOutboxPublish(String eventType, String result) {
    try {
      String t = safe(eventType);
      String r = normalizeOutboxResult(result);

      String key = "type=" + t + ",result=" + r;
      Counter c = outboxPublishCounter.computeIfAbsent(key, k ->
          Counter.builder(M_OUTBOX_PUBLISH_TOTAL)
              .description("outbox publish attempts")
              .tag("event_type", t)
              .tag("result", r)
              .register(registry));
      c.increment();
    } catch (Exception ignore) {
      // best-effort
    }
  }

  public Sample startOutboxPublishTimer() {
    try {
      return Timer.start(registry);
    } catch (Exception ignore) {
      return null;
    }
  }

  public void stopOutboxPublishTimer(Sample sample, String eventType, String result) {
    if (sample == null) return;
    try {
      String t = safe(eventType);
      String r = normalizeOutboxResult(result);
      String key = "type=" + t + ",result=" + r;

      Timer timer = outboxPublishTimer.computeIfAbsent(key, k ->
          Timer.builder(M_OUTBOX_PUBLISH_LATENCY)
              .description("outbox publish latency")
              .tag("event_type", t)
              .tag("result", r)
              .publishPercentileHistogram()
              .register(registry));
      sample.stop(timer);
    } catch (Exception ignore) {
      // best-effort
    }
  }

  // ---------------------------
  // Helpers
  // ---------------------------

  private static String safe(String s) {
    if (s == null) return "unknown";
    String t = s.trim();
    return t.isEmpty() ? "unknown" : t;
  }

  private static String safeStage(int stage) {
    return stage > 0 ? String.valueOf(stage) : "0";
  }

  private static String normalizeOutboxResult(String r) {
    if (r == null) return "UNKNOWN";
    String u = r.trim().toUpperCase();
    if ("SENT".equals(u)) return "SENT";
    if ("FAIL".equals(u) || "FAILED".equals(u)) return "FAIL";
    return "UNKNOWN";
  }

  private static String normalizeAwardIssueResult(String issueStatus, Boolean issued) {
    if (issueStatus == null || issueStatus.isBlank()) {
      return Boolean.TRUE.equals(issued) ? "CREATED_NEW" : "UNKNOWN";
    }
    String u = issueStatus.trim().toUpperCase();

    if ("FAILED".equals(u) || "FAIL".equals(u)) return "FAILED";
    if ("DISABLED".equals(u)) return "DISABLED";

    if ("CREATED".equals(u)) {
      return Boolean.TRUE.equals(issued) ? "CREATED_NEW" : "CREATED_DUP";
    }

    return "UNKNOWN";
  }
}
