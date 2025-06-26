package ru.strbnm.exchange_service.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import ru.strbnm.exchange_service.entity.ExchangeRate;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;
import ru.strbnm.kafka.dto.ExchangeRateMessage;
import ru.strbnm.kafka.dto.Rate;

@Slf4j
@ActiveProfiles("kafka_test")
@SpringBootTest(properties = {"spring.config.name=application-test"})
@EmbeddedKafka(topics = "exchange-rates")
public class KafkaConsumerServiceTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private KafkaTemplate<String, ExchangeRateMessage> kafkaTemplate;

    @Test
    void testProcessNotificationMessages() throws ExecutionException, InterruptedException {

    ExchangeRateMessage exchangeRateMessage1 =
        ExchangeRateMessage.builder()
                .timestamp(Instant.now().getEpochSecond())
                .rates(List.of(
                        new Rate("Рубль", "RUB", BigDecimal.ONE),
                        new Rate("Доллар", "USD", new BigDecimal("0.012")),
                        new Rate("Юань", "CNY", new BigDecimal("0.13"))
                ))
                .build();

    List<Rate> lastRates = List.of(
            new Rate("Рубль", "RUB", BigDecimal.ONE),
            new Rate("Доллар", "USD", new BigDecimal("0.010")),
            new Rate("Юань", "CNY", new BigDecimal("0.15"))
    );
    ExchangeRateMessage exchangeRateMessage2 =
            ExchangeRateMessage.builder()
                    .timestamp(Instant.now().getEpochSecond())
                    .rates(lastRates)
                    .build();

        log.info("Отправка сообщений в топик");
        var result1 = kafkaTemplate.send("exchange-rates", "actual-rates", exchangeRateMessage1).get();
        log.info("Отправка сообщения в топик {} партицию {} смещение {}", result1.getRecordMetadata().topic(), result1.getRecordMetadata().partition(), result1.getRecordMetadata().offset());
        var result2 =kafkaTemplate.send("exchange-rates", "actual-rates", exchangeRateMessage2).get();
        log.info("Отправка сообщения в топик {} партицию {} смещение {}", result2.getRecordMetadata().topic(), result2.getRecordMetadata().partition(), result2.getRecordMetadata().offset());
        // ждём, пока сообщения обработаются и попадут в БД
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<Rate> all = exchangeRateRepository.findAll().map(this::toRate).collectList().block();
                    assertThat(all).hasSize(3);
                    assertThat(all)
                            .usingRecursiveComparison()
                            .withComparatorForType((a, b) -> ((BigDecimal) a).compareTo((BigDecimal) b), BigDecimal.class)
                            .ignoringCollectionOrder()
                            .isEqualTo(lastRates);
                });
    }

    private Rate toRate(ExchangeRate entity) {
        return Rate.builder()
                .title(entity.getTitle())
                .name(entity.getCurrencyCode())
                .value(entity.getRateToRub())
                .build();
    }
}
