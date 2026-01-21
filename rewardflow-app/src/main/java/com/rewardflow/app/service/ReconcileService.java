package com.rewardflow.app.service;

import com.rewardflow.infra.mysql.mapper.RewardFlowMapper;
import com.rewardflow.infra.mysql.mapper.RewardOutboxMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 对账，reward_flow 表和 reward_outbox 表之间的数据一致性检查
 *
 * <p>内部一致性核查
 * 现在只是在 outbox 里保证每个 outBizNo 有一条 AWARD_CREATED 记录
 */
@Service
public class ReconcileService {

  private final RewardFlowMapper rewardFlowMapper;
  private final RewardOutboxMapper rewardOutboxMapper;
  private final FeatureCenterService featureCenterService;

  public ReconcileService(RewardFlowMapper rewardFlowMapper, RewardOutboxMapper rewardOutboxMapper, FeatureCenterService featureCenterService) {
    this.rewardFlowMapper = rewardFlowMapper;
    this.rewardOutboxMapper = rewardOutboxMapper;
    this.featureCenterService = featureCenterService;
  }

  public Map<String, Object> reconcileFlowOutbox(String scene, String bizDate, int limit) {
    Boolean enabled = featureCenterService.currentConfig().getReconcileEnabled();
    if (enabled != null && !enabled) {
      return Map.of("enabled", false, "message", "reconcile disabled");
    }

    // 查两张表各自的 out_biz_no 列表
    List<String> flowOutBizNos = rewardFlowMapper.listOutBizNoBySceneAndDate(scene, bizDate, limit);
    List<String> outboxOutBizNos = rewardOutboxMapper.listOutBizNoLike("%|" + scene + "|" + bizDate + "|%", limit);

    Set<String> flowSet = new HashSet<>(flowOutBizNos == null ? List.of() : flowOutBizNos);
    Set<String> outboxSet = new HashSet<>(outboxOutBizNos == null ? List.of() : outboxOutBizNos);

    List<String> missingOutbox = new ArrayList<>();
    for (String ob : flowSet) {
      if (!outboxSet.contains(ob)) missingOutbox.add(ob);
    }

    List<String> orphanOutbox = new ArrayList<>();
    for (String ob : outboxSet) {
      if (!flowSet.contains(ob)) orphanOutbox.add(ob);
    }

    return Map.of(
        "enabled", true,
        "scene", scene,
        "bizDate", bizDate,
        "flowCount", flowSet.size(),
        "outboxCount", outboxSet.size(),
        "missingOutboxCount", missingOutbox.size(),
        "orphanOutboxCount", orphanOutbox.size(),
        "missingOutboxSamples", missingOutbox.stream().limit(20).toList(),
        "orphanOutboxSamples", orphanOutbox.stream().limit(20).toList()
    );
  }
}
