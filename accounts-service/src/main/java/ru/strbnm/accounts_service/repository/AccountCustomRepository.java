package ru.strbnm.accounts_service.repository;

import reactor.core.publisher.Flux;
import ru.strbnm.accounts_service.domain.AccountInfoRow;

public interface AccountCustomRepository {

    Flux<AccountInfoRow> findUserCurrencyAccounts(String login);
}
