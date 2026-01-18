package com.rewardflow.infra.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.rewardflow.infra.mysql.mapper")
public class MyBatisConfig {
}
