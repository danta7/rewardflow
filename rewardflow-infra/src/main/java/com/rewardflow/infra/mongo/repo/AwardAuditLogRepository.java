package com.rewardflow.infra.mongo.repo;

import com.rewardflow.infra.mongo.entity.AwardAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AwardAuditLogRepository extends MongoRepository<AwardAuditLog, String> {}
