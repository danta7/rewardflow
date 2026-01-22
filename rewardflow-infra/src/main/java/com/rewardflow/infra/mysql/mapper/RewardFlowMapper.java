package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.RewardFlowDO;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RewardFlowMapper {

  /** 查询发奖阶段 通过 user+scene+date+prizeCode. */
  List<Integer> selectAwardedStages(@Param("userId") String userId,
                                   @Param("bizScene") String bizScene,
                                   @Param("prizeDate") LocalDate prizeDate,
                                   @Param("prizeCode") String prizeCode);

  /** 通过 user+scene+date 查询已发奖的记录 包含 prizeCode 和 stage */
  List<RewardFlowDO> selectAwardedFlows(@Param("userId") String userId,
                                       @Param("bizScene") String bizScene,
                                       @Param("prizeDate") LocalDate prizeDate);

  int insert(RewardFlowDO record);
  /** 查询指定场景和日期的 outBizNo 列表，限制返回数量 */
  List<String> listOutBizNoBySceneAndDate(@Param("scene") String scene,
                                         @Param("bizDate") String bizDate,
                                         @Param("limit") int limit);

  RewardFlowDO selectByOutBizNo(@Param("outBizNo") String outBizNo);
}
