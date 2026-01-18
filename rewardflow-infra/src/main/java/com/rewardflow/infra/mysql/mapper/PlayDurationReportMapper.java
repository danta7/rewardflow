package com.rewardflow.infra.mysql.mapper;

import com.rewardflow.infra.mysql.entity.PlayDurationReportDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayDurationReportMapper {

  int insert(PlayDurationReportDO record);
}
