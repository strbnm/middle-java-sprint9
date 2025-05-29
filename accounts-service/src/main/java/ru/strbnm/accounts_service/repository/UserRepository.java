package ru.strbnm.accounts_service.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.entity.User;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long>, UserCustomRepository {

    Mono<User> findUserByLogin(String login);
    @Query("SELECT * FROM users ORDER BY name")
    Flux<User> findAllOrderByNameAsc();
}
