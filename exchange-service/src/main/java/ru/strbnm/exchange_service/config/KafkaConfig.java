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

    @Bean
    public ConsumerAwareRebalanceListener lastMessageSeeker() {
        return new ConsumerAwareRebalanceListener() {
            @Override
            public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
                for (TopicPartition partition : partitions) {
                    consumer.seekToEnd(List.of(partition));
                    long position = consumer.position(partition);
                    long seekOffset = (position > 0) ? position - 1 : 0;
                    consumer.seek(partition, seekOffset);
                    log.info("Seek to last existing record at offset {} for {}", seekOffset, partition);
                }
            }
        };
    }


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExchangeRateMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, ExchangeRateMessage> consumerFactory,
            ConsumerAwareRebalanceListener rebalanceListener) {
        ConcurrentKafkaListenerContainerFactory<String, ExchangeRateMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener);
        return factory;
    }
}