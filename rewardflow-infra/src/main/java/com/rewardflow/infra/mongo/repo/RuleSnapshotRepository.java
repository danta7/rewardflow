package com.rewardflow.infra.mongo.repo;

import com.rewardflow.infra.mongo.entity.RuleSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RuleSnapshotRepository extends MongoRepository<RuleSnapshot, String> {
  Optional<RuleSnapshot> findByContentHash(String contentHash);
  List<RuleSnapshot> findTop50ByOrderByCreatedAtDesc();
}
