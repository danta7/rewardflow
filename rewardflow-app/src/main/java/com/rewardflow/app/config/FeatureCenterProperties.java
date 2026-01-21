package com.rewardflow.app.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Feature-center (Nacos) 配置中心，运行时动态开关功能
 */
@Validated
@ConfigurationProperties(prefix = "rewardflow.feature-center")
public class FeatureCenterProperties {

  /** 启用从 Nacos 获取 feature JSON。如果禁用，则使用本地备用资源 */
  private boolean enabled = true;

  /** Nacos server addr, e.g. localhost:8848 */
  @NotBlank
  private String serverAddr = "localhost:8848";

  @NotBlank
  private String dataId = "rewardflow-feature-switches.json";

  /** Nacos config group */
  @NotBlank
  private String group = "DEFAULT_GROUP";

  @NotNull
  private Long timeoutMs = 3000L;

  /** Optional basic auth */
  private String username;
  private String password;

  /** nacos 不可用时 使用本地 fallback */
  @NotBlank
  private String fallbackResource = "classpath:features/default-feature-config.json";

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

  public String getFallbackResource() {
    return fallbackResource;
  }

  public void setFallbackResource(String fallbackResource) {
    this.fallbackResource = fallbackResource;
  }
}
