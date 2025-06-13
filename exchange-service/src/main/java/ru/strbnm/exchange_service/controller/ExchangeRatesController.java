package ru.strbnm.exchange_service.controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.api.ExchangeServiceApi;
import ru.strbnm.exchange_service.domain.ConvertRequest;
import ru.strbnm.exchange_service.domain.ConvertedAmount;
import ru.strbnm.exchange_service.domain.ExchangeRateRequest;
import ru.strbnm.exchange_service.domain.Rate;
import ru.strbnm.exchange_service.service.ExchangeService;

@Controller
@RequestMapping("${openapi.service.base-path:/}")
public class ExchangeRatesController implements ExchangeServiceApi {
  private final ExchangeService exchangeService;

  @Autowired
  public ExchangeRatesController(ExchangeService exchangeService) {
    this.exchangeService = exchangeService;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/api/v1/convert",
      produces = {"application/json"},
      consumes = {"application/json"})
  public Mono<ResponseEntity<ConvertedAmount>> convertCurrency(
      @Parameter(name = "ConvertRequest", description = "", required = true) @Valid @RequestBody
          Mono<ConvertRequest> convertRequest,
      @Parameter(hidden = true) final ServerWebExchange exchange) {
    return convertRequest.flatMap(
        request ->
            exchangeService
                .convert(request.getFrom(), request.getTo(), request.getAmount())
                .flatMap(
                    amount ->
                        Mono.just(
                            ResponseEntity.ok(ConvertedAmount.builder().amount(amount).build()))));
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/api/v1/rates",
      produces = {"application/json"},
      consumes = {"application/json"})
  public Mono<ResponseEntity<String>> createRates(
      @Parameter(name = "ExchangeRateRequest", description = "", required = true)
          @Valid
          @RequestBody
          Mono<ExchangeRateRequest> exchangeRateRequest,
      @Parameter(hidden = true) final ServerWebExchange exchange) {
    return exchangeRateRequest
        .flatMap(exchangeService::saveRates)
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("Success"));
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/api/v1/rates",
      produces = {"application/json"})
  public Mono<ResponseEntity<Flux<Rate>>> getRates(
      @Parameter(hidden = true) final ServerWebExchange exchange) {
    Flux<Rate> rates = exchangeService.getRates();
    return Mono.just(ResponseEntity.ok(rates));
  }
}
