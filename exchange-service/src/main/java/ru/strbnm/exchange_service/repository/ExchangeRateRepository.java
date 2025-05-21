package ru.strbnm.exchange_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.entity.ExchangeRate;

@Repository
public interface ExchangeRateRepository extends ReactiveCrudRepository<ExchangeRate, Long> {
    Mono<ExchangeRate> findTopByCurrencyCodeOrderByTimestampDesc(String currencyCode);
}