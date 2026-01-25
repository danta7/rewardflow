package com.rewardflow.app.service;

import com.rewardflow.app.config.RewardFlowProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 路由判定服务，根据usreid和scene的访问频率决定是否吧“play daily agg” 走Redis方案
 * 
 */
@Service
public class PlayDailyAggRoutingService {

  private static final Logger log = LoggerFactory.getLogger(PlayDailyAggRoutingService.class);

  private static final String CNT_PREFIX = "rf:play:hot:cnt:";
  private static final String FLAG_PREFIX = "rf:play:hot:flag:";

  private final StringRedisTemplate redis;
  private final RewardFlowProperties props;

  public PlayDailyAggRoutingService(StringRedisTemplate redis, RewardFlowProperties props) {
    this.redis = redis;
    this.props = props;
  }

  public boolean shouldUseRedis(String userId, String scene, long nowMs) {
    RewardFlowProperties.PlayDailyAgg cfg = props.getPlayDailyAgg();
    if (!cfg.isRedisEnabled()) {
      return false;
    }
    // 如果当前userID 和 scene 已经是hot，直接返回true
    if (isHot(userId, scene)) {
      return true;
    }

    long minute = nowMs / 60_000L;
    String cntKey = CNT_PREFIX + encodePart(scene) + ":" + encodePart(userId) + ":" + minute;
    Long cnt = redis.opsForValue().increment(cntKey);
    if (cnt != null && cnt == 1L) {
      redis.expire(cntKey, Duration.ofSeconds(120));
    }

    // 如果本分钟访问量超过阈值，设置hot标记
    if (cnt != null && cnt >= cfg.getHighFreqThresholdPerMinute()) {
      String flagKey = flagKey(userId, scene);
      redis.opsForValue().set(flagKey, "1", Duration.ofSeconds(cfg.getHotWindowSeconds()));
      log.info("play daily agg hot flag set: userId={}, scene={}, minute={}, cnt={}, threshold={}, windowSeconds={}",
          userId, scene, minute, cnt, cfg.getHighFreqThresholdPerMinute(), cfg.getHotWindowSeconds());
      return true;
    }

    return false;
  }

  public boolean isHot(String userId, String scene) {
    String flagKey = flagKey(userId, scene);
    Boolean exists = redis.hasKey(flagKey);
    return exists != null && exists;
  }

  private String flagKey(String userId, String scene) {
    return FLAG_PREFIX + encodePart(scene) + ":" + encodePart(userId);
  }

  private String encodePart(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
