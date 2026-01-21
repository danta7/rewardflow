package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.RewardOutboxDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RewardOutboxMapper {

  int insert(RewardOutboxDO record);

  RewardOutboxDO selectByOutBizNoAndEventType(@Param("outBizNo") String outBizNo,
                                             @Param("eventType") String eventType);
  // 捞待发送事件（status = 0）
  List<RewardOutboxDO> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

  // 标记已发送
  int markSent(@Param("eventId") String eventId);

  // 更新重试信息
  int updateRetry(@Param("eventId") String eventId,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("status") int status);
}
