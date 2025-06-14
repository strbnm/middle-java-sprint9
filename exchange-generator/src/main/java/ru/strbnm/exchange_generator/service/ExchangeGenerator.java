package ru.strbnm.exchange_generator.service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.strbnm.exchange_generator.client.api.ExchangeServiceApi;
import ru.strbnm.exchange_generator.client.domain.ExchangeRateRequest;
import ru.strbnm.exchange_generator.client.domain.Rate;
import ru.strbnm.exchange_generator.exception.ExchangeRatePublicationException;
import ru.strbnm.exchange_generator.exception.UnavailabilityPaymentServiceException;

@Component
public class ExchangeGenerator {

  private final ExchangeServiceApi exchangeServiceApi; // exchange service url
  private final Random random = new Random();
  private final MathContext mathContext = new MathContext(6, RoundingMode.HALF_UP);

  @Autowired
  public ExchangeGenerator(ExchangeServiceApi exchangeServiceApi) {
    this.exchangeServiceApi = exchangeServiceApi;
  }

  public Mono<String> generateAndSendRates() {
    long timestamp = Instant.now().getEpochSecond();

    List<Rate> rates =
            List.of(
                    new Rate("Рубль", "RUB", BigDecimal.ONE),
                    new Rate("Доллар", "USD", round(randomInRange(0.01, 0.02))),
                    new Rate("Юань", "CNY", round(randomInRange(0.1, 0.2))));

    ExchangeRateRequest request = new ExchangeRateRequest(timestamp);
    request.setRates(rates);

    return exchangeServiceApi
            .createRates(request)
            .retryWhen(
                    Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(10)) // бесконечные повторы каждые 10 сек
                            .filter(
                                    throwable ->
                                            (throwable instanceof WebClientResponseException
                                                    && ((WebClientResponseException) throwable).getStatusCode()
                                                    .is5xxServerError())
                                                    || throwable instanceof WebClientRequestException)
            )
            .onErrorResume(
                    ex -> {
                      // Логируем и не прерываем весь поток
                      System.err.println("Ошибка при публикации курсов валют: " + ex.getMessage());
                      return Mono.empty();
                    });
  }


  @PostConstruct
  public void scheduleRateGeneration() {
    Flux.interval(Duration.ofMinutes(2), Duration.ofSeconds(1))
            .flatMap(tick -> generateAndSendRates())
            .subscribe(
                    success -> {},
                    error -> System.err.println("Фатальная ошибка в генерации курсов: " + error.getMessage())
            );
  }

  private BigDecimal round(double value) {
    return new BigDecimal(value, mathContext).setScale(4, RoundingMode.HALF_UP);
  }

  private double randomInRange(double min, double max) {
    return min + (max - min) * random.nextDouble();
  }
}
