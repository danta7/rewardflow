package com.rewardflow.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.rewardflow")
public class RewardFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(RewardFlowApplication.class, args);
  }
}
