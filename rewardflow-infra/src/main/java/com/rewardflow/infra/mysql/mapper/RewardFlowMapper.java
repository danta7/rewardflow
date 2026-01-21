package com.rewardflow.infra.mysql.mapper;

import java.time.LocalDate;
import java.util.List;
import com.rewardflow.infra.mysql.entity.RewardFlowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RewardFlowMapper {

  /** 查询发奖阶段 通过 user+scene+date+prizeCode. */
  List<Integer> selectAwardedStages(@Param("userId") String userId,
                                   @Param("bizScene") String bizScene,
                                   @Param("prizeDate") LocalDate prizeDate,
                                   @Param("prizeCode") String prizeCode);
  /** 插入发奖记录 */
  int insert(RewardFlowDO record);

  /** 查询指定场景和日期的外部幂等号列表，限制返回数量 */
  java.util.List<String> listOutBizNoBySceneAndDate(@org.apache.ibatis.annotations.Param("scene") String scene,
                                                 @org.apache.ibatis.annotations.Param("bizDate") String bizDate,
                                                 @org.apache.ibatis.annotations.Param("limit") int limit);


  RewardFlowDO selectByOutBizNo(@Param("outBizNo") String outBizNo);
}
