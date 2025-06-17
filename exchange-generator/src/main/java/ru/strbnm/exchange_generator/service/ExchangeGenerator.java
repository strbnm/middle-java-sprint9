package ru.strbnm.exchange_generator.service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import ru.strbnm.kafka.dto.ExchangeRateMessage;
import ru.strbnm.kafka.dto.Rate;

@Slf4j
@Service
public class ExchangeGenerator {

  private final KafkaTemplate<String, ExchangeRateMessage> kafkaTemplate; // exchange service url
  private final Random random = new Random();
  private final MathContext mathContext = new MathContext(6, RoundingMode.HALF_UP);

  @Autowired
  public ExchangeGenerator(KafkaTemplate<String, ExchangeRateMessage> kafkaTemplate) {
      this.kafkaTemplate = kafkaTemplate;
  }

  public void generateAndSendRates() {
    long timestamp = Instant.now().getEpochSecond();
    UUID uuid = UUID.randomUUID();
    List<Rate> rates =
            List.of(
                    Rate.builder().title("Рубль").name("RUB").value(BigDecimal.ONE).build(),
                    Rate.builder().title("Доллар").name("USD").value(round(randomInRange(0.01, 0.02))).build(),
                    Rate.builder().title("Юань").name("CNY").value(round(randomInRange(0.1, 0.2))).build());

    ExchangeRateMessage message = ExchangeRateMessage.builder().timestamp(timestamp).rates(rates).build();
    kafkaTemplate.send("exchange-rates", "actual_rates", message).whenComplete((result, e) -> {
          if (e != null) {
              log.error("Ошибка при отправке сообщения: {}", e.getMessage(), e);
              return;
          }

          RecordMetadata metadata = result.getRecordMetadata();
          log.info("Сообщение отправлено. Topic = {}, partition = {}, offset = {}",
                  metadata.topic(), metadata.partition(), metadata.offset());
      });  ;
  }


  @Scheduled(fixedDelay = 1000)
  public void scheduleRateGeneration() {
      generateAndSendRates();
  }

  private BigDecimal round(double value) {
    return new BigDecimal(value, mathContext).setScale(4, RoundingMode.HALF_UP);
  }

  private double randomInRange(double min, double max) {
    return min + (max - min) * random.nextDouble();
  }
}
