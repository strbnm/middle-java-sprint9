package ru.strbnm.exchange_service.service;


import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.entity.ExchangeRate;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;
import ru.strbnm.kafka.dto.ExchangeRateMessage;

@Profile("!contracts & !test")
@Slf4j
@Service
public class KafkaConsumerService{

    private final ExchangeRateRepository exchangeRateRepository;
    private final TransactionalOperator transactionalOperator;
    private final AtomicReference<Instant> lastRateUpdate = new AtomicReference<>(Instant.EPOCH);
    private final MeterRegistry meterRegistry;

    public KafkaConsumerService(ExchangeRateRepository exchangeRateRepository,
                                TransactionalOperator transactionalOperator, MeterRegistry meterRegistry) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.transactionalOperator = transactionalOperator;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "exchange-rates", concurrency = "1", idIsGroup = false)
    public Mono<Void> listen(ExchangeRateMessage message) {
        return transactionalOperator.execute(status -> updateRates(message))
                .doOnError(e -> log.error("Ошибка при обновлении курсов валют", e))
                .then();
    }

    private Mono<Void> updateRates(ExchangeRateMessage rateMessage) {
        return exchangeRateRepository.deleteAll()
                .thenMany(
                        Flux.fromIterable(rateMessage.getRates())
                                .map(rate -> ExchangeRate.builder()
                                        .title(rate.getTitle())
                                        .currencyCode(rate.getName())
                                        .rateToRub(rate.getValue().setScale(4, RoundingMode.HALF_UP))
                                        .createdAt(rateMessage.getTimestamp())
                                        .build()
                                )
                )
                .as(exchangeRateRepository::saveAll)
                .then()
                .doOnSuccess(v -> lastRateUpdate.set(Instant.now()));
    }

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("currency.rate.delay.seconds", () ->
                        Duration.between(lastRateUpdate.get(), Instant.now()).getSeconds())
                .description("Время в секундах с момента последнего обновления курса валют")
                .register(meterRegistry);
    }
}
