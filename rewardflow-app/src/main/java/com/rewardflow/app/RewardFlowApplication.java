package com.rewardflow.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.rewardflow")
@ConfigurationPropertiesScan(basePackages = "com.rewardflow")
@EnableScheduling
public class RewardFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(RewardFlowApplication.class, args);
  }
}
