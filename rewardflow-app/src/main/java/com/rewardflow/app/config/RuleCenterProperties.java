package com.rewardflow.app.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 规则中心（Nacos）配置分阶段奖励规则
 */
@Validated
@ConfigurationProperties(prefix = "rewardflow.rule-center")
public class RuleCenterProperties {

  /** Enable fetching rule JSON from Nacos. If false, use local fallback resource. */
  private boolean enabled = true;

  /** Nacos server address, e.g. localhost:8848 */
  @NotBlank
  private String serverAddr = "localhost:8848";

  /** Optional Nacos username (for auth-enabled Nacos). */
  private String username;

  /** Optional Nacos password (for auth-enabled Nacos). */
  private String password;

  /** DataId that stores rule JSON. */
  @NotBlank
  private String dataId = "rewardflow-award-rules.json";

  /** Group of the config. */
  @NotBlank
  private String group = "DEFAULT_GROUP";

  /** Fetch timeout in ms. */
  @NotNull
  private Long timeoutMs = 3000L;

  /** Local fallback resource when Nacos is unavailable or empty. */
  @NotBlank
  private String fallbackResource = "classpath:rules/default-award-rules.json";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServerAddr() {
    return serverAddr;
  }

  public void setServerAddr(String serverAddr) {
    this.serverAddr = serverAddr;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDataId() {
    return dataId;
  }

  public void setDataId(String dataId) {
    this.dataId = dataId;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public Long getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(Long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public String getFallbackResource() {
    return fallbackResource;
  }

  public void setFallbackResource(String fallbackResource) {
    this.fallbackResource = fallbackResource;
  }
}
