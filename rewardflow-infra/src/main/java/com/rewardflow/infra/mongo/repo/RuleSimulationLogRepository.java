package com.rewardflow.infra.mongo.repo;

import com.rewardflow.infra.mongo.entity.RuleSimulationLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RuleSimulationLogRepository extends MongoRepository<RuleSimulationLog, String> {
  List<RuleSimulationLog> findTop50ByOrderByCreatedAtDesc();
}
