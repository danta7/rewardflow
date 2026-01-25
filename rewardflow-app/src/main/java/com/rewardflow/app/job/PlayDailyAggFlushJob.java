package com.rewardflow.app.job;

import com.rewardflow.app.service.PlayDailyRedisAggService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PlayDailyAggFlushJob {

  private static final Logger log = LoggerFactory.getLogger(PlayDailyAggFlushJob.class);

  private final PlayDailyRedisAggService aggService;

  public PlayDailyAggFlushJob(PlayDailyRedisAggService aggService) {
    this.aggService = aggService;
  }

  @Scheduled(
      fixedDelayString = "${rewardflow.play-daily-agg.flush-interval-ms:5000}",
      initialDelayString = "${rewardflow.play-daily-agg.flush-initial-delay-ms:2000}"
  )
  public void flush() {
    if (!aggService.enabled()) {
      return;
    }
    try {
      int processed = aggService.flushOnce();
      if (processed > 0) {
        log.info("play daily agg flush job done: processed={}", processed);
      }
    } catch (Exception ex) {
      log.warn("play daily agg flush job failed: err={}", ex.toString());
    }
  }
}
