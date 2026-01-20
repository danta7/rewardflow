package com.rewardflow.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.rewardflow.infra.mongo.repo")
public class MongoConfig {}
