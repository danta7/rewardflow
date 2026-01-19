package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.PlayDurationReportDO;
import com.rewardflow.infra.mysql.entity.PlayReportAggResult;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlayDurationReportMapper {

  int insert(PlayDurationReportDO record);

  /**
   * 增量聚合：对 lastSyncTime 之后的记录做聚合
   * 计算 duration 的总和（sum（duration））以及最新的 sync_time（max(sync_time)）
   */
  PlayReportAggResult selectAggSince(@Param("userId") String userId,
                                    @Param("bizScene") String bizScene,
                                    @Param("bizDate") LocalDate bizDate,
                                    @Param("lastSyncTime") long lastSyncTime);
}
