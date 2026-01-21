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

  RewardFlowDO selectByOutBizNo(@Param("outBizNo") String outBizNo);
}
