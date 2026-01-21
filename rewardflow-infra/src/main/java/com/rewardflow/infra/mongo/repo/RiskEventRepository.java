package com.rewardflow.infra.mongo.repo;

import com.rewardflow.infra.mongo.entity.RiskEvent;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RiskEventRepository extends MongoRepository<RiskEvent, String> {
  // 查最新的 50 条记录
  List<RiskEvent> findTop50ByOrderByCreatedAtDesc();
}
