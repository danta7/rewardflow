package com.rewardflow.app.service;

import com.rewardflow.infra.mongo.entity.RuleSnapshot;
import com.rewardflow.infra.mongo.repo.RuleSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 将规则/配置json按照内容hash去重持久化到 mongo */
@Service
public class RuleSnapshotService {

  private static final Logger log = LoggerFactory.getLogger(RuleSnapshotService.class);

  private final RuleSnapshotRepository repo;

  public RuleSnapshotService(RuleSnapshotRepository repo) {
    this.repo = repo;
  }

  public void snapshotIfNew(String rawJson, String source) {
    if (rawJson == null || rawJson.isBlank()) return;
    String hash = sha256(rawJson);
    try {
      if (repo.findByContentHash(hash).isPresent()) {
        return;
      }
      RuleSnapshot s = new RuleSnapshot();
      s.setContentHash(hash);
      s.setSource(source);
      s.setRawJson(rawJson);
      s.setCreatedAt(Instant.now());
      repo.save(s);
    } catch (DuplicateKeyException dup) {
      // ignore
    } catch (Exception e) {
      log.debug("rule snapshot save failed", e);
    }
  }

  public List<RuleSnapshot> latest() {
    return repo.findTop50ByOrderByCreatedAtDesc();
  }

  private static String sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      return Integer.toHexString(s.hashCode());
    }
  }
}
