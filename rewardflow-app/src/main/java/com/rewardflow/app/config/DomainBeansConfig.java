package com.rewardflow.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rewardflow.domain.rule.AwardCalculator;

@Configuration
public class DomainBeansConfig {
    
    @Bean
    public AwardCalculator awardCalculator() {
        return new AwardCalculator();
    }
}
