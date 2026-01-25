package com.rewardflow.app.service;

import com.rewardflow.app.config.RewardFlowProperties;
import com.rewardflow.infra.mysql.entity.UserPlayDailyDO;
import com.rewardflow.infra.mysql.mapper.UserPlayDailyMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Play Daily 聚合服务，高并发下先把增量写到 Redis，定时批量刷新到 Mysql
 */

@Service
public class PlayDailyRedisAggService {

  private static final Logger log = LoggerFactory.getLogger(PlayDailyRedisAggService.class);

  private static final String KEY_PREFIX = "rf:play:daily:";
  private static final String DIRTY_ZSET = "rf:play:daily:dirty";

  private static final String FIELD_BASE_TOTAL = "base_total";
  private static final String FIELD_BASE_LAST_SYNC = "base_last_sync";
  private static final String FIELD_PENDING_DELTA = "pending_delta";
  private static final String FIELD_PENDING_MAX_SYNC = "pending_max_sync";
  private static final String FIELD_INFLIGHT_DELTA = "inflight_delta";
  private static final String FIELD_INFLIGHT_MAX_SYNC = "inflight_max_sync";
  private static final String FIELD_INFLIGHT_AT = "inflight_at";
  private static final String FIELD_UPDATED_AT = "updated_at";

  private final StringRedisTemplate redis;
  private final UserPlayDailyMapper dailyMapper;
  private final RewardFlowProperties props;

