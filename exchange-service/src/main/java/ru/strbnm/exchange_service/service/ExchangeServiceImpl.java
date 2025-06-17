package ru.strbnm.exchange_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.domain.Rate;
import ru.strbnm.exchange_service.entity.ExchangeRate;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;

@Transactional(isolation = Isolation.REPEATABLE_READ)
@Service
public class ExchangeServiceImpl implements ExchangeService {

  private final ExchangeRateRepository exchangeRateRepository;
  private final String BASE_CURRENCY = "RUB";

  public ExchangeServiceImpl(ExchangeRateRepository exchangeRateRepository) {
    this.exchangeRateRepository = exchangeRateRepository;
  }

  @Override
  public Mono<BigDecimal> convert(String from, String to, BigDecimal amount) {
    if (from.equals(to)) return Mono.just(amount);
    if (BASE_CURRENCY.equals(from)) {
      return fromBaseCurrency(to, amount);
    } else if (BASE_CURRENCY.equals(to)) {
      return toBaseCurrency(from, amount);
    } else {
      return fromIntermediateCurrency(from, to, amount);
    }
  }

  private Mono<BigDecimal> fromBaseCurrency(String to, BigDecimal amount) {
    return exchangeRateRepository
        .findByCurrencyCode(to)
        .map(toRate -> round(amount.multiply(toRate.getRateToRub())));
  }

  private Mono<BigDecimal> toBaseCurrency(String from, BigDecimal amount) {
    return exchangeRateRepository
            .findByCurrencyCode(from)
            .map(fromRate -> round(amount.divide(fromRate.getRateToRub(), 8, RoundingMode.HALF_UP)));
  }

  private Mono<BigDecimal> fromIntermediateCurrency(String from, String to, BigDecimal amount) {
    return exchangeRateRepository.findByCurrencyCode(from)
            .zipWith(exchangeRateRepository.findByCurrencyCode(to))
            .map(tuple -> {
              BigDecimal fromRate = tuple.getT1().getRateToRub();
              BigDecimal toRate = tuple.getT2().getRateToRub();
              return round(amount.divide(fromRate, 8, RoundingMode.HALF_UP).multiply(toRate));
            });
  }

  private BigDecimal round(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
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
