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
    if (handlers != null) {
      for (RewardHandler h : handlers) {
        String code = h.prizeCode();
        if (code == null || code.isBlank()) {
          throw new IllegalArgumentException("Handler prizeCode() is null/empty: " + h.getClass().getName());
        }

        RewardHandler existing = handlerMap.putIfAbsent(code, h);
        if (existing != null) {
          // 发现重复 prizeCode ：启动直接失败，避免运行时路由到错误实现
          throw new IllegalStateException(
            "Duplicate RewardHandler for prizeCode=" + code +
            ", existed = " + existing.getClass().getName()
            + ", new = " + h.getClass().getName()
          );
        }
        handlerMap.put(h.prizeCode(), h);
      }
    }
  }

  public RewardHandler get(String prizeCode) {
    RewardHandler h = handlerMap.get(prizeCode);
    if (h == null) {
      throw new IllegalArgumentException("No handler for prizeCode=" + prizeCode);
    }
    return h;
  }
}
