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
    CashTransactionRequest request =
        new CashTransactionRequest(
            1L,
            CurrencyEnum.RUB,
            CorrespondentEnum.ACCOUNT,
            CorrespondentEnum.CASH,
            new BigDecimal("200000"),
            "cash");

    StepVerifier.create(checkTransactionService.checkTransaction(request))
        .expectNextMatches(
            result -> {
              if (!result.isBlocked()) return false;
              assert result.reason() != null;
              return result.reason().contains("Превышена допустимая сумма");
            })
        .verifyComplete();
  }

  @Test
  void transferTransaction_toCashTarget_shouldBeBlocked() {
    TransferTransactionRequest request =
        new TransferTransactionRequest(
            1L,
            new TransferTransactionRequestFrom(CurrencyEnum.RUB, CorrespondentEnum.ACCOUNT),
            new TransferTransactionRequestTo(CurrencyEnum.RUB, CorrespondentEnum.CASH),
            new BigDecimal("1000.0"),
            "cash",
            false);

    StepVerifier.create(checkTransactionService.checkTransaction(request))
        .expectNextMatches(
            result -> {
              if (!result.isBlocked()) return false;
              assert result.reason() != null;
              return result.reason().contains("Недопустимая операция для сервиса переводов");
            })
        .verifyComplete();
  }

    @ParameterizedTest
    @CsvSource({
            "'RUB', '600001', false",
            "'CNY', '60001', false",
            "'USD', '6001', false"
    })
  void transferTransaction_exceedsUsdLimit_shouldBeBlocked(String currency, String amount, boolean isToYourself) {
    TransferTransactionRequest request =
        new TransferTransactionRequest(
            1L,
            new TransferTransactionRequestFrom(CurrencyEnum.fromValue(currency), CorrespondentEnum.ACCOUNT),
            new TransferTransactionRequestTo(CurrencyEnum.fromValue(currency), CorrespondentEnum.ACCOUNT),
            new BigDecimal(amount),
            "transfer",
                isToYourself);

    StepVerifier.create(checkTransactionService.checkTransaction(request))
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
    "'RUB', '150000', 'account', 'cash'",
    "'CNY', '15000', 'account', 'cash'",
    "'USD', '1500', 'account', 'cash'",
      "'RUB', '1000000', 'cash', 'account'",
      "'CNY', '1000000', 'cash', 'account'",
      "'USD', '1000000', 'cash', 'account'"
  })
  void validCashTransaction_shouldBeAllowed(
      String currency, String amount, String source, String target) {
    CashTransactionRequest request =
        new CashTransactionRequest(
            1L,
            CurrencyEnum.fromValue(currency),
            CorrespondentEnum.fromValue(source),
            CorrespondentEnum.fromValue(target),
            new BigDecimal(amount),
            "cash");

    StepVerifier.create(checkTransactionService.checkTransaction(request))
        .expectNext(CheckResult.allowed())
        .verifyComplete();
  }
}
