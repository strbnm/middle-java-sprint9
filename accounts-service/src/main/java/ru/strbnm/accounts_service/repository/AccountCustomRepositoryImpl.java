package ru.strbnm.accounts_service.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.strbnm.accounts_service.domain.AccountInfoRow;
import ru.strbnm.accounts_service.domain.CurrencyEnum;

@Slf4j
@Repository
public class AccountCustomRepositoryImpl implements AccountCustomRepository {
  private final DatabaseClient databaseClient;

  @Autowired
  public AccountCustomRepositoryImpl(@NonNull R2dbcEntityTemplate template) {
    this.databaseClient = template.getDatabaseClient();
  }

  @Override
  public Flux<AccountInfoRow> findUserCurrencyAccounts(String login) {
    String query = """
      SELECT
        a.currency AS currency,
        COALESCE(a.balance, 0::numeric) AS balance,
        TRUE AS "exists"
      FROM users u
      JOIN accounts a ON a.user_id = u.id
      WHERE u.login = :login
      """;

    return databaseClient
            .sql(query)
            .bind("login", login)
            .map(row -> new AccountInfoRow(
                    CurrencyEnum.valueOf(row.get("currency", String.class)),
                    row.get("balance", BigDecimal.class),
                    row.get("exists", Boolean.class)
            ))
            .all()
            .collectList()
            .flatMapMany(existingAccounts -> {
              // Список всех поддерживаемых валют
              List<CurrencyEnum> allCurrencies = List.of(CurrencyEnum.values());

              // Собираем карту существующих валют -> AccountInfoRow
              Map<CurrencyEnum, AccountInfoRow> accountMap = existingAccounts.stream()
                      .collect(Collectors.toMap(AccountInfoRow::getCurrency, Function.identity()));
              log.info(accountMap.toString());
              // Для каждой валюты создаём либо реальную запись, либо дефолтную
              List<AccountInfoRow> result = allCurrencies.stream()
                      .map(currency -> accountMap.getOrDefault(
                              currency,
                              new AccountInfoRow(currency, BigDecimal.ZERO, false)
                      ))
                      .toList();
              log.info(result.toString());
              return Flux.fromIterable(result);
            });
  }
}
