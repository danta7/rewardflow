package com.rewardflow.app.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把 Outbox 里的事件发布到 RabbitMQ */
@Configuration
public class RabbitTopologyConfig {

  @Bean
  public Declarables rewardflowAwardDeclarables(RewardMqProperties mq) {
    String mainExchangeName = mq.getExchange();
    String mainRoutingKey = mq.getRoutingKey();
    String mainQueueName = mq.getQueue();

    String retryExchangeName = mainExchangeName + ".retry";
    String retryRoutingKey = mainRoutingKey + ".retry";
    String retryQueueName = mainQueueName + ".retry";

    String deadExchangeName = mainExchangeName + ".dead";
    String deadRoutingKey = mainRoutingKey + ".dead";
    String deadQueueName = mainQueueName + ".dead";

    DirectExchange mainExchange = new DirectExchange(mainExchangeName, true, false);
    DirectExchange retryExchange = new DirectExchange(retryExchangeName, true, false);
    DirectExchange deadExchange = new DirectExchange(deadExchangeName, true, false);

    Queue mainQueue = QueueBuilder.durable(mainQueueName)
        .withArgument("x-dead-letter-exchange", deadExchangeName)
        .withArgument("x-dead-letter-routing-key", deadRoutingKey)
        .build();

    Queue retryQueue = QueueBuilder.durable(retryQueueName)
        .withArgument("x-dead-letter-exchange", mainExchangeName)
        .withArgument("x-dead-letter-routing-key", mainRoutingKey)
        .build();

    Queue deadQueue = QueueBuilder.durable(deadQueueName).build();

    Binding mainBinding = BindingBuilder.bind(mainQueue).to(mainExchange).with(mainRoutingKey);
    Binding retryBinding = BindingBuilder.bind(retryQueue).to(retryExchange).with(retryRoutingKey);
    Binding deadBinding = BindingBuilder.bind(deadQueue).to(deadExchange).with(deadRoutingKey);

    return new Declarables(
        mainExchange,
        retryExchange,
        deadExchange,
        mainQueue,
        retryQueue,
        deadQueue,
        mainBinding,
        retryBinding,
        deadBinding
    );
  }
}
