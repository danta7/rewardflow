package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.UserPlayDailyDO;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPlayDailyMapper {

  // 查询一条汇总记录
  UserPlayDailyDO selectOne(@Param("userId") String userId,
                            @Param("bizScene") String bizScene,
                            @Param("bizDate") LocalDate bizDate);

  // 查询一条汇总记录，并加锁(当要更新时长时，先把记录锁住，然后再更新避免并发问题)
  UserPlayDailyDO selectOneForUpdate(@Param("userId") String userId,
                                     @Param("bizScene") String bizScene,
                                     @Param("bizDate") LocalDate bizDate);

  // 插入一条汇总记录                                
  int insert(UserPlayDailyDO record);

  // 更新累计进度
  int updateTotals(@Param("id") Long id,
                   @Param("totalDuration") int totalDuration,
                   @Param("lastSyncTime") long lastSyncTime,
                   @Param("version") int version);
}
