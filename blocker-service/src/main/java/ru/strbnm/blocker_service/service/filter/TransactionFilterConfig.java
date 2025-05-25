package ru.strbnm.blocker_service.service.filter;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.CorrespondentEnum;
import ru.strbnm.blocker_service.domain.CurrencyEnum;
import ru.strbnm.blocker_service.domain.TransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;

@Configuration
public class TransactionFilterConfig {

  @Bean
  public List<ReactiveTransactionFilter> transactionFilters() {
    return new TransactionFilterChainBuilder()
        .addFilter(
            (request, chain) -> {
              if (request instanceof CashTransactionRequest cashReq
                  && CorrespondentEnum.fromValue("cash").equals(cashReq.getTarget())
                  && exceedsCashLimit(cashReq.getAmount(), cashReq.getCurrencyCode())) {
                return Mono.just(CheckResult.blocked("Превышена допустимая сумма снятия наличных"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
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

  private boolean exceedsCashLimit(Double amount, CurrencyEnum currency) {
    return switch (currency) {
      case CurrencyEnum.RUB -> amount > 150_000;
      case CurrencyEnum.USD -> amount > 1_500;
      case CurrencyEnum.CNY -> amount > 15_000;
    };
  }

  private boolean exceedsTransferLimit(Double amount, CurrencyEnum currency) {
    return switch (currency) {
      case CurrencyEnum.RUB -> amount > 600_000;
      case CurrencyEnum.USD -> amount > 6_000;
      case CurrencyEnum.CNY -> amount > 60_000;
    };
  }
}
