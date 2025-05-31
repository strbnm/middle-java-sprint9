package ru.strbnm.blocker_service.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier;
import ru.strbnm.blocker_service.domain.*;
import ru.strbnm.blocker_service.dto.CheckResult;
import ru.strbnm.blocker_service.service.filter.TransactionFilterConfig;

@SpringBootTest(
    classes = CheckTransactionServiceImpl.class,
    properties = {"spring.config.name=application-test"})
@Import(TransactionFilterConfig.class)
public class CheckTransactionServiceImplTest {

  @Autowired private CheckTransactionService checkTransactionService;

  @Test
  void cashTransaction_exceedsRubLimit_shouldBeBlocked() {
    CheckCashTransactionRequest request =
        new CheckCashTransactionRequest(
            1L,
            BlockerCurrencyEnum.RUB,
            new BigDecimal("200000"),
            CheckCashTransactionRequest.ActionTypeEnum.GET);

    StepVerifier.create(checkTransactionService.checkCashTransaction(request))
        .expectNextMatches(
            result -> {
              if (!result.isBlocked()) return false;
              assert result.reason() != null;
              return result.reason().contains("Превышена допустимая сумма");
            })
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({"'RUB', '600001', false", "'CNY', '60001', false", "'USD', '6001', false"})
  void transferTransaction_exceedsUsdLimit_shouldBeBlocked(
      String currency, String amount, boolean isItself) {
    CheckTransferTransactionRequest request =
        new CheckTransferTransactionRequest(
            1L,
            BlockerCurrencyEnum.fromValue(currency),
            BlockerCurrencyEnum.fromValue(currency),
            new BigDecimal(amount),
            isItself);

    StepVerifier.create(checkTransactionService.checkTransferTransaction(request))
        .expectNextMatches(
            result -> {
              if (!result.isBlocked()) return false;
              assert result.reason() != null;
              return result.reason().contains("Превышена допустимая сумма перевода другим лицам");
            })
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({
    "'RUB', '150000', 'GET'",
    "'CNY', '15000', 'GET'",
    "'USD', '1500', 'GET'",
    "'RUB', '1000000', 'PUT'",
    "'CNY', '1000000', 'PUT'",
    "'USD', '1000000', 'PUT'"
  })
  void validCashTransaction_shouldBeAllowed(
      String currency, String amount, String actionType) {
    CheckCashTransactionRequest request =
        new CheckCashTransactionRequest(
            1L,
            BlockerCurrencyEnum.fromValue(currency),
            new BigDecimal(amount),
                CheckCashTransactionRequest.ActionTypeEnum.fromValue(actionType));

    StepVerifier.create(checkTransactionService.checkCashTransaction(request))
        .expectNext(CheckResult.allowed())
        .verifyComplete();
  }
}
