package com.rewardflow.app.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PingController {

  @GetMapping("/ping")
  public Map<String, Object> ping() {
    return Map.of(
        "ok", true,
        "service", "rewardflow",
        "ts", Instant.now().toString()
    );
  }
}
