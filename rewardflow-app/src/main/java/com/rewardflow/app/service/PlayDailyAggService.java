package com.rewardflow.app.service;

import com.rewardflow.infra.mysql.entity.PlayReportAggResult;
import com.rewardflow.infra.mysql.entity.UserPlayDailyDO;
import com.rewardflow.infra.mysql.mapper.PlayDurationReportMapper;
import com.rewardflow.infra.mysql.mapper.UserPlayDailyMapper;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PlayDailyAggService {

  private final UserPlayDailyMapper dailyMapper;
  private final PlayDurationReportMapper reportMapper;

  public PlayDailyAggService(UserPlayDailyMapper dailyMapper,
                             PlayDurationReportMapper reportMapper) {
    this.dailyMapper = dailyMapper;
    this.reportMapper = reportMapper;
  }

  /**
   * 使用 Mysql 增量聚合来计算每天的总时长（只聚合上次没统计过的新明细）
   *   delta = SUM(duration) WHERE sync_time > last_sync_time
   * 结果写回 user_play_daily 表
   *
   * 要在外层事务中调用该方法，“加锁+查+写”需要跟“插入明细表”放在同一个事物里面保证一致性
   */
  public AggOutcome aggregate(String userId, String scene, LocalDate bizDate, long currentSyncTime) {
    // 锁住当天汇总行：让同一个（userId，scene，bizDate）的聚合过程串行化避免并发把总数算乱
    UserPlayDailyDO daily = dailyMapper.selectOneForUpdate(userId, scene, bizDate);
    if (daily == null) {
      // 如果当天汇总行不存在，就先创建一行（并发安全的初始化）
      UserPlayDailyDO init = new UserPlayDailyDO();
      init.setUserId(userId);
      init.setBizScene(scene);
      init.setBizDate(bizDate);
      init.setTotalDuration(0);
      init.setLastSyncTime(0L);
      init.setVersion(0);
      try {
        dailyMapper.insert(init);
      } catch (DuplicateKeyException ignore) {
        // 另一个线程已经先创建了这行
      }
      daily = dailyMapper.selectOneForUpdate(userId, scene, bizDate);
    }

 
    long lastSync = daily.getLastSyncTime() == null ? 0L : daily.getLastSyncTime();
    // 明细表中做“增量聚合查询”
    PlayReportAggResult agg = reportMapper.selectAggSince(userId, scene, bizDate, lastSync);
    // 本次新增的时长
    int delta = agg != null && agg.getDeltaDuration() != null ? agg.getDeltaDuration() : 0;

    long maxSync = lastSync;
    if (agg != null && agg.getMaxSyncTime() != null) {
      maxSync = Math.max(lastSync, agg.getMaxSyncTime());
    } else {
      // 兜底：agg 为空就用当前的 sync_time 作为边界推进
      maxSync = Math.max(lastSync, currentSyncTime);
    }

    int newTotal = (daily.getTotalDuration() == null ? 0 : daily.getTotalDuration()) + delta;

    // 只有当 sync_time 边界推进或总时长变化时才更新（避免无意义更新）
    // 注意：如果上报乱序（sync_time <= lastSync），那么 delta 会是 0
    dailyMapper.updateTotals(daily.getId(), newTotal, maxSync, daily.getVersion() == null ? 0 : daily.getVersion());

    AggOutcome out = new AggOutcome();
    out.dailyId = daily.getId();
    out.deltaDuration = delta;
    out.totalDuration = newTotal;
    out.lastSyncTime = maxSync;
    return out;
  }

  public static class AggOutcome {
    public Long dailyId;
    public Integer deltaDuration;
    public Integer totalDuration;
    public Long lastSyncTime;
  }
}
