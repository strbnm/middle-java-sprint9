package ru.strbnm.exchange_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.domain.ExchangeRateRequest;
import ru.strbnm.exchange_service.domain.Rate;
import ru.strbnm.exchange_service.entity.ExchangeRate;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;

@Transactional(isolation = Isolation.REPEATABLE_READ)
@Service
public class ExchangeServiceImpl implements ExchangeService {

  private final ExchangeRateRepository exchangeRateRepository;

  public ExchangeServiceImpl(ExchangeRateRepository exchangeRateRepository) {
    this.exchangeRateRepository = exchangeRateRepository;
  }

  @Override
  public Mono<BigDecimal> convert(String from, String to, BigDecimal amount) {
    if (from.equals(to)) return Mono.just(amount);

    return exchangeRateRepository
        .findByCurrencyCode(from)
        .zipWith(exchangeRateRepository.findByCurrencyCode(to))
        .map(
            tuple -> {
              BigDecimal fromRate = tuple.getT1().getRateToRub();
              BigDecimal toRate = tuple.getT2().getRateToRub();
              return amount.multiply(toRate).divide(fromRate, 8, RoundingMode.HALF_UP);
            });
  }

  @Override
  public Mono<Void> saveRates(ExchangeRateRequest rateRequest) {
    return exchangeRateRepository.deleteAll()
            .thenMany(Flux.fromIterable(rateRequest.getRates())
                    .map(rate -> ExchangeRate.builder()
                            .title(rate.getTitle())
                            .currencyCode(rate.getName())
                            .rateToRub(rate.getValue())
                            .createdAt(rateRequest.getTimestamp())
                            .build()))
            .transform(exchangeRateRepository::saveAll)
            .then();
  }

  @Override
  public Flux<Rate> getRates() {
    return exchangeRateRepository.findAll().map(this::toRate);
  }

  private Rate toRate(ExchangeRate entity) {
    return Rate.builder()
        .title(entity.getTitle())
        .name(entity.getCurrencyCode())
        .value(entity.getRateToRub())
        .build();
  }
}
