package ru.strbnm.exchange_service.service;

import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.domain.Rate;

public interface ExchangeService {
    Mono<BigDecimal> convert(String from, String to, BigDecimal amount);
    Flux<Rate> getRates();
}
