package ru.strbnm.accounts_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.entity.Account;

public interface AccountRepository extends ReactiveCrudRepository<Account, Long>, AccountCustomRepository {

    Flux<Account> findByUserId(Long userId);

    Mono<Account> findByUserIdAndCurrency(Long userId, String currency);

    Mono<Void> deleteByUserIdAndCurrency(Long userId, String currency);
}
