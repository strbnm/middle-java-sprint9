package ru.strbnm.exchange_service.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import ru.strbnm.kafka.dto.ExchangeRateMessage;

import java.util.Collection;
import java.util.List;

@Slf4j
@Profile("!contracts & !test")
@EnableKafka
@Configuration
public class KafkaConfig {

}