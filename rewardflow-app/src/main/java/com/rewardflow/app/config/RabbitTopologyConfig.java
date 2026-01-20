package com.rewardflow.app.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把 Outbox 里的事件发布到 RabbitMQ */
@Configuration
public class RabbitTopologyConfig {

  @Bean
  public Declarables rewardflowAwardDeclarables(RewardMqProperties mq) {
    DirectExchange exchange = new DirectExchange(mq.getExchange(), true, false);
    Queue queue = new Queue(mq.getQueue(), true);
    Binding binding = BindingBuilder.bind(queue).to(exchange).with(mq.getRoutingKey());
    return new Declarables(exchange, queue, binding);
  }
}
