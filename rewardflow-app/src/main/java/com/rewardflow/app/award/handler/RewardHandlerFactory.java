package com.rewardflow.app.award.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 根据 prizeCode 选择合适的发奖处理器（handlre）的工厂
 */
@Component
public class RewardHandlerFactory {

  // prizeCode -> handler实现 “COIN” -> CoinRewardHandler
  private final Map<String, RewardHandler> handlerMap = new HashMap<>();

  public RewardHandlerFactory(List<RewardHandler> handlers) {
    if (handlers == null) {
      return;
    }

    for (RewardHandler h : handlers) {
      if (h == null) {
        continue;
      }
      
      String code = h.prizeCode();
      if (code == null || code.isBlank()) {
        throw new IllegalStateException("RewardHandler prizeCode is blank: " + h.getClass().getName());
      }
      code = code.trim().toUpperCase();

      RewardHandler existing = handlerMap.putIfAbsent(code, h);
      if (existing != null) {
        // 发现重复 prizeCode ：启动直接失败，避免运行时路由到错误实现
        throw new IllegalStateException(
            "Duplicate RewardHandler for prizeCode="
                + code
                + ", existed = "
                + existing.getClass().getName()
                + ", new = "
                + h.getClass().getName());
      }
    }
  }

  public RewardHandler get(String prizeCode) {
    if (prizeCode == null || prizeCode.isBlank()) {
      throw new IllegalArgumentException("prizeCode is blank");
    }
    String key = prizeCode.trim().toUpperCase();
    RewardHandler h = handlerMap.get(key);
    if (h == null) {
      throw new IllegalArgumentException("No handler for prizeCode=" + prizeCode);
    }
    return h;
  }
}
