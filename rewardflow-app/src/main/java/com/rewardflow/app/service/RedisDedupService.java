package com.rewardflow.app.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用 Redis 做“短路去重”（先挡一下重复请求）
 *
 * <p> Redis 去重复只是一个性能优化，用来在弱网重试上报时减少对 Mysql 的压力
 * 最终的幂等性仍然靠 MySQL的唯一索引保证
 */
@Service
public class RedisDedupService {

  private static final String KEY_PREFIX = "rf:play:dedup:";

  private final StringRedisTemplate redis;

  public RedisDedupService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * @return 第一次写入返回 true，重复写入返回 false
   */
  public boolean tryAcquire(String scene, String userId, String soundId, long syncTime, long ttlSeconds) {
    // rf:play:dedup:audio_play:u1:s1:1697059200
    String key = KEY_PREFIX + scene + ":" + userId + ":" + soundId + ":" + syncTime;
    Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
    return ok != null && ok;
  }
}
