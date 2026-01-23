package com.rewardflow.app.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 指标接线
 *
 * <p>我们保持轻量级，业务指标由 service 发出（参见 RewardFlowMetrics），在这里
 * 我们添加通用标签+一些JMV/系统
 */
@Configuration
public class MetricsConfig {

  @Bean
  MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(
      @Value("${spring.application.name:rewardflow}") String app,
      @Value("${spring.profiles.active:default}") String profile) {
    return registry -> {
      registry.config().commonTags("app", app, "profile", profile);

      // 防止意外创建高基数计量器/标签，可据需要进行调整。
      registry.config().meterFilter(MeterFilter.denyNameStartsWith("http.client.requests"));
    };
  }

  @Bean
  public JvmMemoryMetrics jvmMemoryMetrics() {
    return new JvmMemoryMetrics();
  }

  @Bean
  public JvmGcMetrics jvmGcMetrics() {
    return new JvmGcMetrics();
  }

  @Bean
  public JvmThreadMetrics jvmThreadMetrics() {
    return new JvmThreadMetrics();
  }

  @Bean
  public ProcessorMetrics processorMetrics() {
    return new ProcessorMetrics();
  }

  @Bean
  public UptimeMetrics uptimeMetrics() {
    return new UptimeMetrics();
  }

  @Bean
  public FileDescriptorMetrics fileDescriptorMetrics() {
    return new FileDescriptorMetrics();
  }
}
