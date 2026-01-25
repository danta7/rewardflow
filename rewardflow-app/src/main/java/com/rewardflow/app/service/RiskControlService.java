package com.rewardflow.app.service;

import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.app.exception.BizException;
import java.util.Map;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 风控增强：防刷接口、防伪造duration或刷时长
 *
 * <ul>
 *   <li>基础校验（比如 duration 合法范围、客户端时间与服务端时间偏差）在 PlayReportAppService 中</li>
 *   <li>使用 Redis 做“按分钟分桶”的限制：限制每分钟上报次数以及每分钟累计播放时长的突发上限</li>
 * </ul>
 */
@Service
public class RiskControlService {

  private static final Logger log = LoggerFactory.getLogger(RiskControlService.class);

  private static final String CNT_PREFIX = "rf:risk:cnt:";
  private static final String DUR_PREFIX = "rf:risk:dur:";

  private final StringRedisTemplate redis;
  private final RiskEventService riskEventService;
  private final RewardFlowProperties props;

  public RiskControlService(StringRedisTemplate redis,
                            RiskEventService riskEventService,
                            RewardFlowProperties props) {
    this.redis = redis;
    this.riskEventService = riskEventService;
    this.props = props;
  }

  /**
   * 使用 Redis INCR/INCRBY 做“按分钟分桶”的限制,key过期很快
   */
  public void checkMinuteLimits(String userId, String scene, int duration, long nowMs) {
    RewardFlowProperties.Risk risk = props.getRisk();
    long minute = nowMs / 60_000L;
    String cntKey = CNT_PREFIX + scene + ":" + userId + ":" + minute;
    String durKey = DUR_PREFIX + scene + ":" + userId + ":" + minute;

    // 记数限制
    Long cnt = redis.opsForValue().increment(cntKey);
    if (cnt != null && cnt == 1L) {
      redis.expire(cntKey, Duration.ofSeconds(120));
    }
    if (cnt != null && cnt > risk.getMaxReportsPerMinute()) {
      riskEventService.log(
          userId,
          scene,
          null,
          "RATE_LIMIT",
          Map.of("kind", "count", "count", cnt, "limit", risk.getMaxReportsPerMinute()));
      log.warn("risk limit exceeded: userId={}, scene={}, kind=count, minute={}, count={}, limit={}",
          userId, scene, minute, cnt, risk.getMaxReportsPerMinute());
      throw new BizException(4291, "too many reports per minute");
    }

    Long sum = redis.opsForValue().increment(durKey, duration);
    if (sum != null && sum == duration) {
      redis.expire(durKey, Duration.ofSeconds(120));
    }
    if (sum != null && sum > risk.getMaxDurationPerMinute()) {
      riskEventService.log(
          userId,
          scene,
          null,
          "RATE_LIMIT",
          Map.of("kind", "duration", "sum", sum, "limit", risk.getMaxDurationPerMinute()));
      log.warn("risk limit exceeded: userId={}, scene={}, kind=duration, minute={}, sum={}, limit={}",
          userId, scene, minute, sum, risk.getMaxDurationPerMinute());
      throw new BizException(4292, "too much duration per minute");
    }
  }
}
