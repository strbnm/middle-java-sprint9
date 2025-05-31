package ru.strbnm.blocker_service.service.filter;

import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.BlockerCurrencyEnum;
import ru.strbnm.blocker_service.domain.CheckCashTransactionRequest;
import ru.strbnm.blocker_service.domain.CheckTransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;

@Slf4j
@Configuration
public class TransactionFilterConfig {

  @Bean
  public List<ReactiveTransactionFilter> transactionFilters() {
    return new TransactionFilterChainBuilder()
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр Превышена допустимая сумма снятия наличных: {}", request);
              if (request instanceof CheckCashTransactionRequest cashReq
                  && cashReq.getActionType() == CheckCashTransactionRequest.ActionTypeEnum.GET
                  && exceedsCashLimit(cashReq.getAmount(), cashReq.getCurrency())) {
                return Mono.just(CheckResult.blocked("Превышена допустимая сумма снятия наличных"));
              }
              return chain.next(request);
            })
        .addFilter(
            (request, chain) -> {
              log.debug("Фильтр Превышена допустимая сумма перевода другим лицам: {}", request);
              if (request instanceof CheckTransferTransactionRequest transferReq
                  && !Boolean.TRUE.equals(transferReq.getIsItself())
                  && exceedsTransferLimit(
                      transferReq.getAmount(), transferReq.getFromCurrency())) {
                return Mono.just(
                    CheckResult.blocked("Превышена допустимая сумма перевода другим лицам"));
              }
              return chain.next(request);
            })
        .build();
  }

  private boolean exceedsCashLimit(BigDecimal amount, BlockerCurrencyEnum currency) {
    return switch (currency) {
      case BlockerCurrencyEnum.RUB -> amount.compareTo(new BigDecimal("150000")) > 0;
      case BlockerCurrencyEnum.USD -> amount.compareTo(new BigDecimal("1500")) > 0;
      case BlockerCurrencyEnum.CNY -> amount.compareTo(new BigDecimal("15000")) > 0;
    };
  }

  private boolean exceedsTransferLimit(BigDecimal amount, BlockerCurrencyEnum currency) {
    return switch (currency) {
      case BlockerCurrencyEnum.RUB -> amount.compareTo(new BigDecimal("600000")) > 0;
      case BlockerCurrencyEnum.USD -> amount.compareTo(new BigDecimal("6000")) > 0;
      case BlockerCurrencyEnum.CNY -> amount.compareTo(new BigDecimal("60000")) > 0;
    };
  }
}
