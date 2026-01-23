package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RewardOutboxMapper {

  int insert(RewardOutboxDO record);

  // 查询 outBizNo 模糊匹配的记录，限制返回数量
  List<String> listOutBizNoLike(@Param("like") String likePattern,
                               @Param("limit") int limit);

  RewardOutboxDO selectByOutBizNoAndEventType(@Param("outBizNo") String outBizNo,
                                             @Param("eventType") String eventType);

  // 捞待发送事件（status = 0 and next_retry_time <= now）
  List<RewardOutboxDO> selectPending(@Param("now") LocalDateTime now,
                                     @Param("limit") int limit);

  // 统计待发送事件数量
  long countPending(@Param("now") LocalDateTime now);

  // 统计失败事件数量（status = 2）
  long countFailed();

  // 标记已发送（仅当 status=0 才会更新）
  int markSent(@Param("eventId") String eventId);

  /**
   * 更新重试信息（仅当 status=0 且 retry_count==expectedRetryCount 才会更新）
   * 防止并发下把 status=1 的记录改回 0/2，或者不同 worker 覆盖 retry_count。
   */
  int updateRetry(@Param("eventId") String eventId,
                  @Param("expectedRetryCount") int expectedRetryCount,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("status") int status);
}
