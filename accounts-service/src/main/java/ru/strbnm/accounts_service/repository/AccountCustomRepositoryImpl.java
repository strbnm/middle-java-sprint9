package ru.strbnm.accounts_service.repository;

import java.math.BigDecimal;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.strbnm.accounts_service.domain.AccountInfoRow;
import ru.strbnm.accounts_service.domain.CurrencyEnum;

@Repository
public class AccountCustomRepositoryImpl implements AccountCustomRepository {
  private final DatabaseClient databaseClient;

  @Autowired
  public AccountCustomRepositoryImpl(@NonNull R2dbcEntityTemplate template) {
    this.databaseClient = template.getDatabaseClient();
  }

  @Override
  public Flux<AccountInfoRow> findUserCurrencyAccounts(String login) {
    String query =
        """
        SELECT
          c.currency AS currency,
          a.balance AS balance,
          CASE WHEN a.id IS NOT NULL THEN TRUE ELSE FALSE END AS "exists"
        FROM users u
        CROSS JOIN (VALUES ('RUB'), ('USD'), ('CNY')) AS c(currency)
        LEFT JOIN account a ON a.user_id = u.id AND a.currency = c.currency
        WHERE u.login = :login;
        """;

    return databaseClient
        .sql(query)
        .bind("login", login)
        .map(
            row -> new AccountInfoRow(
                    CurrencyEnum.valueOf(row.get("currency", String.class)),
                    row.get("balance", BigDecimal.class),
                    row.get("exists", Boolean.class)
            ))
        .all();
  }
}
