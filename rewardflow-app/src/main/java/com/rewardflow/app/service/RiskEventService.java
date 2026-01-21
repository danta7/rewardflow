package com.rewardflow.app.service;

import com.rewardflow.infra.mongo.entity.RiskEvent;
import com.rewardflow.infra.mongo.repo.RiskEventRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Best-effort 风控相关日志写入 mongodb */
@Service
public class RiskEventService {

  private static final Logger log = LoggerFactory.getLogger(RiskEventService.class);

  private final RiskEventRepository repo;

  public RiskEventService(RiskEventRepository repo) {
    this.repo = repo;
  }

  public void log(String userId, String scene, String traceId, String type, Map<String, Object> detail) {
    try {
      RiskEvent e = new RiskEvent();
      e.setUserId(userId);
      e.setScene(scene);
      e.setTraceId(traceId);
      e.setType(type);
      e.setDetail(detail);
      repo.save(e);
    } catch (Exception ex) {
      // Never fail business by audit
      log.debug("risk event save failed", ex);
    }
  }
}