  private final DefaultRedisScript<List> recordScript = new DefaultRedisScript<>(RECORD_SCRIPT, List.class);
  private final DefaultRedisScript<List> reserveScript = new DefaultRedisScript<>(RESERVE_SCRIPT, List.class);
  private final DefaultRedisScript<Long> commitScript = new DefaultRedisScript<>(COMMIT_SCRIPT, Long.class);
  private final DefaultRedisScript<Long> rollbackScript = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);

  public PlayDailyRedisAggService(StringRedisTemplate redis,
                                  UserPlayDailyMapper dailyMapper,
                                  RewardFlowProperties props) {
    this.redis = redis;
    this.dailyMapper = dailyMapper;
    this.props = props;
  }

  public boolean enabled() {
    return props.getPlayDailyAgg().isRedisEnabled();
  }

  // 写入一次播放增量，返回当前总量和本次增量
  public AggOutcome recordAndGetTotal(String userId, String scene, LocalDate bizDate, int duration, long syncTime) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(scene, "scene");
    Objects.requireNonNull(bizDate, "bizDate");
    ensureBase(userId, scene, bizDate);

    String key = keyFor(userId, scene, bizDate);
    long nowMs = System.currentTimeMillis();
    long ttlMs = props.getPlayDailyAgg().getRedisTtlSeconds() * 1000L;

    List<Long> out = toLongList(redis.execute(
        recordScript,
        List.of(key, DIRTY_ZSET),
        String.valueOf(duration),
        String.valueOf(syncTime),
        String.valueOf(nowMs),
        String.valueOf(ttlMs)
    ));
    if (out.isEmpty()) {
      log.warn("redis agg record returned empty: userId={}, scene={}, bizDate={}, duration={}, syncTime={}",
          userId, scene, bizDate, duration, syncTime);
    }

    int total = out.size() > 0 ? out.get(0).intValue() : 0;
    int delta = out.size() > 1 ? out.get(1).intValue() : 0;
    AggOutcome res = new AggOutcome();
    res.totalDuration = total;
    res.deltaDuration = delta;
    return res;
  }

  public AggOutcome getTotalBestEffort(String userId, String scene, LocalDate bizDate) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(scene, "scene");
    Objects.requireNonNull(bizDate, "bizDate");
    ensureBase(userId, scene, bizDate);

    String key = keyFor(userId, scene, bizDate);
    Integer baseTotal = toInt(redis.opsForHash().get(key, FIELD_BASE_TOTAL), 0);
    Integer pendingDelta = toInt(redis.opsForHash().get(key, FIELD_PENDING_DELTA), 0);

    AggOutcome res = new AggOutcome();
    res.totalDuration = baseTotal + pendingDelta;
    res.deltaDuration = 0;
    return res;
  }

  public int flushOnce() {
    long nowMs = System.currentTimeMillis();
    RewardFlowProperties.PlayDailyAgg cfg = props.getPlayDailyAgg();
    long flushDelayMs = Math.max(0L, cfg.getFlushIntervalMs());
    long maxScore = nowMs - flushDelayMs;
    int batchSize = Math.max(1, cfg.getFlushBatchSize());

    ZSetOperations<String, String> zset = redis.opsForZSet();
    Set<String> keys = zset.rangeByScore(DIRTY_ZSET, 0, maxScore, 0, batchSize);
    if (keys == null || keys.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      long ttlMs = cfg.getRedisTtlSeconds() * 1000L;
      List<Long> reserved = toLongList(redis.execute(
          reserveScript,
          List.of(key, DIRTY_ZSET),
          String.valueOf(nowMs),
          String.valueOf(cfg.getInflightTimeoutMs()),
          String.valueOf(ttlMs)
      ));
      if (reserved == null || reserved.size() < 2) {
        continue;
      }

      long delta = reserved.get(0);
      long maxSync = reserved.get(1);
      if (delta <= 0) {
        continue;
      }

      ParsedKey parsed = parseKey(key);
      if (parsed == null) {
        log.warn("play daily agg key parse failed: key={}", key);
        continue;
      }

      try {
        dailyMapper.upsertAddDelta(parsed.userId, parsed.scene, parsed.bizDate, (int) delta, maxSync);
        UserPlayDailyDO daily = dailyMapper.selectOne(parsed.userId, parsed.scene, parsed.bizDate);
        int baseTotal = daily == null || daily.getTotalDuration() == null ? 0 : daily.getTotalDuration();
        long baseLastSync = daily == null || daily.getLastSyncTime() == null ? 0L : daily.getLastSyncTime();

        redis.execute(
            commitScript,
            List.of(key, DIRTY_ZSET),
            String.valueOf(baseTotal),
            String.valueOf(baseLastSync),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(ttlMs)
        );
        processed++;
      } catch (Exception ex) {
        log.warn("play daily agg flush failed: key={}, userId={}, scene={}, bizDate={}, delta={}, maxSync={}, err={}",
            key, parsed.userId, parsed.scene, parsed.bizDate, delta, maxSync, ex.toString());
        redis.execute(
            rollbackScript,
            List.of(key, DIRTY_ZSET),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(ttlMs)
        );
      }
    }
    if (processed > 0) {
      log.info("play daily agg flush done: processed={}, batchSize={}, maxScore={}, delayMs={}",
          processed, batchSize, maxScore, flushDelayMs);
    }
    return processed;
  }

  private void ensureBase(String userId, String scene, LocalDate bizDate) {
    String key = keyFor(userId, scene, bizDate);
    Boolean hasBase = redis.opsForHash().hasKey(key, FIELD_BASE_TOTAL);
    if (hasBase != null && hasBase) {
      return;
    }

    UserPlayDailyDO daily = dailyMapper.selectOne(userId, scene, bizDate);
    int baseTotal = daily == null || daily.getTotalDuration() == null ? 0 : daily.getTotalDuration();
    long baseLastSync = daily == null || daily.getLastSyncTime() == null ? 0L : daily.getLastSyncTime();

    redis.opsForHash().put(key, FIELD_BASE_TOTAL, String.valueOf(baseTotal));
    redis.opsForHash().put(key, FIELD_BASE_LAST_SYNC, String.valueOf(baseLastSync));
    redis.opsForHash().put(key, FIELD_PENDING_DELTA, "0");
    redis.opsForHash().put(key, FIELD_PENDING_MAX_SYNC, "0");
    redis.opsForHash().put(key, FIELD_INFLIGHT_DELTA, "0");
    redis.opsForHash().put(key, FIELD_INFLIGHT_MAX_SYNC, "0");
    redis.opsForHash().put(key, FIELD_INFLIGHT_AT, "0");
    redis.opsForHash().put(key, FIELD_UPDATED_AT, String.valueOf(System.currentTimeMillis()));

    long ttlSeconds = props.getPlayDailyAgg().getRedisTtlSeconds();
    redis.expire(key, Duration.ofSeconds(ttlSeconds));
    log.debug("redis agg base initialized: userId={}, scene={}, bizDate={}, baseTotal={}, baseLastSync={}",
        userId, scene, bizDate, baseTotal, baseLastSync);
  }

  private String keyFor(String userId, String scene, LocalDate bizDate) {
    return KEY_PREFIX + encodePart(scene) + ":" + bizDate + ":" + encodePart(userId);
  }

  private ParsedKey parseKey(String key) {
    if (!key.startsWith(KEY_PREFIX)) {
      return null;
    }
    String rest = key.substring(KEY_PREFIX.length());
    String[] parts = rest.split(":", 3);
    if (parts.length != 3) {
      return null;
    }
    ParsedKey p = new ParsedKey();
    p.scene = decodePart(parts[0]);
    if (p.scene == null) {
      return null;
    }
    try {
      p.bizDate = LocalDate.parse(parts[1]);
    } catch (Exception ex) {
      return null;
    }
    p.userId = decodePart(parts[2]);
    if (p.userId == null) {
      return null;
    }
    return p;
  }

  private String encodePart(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private String decodePart(String encoded) {
    if (encoded == null || encoded.isEmpty()) {
      return "";
    }
    try {
      byte[] data = Base64.getUrlDecoder().decode(encoded);
      return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private List<Long> toLongList(Object obj) {
    if (obj == null) {
      return List.of();
    }
    List<Object> raw = (List<Object>) obj;
    List<Long> out = new ArrayList<>(raw.size());
    for (Object v : raw) {
      if (v == null) {
        out.add(0L);
      } else if (v instanceof Number n) {
        out.add(n.longValue());
      } else {
        try {
          out.add(Long.parseLong(v.toString()));
        } catch (Exception ignore) {
          out.add(0L);
        }
      }
    }
    return out;
  }

  private Integer toInt(Object v, int def) {
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(v.toString());
    } catch (Exception ignore) {
      return def;
    }
  }

  private static class ParsedKey {
    private String userId;
    private String scene;
    private LocalDate bizDate;
  }

  public static class AggOutcome {
    public int totalDuration;
    public int deltaDuration;
  }

  private static final String RECORD_SCRIPT = """
    local baseTotal = tonumber(redis.call("HGET", KEYS[1], "base_total") or "0")
    local baseLast = tonumber(redis.call("HGET", KEYS[1], "base_last_sync") or "0")
    local pendingDelta = tonumber(redis.call("HGET", KEYS[1], "pending_delta") or "0")
    local pendingMax = tonumber(redis.call("HGET", KEYS[1], "pending_max_sync") or "0")
    local duration = tonumber(ARGV[1]) or 0
    local syncTime = tonumber(ARGV[2]) or 0
    local nowMs = tonumber(ARGV[3]) or 0
    local ttlMs = tonumber(ARGV[4]) or 0

    local added = 0
    if syncTime > baseLast then
      pendingDelta = pendingDelta + duration
      if syncTime > pendingMax then pendingMax = syncTime end
      redis.call("HSET", KEYS[1],
        "pending_delta", pendingDelta,
        "pending_max_sync", pendingMax,
        "updated_at", nowMs)
      added = duration
    else
      redis.call("HSET", KEYS[1], "updated_at", nowMs)
    end
    redis.call("ZADD", KEYS[2], nowMs, KEYS[1])
    if ttlMs > 0 then redis.call("PEXPIRE", KEYS[1], ttlMs) end
    return {baseTotal + pendingDelta, added, pendingDelta, pendingMax, baseLast}
    """;

  private static final String RESERVE_SCRIPT = """
    local nowMs = tonumber(ARGV[1]) or 0
    local timeout = tonumber(ARGV[2]) or 0
    local ttlMs = tonumber(ARGV[3]) or 0

    local pendingDelta = tonumber(redis.call("HGET", KEYS[1], "pending_delta") or "0")
    local pendingMax = tonumber(redis.call("HGET", KEYS[1], "pending_max_sync") or "0")
    local inflightDelta = tonumber(redis.call("HGET", KEYS[1], "inflight_delta") or "0")
    local inflightMax = tonumber(redis.call("HGET", KEYS[1], "inflight_max_sync") or "0")
    local inflightAt = tonumber(redis.call("HGET", KEYS[1], "inflight_at") or "0")

    if inflightDelta > 0 and timeout > 0 and (nowMs - inflightAt) >= timeout then
      pendingDelta = pendingDelta + inflightDelta
      if inflightMax > pendingMax then pendingMax = inflightMax end
      inflightDelta = 0
      inflightMax = 0
      inflightAt = 0
    end

    if inflightDelta > 0 then
      redis.call("HSET", KEYS[1],
        "pending_delta", pendingDelta,
        "pending_max_sync", pendingMax,
        "inflight_delta", inflightDelta,
        "inflight_max_sync", inflightMax,
        "inflight_at", inflightAt,
        "updated_at", nowMs)
      return {0, 0}
    end

    if pendingDelta <= 0 then
      redis.call("HSET", KEYS[1],
        "pending_delta", pendingDelta,
        "pending_max_sync", pendingMax,
        "updated_at", nowMs)
      redis.call("ZREM", KEYS[2], KEYS[1])
      return {0, 0}
    end

    inflightDelta = pendingDelta
    inflightMax = pendingMax
    pendingDelta = 0
    pendingMax = 0
    inflightAt = nowMs

    redis.call("HSET", KEYS[1],
      "pending_delta", pendingDelta,
      "pending_max_sync", pendingMax,
      "inflight_delta", inflightDelta,
      "inflight_max_sync", inflightMax,
      "inflight_at", inflightAt,
      "updated_at", nowMs)
    redis.call("ZADD", KEYS[2], nowMs, KEYS[1])
    if ttlMs > 0 then redis.call("PEXPIRE", KEYS[1], ttlMs) end
    return {inflightDelta, inflightMax}
    """;

  private static final String COMMIT_SCRIPT = """
    redis.call("HSET", KEYS[1],
      "base_total", ARGV[1],
      "base_last_sync", ARGV[2],
      "inflight_delta", 0,
      "inflight_max_sync", 0,
      "inflight_at", 0,
      "updated_at", ARGV[3])
    if tonumber(ARGV[4]) > 0 then redis.call("PEXPIRE", KEYS[1], ARGV[4]) end
    local pendingDelta = tonumber(redis.call("HGET", KEYS[1], "pending_delta") or "0")
    if pendingDelta <= 0 then
      redis.call("ZREM", KEYS[2], KEYS[1])
    end
    return 1
    """;

  private static final String ROLLBACK_SCRIPT = """
    local pendingDelta = tonumber(redis.call("HGET", KEYS[1], "pending_delta") or "0")
    local pendingMax = tonumber(redis.call("HGET", KEYS[1], "pending_max_sync") or "0")
    local inflightDelta = tonumber(redis.call("HGET", KEYS[1], "inflight_delta") or "0")
    local inflightMax = tonumber(redis.call("HGET", KEYS[1], "inflight_max_sync") or "0")
    pendingDelta = pendingDelta + inflightDelta
    if inflightMax > pendingMax then pendingMax = inflightMax end
    redis.call("HSET", KEYS[1],
      "pending_delta", pendingDelta,
      "pending_max_sync", pendingMax,
      "inflight_delta", 0,
      "inflight_max_sync", 0,
      "inflight_at", 0,
      "updated_at", ARGV[1])
    redis.call("ZADD", KEYS[2], ARGV[1], KEYS[1])
    if tonumber(ARGV[2]) > 0 then redis.call("PEXPIRE", KEYS[1], ARGV[2]) end
    return pendingDelta
    """;
}
