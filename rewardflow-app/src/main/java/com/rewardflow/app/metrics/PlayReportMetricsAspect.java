package com.rewardflow.app.metrics;

import com.rewardflow.api.dto.PlayReportResponse;
import com.rewardflow.app.exception.BizException;
import com.rewardflow.app.service.SceneNormalizer;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 围绕热路径的 Metrics, PlayReportAppService#report.
 *
 * <p>我们保持标签低基数。不要使用 userId 或 soundId 作为标签。
 */
@Aspect
@Component
public class PlayReportMetricsAspect {

  private final RewardFlowMetrics metrics;

  public PlayReportMetricsAspect(RewardFlowMetrics metrics) {
    this.metrics = metrics;
  }

  @Around("execution(* com.rewardflow.app.service.PlayReportAppService.report(..))")
  public Object aroundReport(ProceedingJoinPoint pjp) throws Throwable {
    // args: PlayReportRequest req
    String scene = "unknown";
    try {
      Object[] args = pjp.getArgs();
      if (args != null && args.length > 0 && args[0] != null) {
        // 使用反射以避免直接依赖 PlayReportRequest 类
        try {
          scene = String.valueOf(args[0].getClass().getMethod("getScene").invoke(args[0]));
          scene = SceneNormalizer.normalize(scene);
        } catch (Exception ignore) {
          // keep unknown
        }
      }
    } catch (Exception ignore) {
      // keep unknown
    }

    // 计时开始
    Timer.Sample sample = metrics.startTimer();
    String result = "ok";
    try {
      Object ret = pjp.proceed();
      if (ret instanceof PlayReportResponse resp) {
        if (resp.isDuplicate()) {
          result = "duplicate";
        }
      }
      metrics.recordReport(scene, result, sample);
      return ret;
    } catch (BizException be) {
      // BizException includes validation, risk reject, etc
      result = "biz_error";
      metrics.recordReport(scene, result, sample);
      throw be;
    } catch (Throwable t) {
      result = "error";
      metrics.recordReport(scene, result, sample);
      throw t;
    }
  }
}
