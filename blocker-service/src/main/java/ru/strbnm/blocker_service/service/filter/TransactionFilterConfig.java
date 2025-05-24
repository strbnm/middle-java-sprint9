package ru.strbnm.blocker_service.service.filter;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.CashTransactionRequestFrom;
import ru.strbnm.blocker_service.domain.CashTransactionRequestTo;
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
                  && CashTransactionRequestTo.TargetEnum.fromValue("cash")
                      .equals(cashReq.getTo().getTarget())
                  && exceedsCashLimit(cashReq.getAmount(), cashReq.getFrom().getCurrencyCode())) {
                return Mono.just(CheckResult.blocked("Превышена допустимая сумма снятия наличных"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
              if (request instanceof TransferTransactionRequest transferReq
                  && (CashTransactionRequestTo.TargetEnum.fromValue("cash")
                          .equals(transferReq.getTo().getTarget())
                      || CashTransactionRequestFrom.SourceEnum.fromValue("cash")
                          .equals(transferReq.getFrom().getSource()))) {
                return Mono.just(
                    CheckResult.blocked("Недопустимая операция для сервиса переводов"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
              if (request instanceof CashTransactionRequest cashReq
                  && CashTransactionRequestTo.TargetEnum.fromValue("account")
                      .equals(cashReq.getTo().getTarget())
                  && CashTransactionRequestFrom.SourceEnum.fromValue("account")
                      .equals(cashReq.getFrom().getSource())) {
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

  private boolean exceedsCashLimit(Double amount, String currency) {
    return switch (currency) {
      case "RUB" -> amount > 150_000;
      case "USD" -> amount > 1_500;
      case "CNY" -> amount > 15_000;
      default -> false;
    };
  }

  private boolean exceedsTransferLimit(Double amount, String currency) {
    return switch (currency) {
      case "RUB" -> amount > 600_000;
      case "USD" -> amount > 6_000;
      case "CNY" -> amount > 60_000;
      default -> false;
    };
  }
}
