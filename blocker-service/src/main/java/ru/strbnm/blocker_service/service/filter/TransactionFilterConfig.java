package ru.strbnm.blocker_service.service.filter;

import java.math.BigDecimal;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.CorrespondentEnum;
import ru.strbnm.blocker_service.domain.CurrencyEnum;
import ru.strbnm.blocker_service.domain.TransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;

@Slf4j
@Configuration
public class TransactionFilterConfig {

  @Bean
  public List<ReactiveTransactionFilter> transactionFilters() {
    return new TransactionFilterChainBuilder()
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр превышение допустимой суммы снятия наличных: {}", request);
              if (request instanceof CashTransactionRequest cashReq
                  && CorrespondentEnum.fromValue("cash").equals(cashReq.getTarget())
                  && exceedsCashLimit(cashReq.getAmount(), cashReq.getCurrencyCode())) {
                return Mono.just(CheckResult.blocked("Превышена допустимая сумма снятия наличных"));
              }
              return chain.next(request);
            })
        .addFilter(
                (request, chain) -> {
                  log.debug("Фильтр : {}", request);
                  if (request instanceof CashTransactionRequest cashReq
                          && CorrespondentEnum.fromValue("cash").equals(cashReq.getTarget())
                          && exceedsCashLimit(cashReq.getAmount(), cashReq.getCurrencyCode())) {
                    return Mono.just(CheckResult.blocked("Превышена допустимая сумма снятия наличных"));
                  }
                  return chain.next(request);
                })
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр Недопустимая операция для сервиса переводов: {}", request);
              if (request instanceof TransferTransactionRequest transferReq
                  && (CorrespondentEnum.fromValue("cash")
                          .equals(transferReq.getTo().getTarget())
                      || CorrespondentEnum.fromValue("cash")
                          .equals(transferReq.getFrom().getSource()))) {
                return Mono.just(
                    CheckResult.blocked("Недопустимая операция для сервиса переводов"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр Недопустимая операция для сервиса обналичивания денег: {}", request);
              if (request instanceof CashTransactionRequest cashReq
                  && CorrespondentEnum.fromValue("account")
                      .equals(cashReq.getTarget())
                  && CorrespondentEnum.fromValue("account")
                      .equals(cashReq.getSource())) {
                return Mono.just(
                    CheckResult.blocked("Недопустимая операция для сервиса обналичивания денег"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр Превышена допустимая сумма перевода другим лицам: {}", request);
              if (request instanceof TransferTransactionRequest transferReq
                  && !Boolean.TRUE.equals(transferReq.getIsToYourself())
                  && exceedsTransferLimit(
                      transferReq.getAmount(), transferReq.getFrom().getCurrencyCode())) {
                return Mono.just(
                    CheckResult.blocked("Превышена допустимая сумма перевода другим лицам"));
              }
              return chain.next(request);
            })
        .build();
  }

  private boolean exceedsCashLimit(BigDecimal amount, CurrencyEnum currency) {
    return switch (currency) {
      case CurrencyEnum.RUB -> amount.compareTo(new BigDecimal("150000")) > 0;
      case CurrencyEnum.USD -> amount.compareTo(new BigDecimal("1500")) > 0;
      case CurrencyEnum.CNY -> amount.compareTo(new BigDecimal("15000")) > 0;
    };
  }

  private boolean exceedsTransferLimit(BigDecimal amount, CurrencyEnum currency) {
    return switch (currency) {
      case CurrencyEnum.RUB -> amount.compareTo(new BigDecimal("600000")) > 0;
      case CurrencyEnum.USD -> amount.compareTo(new BigDecimal("6000")) > 0;
      case CurrencyEnum.CNY -> amount.compareTo(new BigDecimal("60000")) > 0;
    };
  }
}
