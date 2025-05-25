package ru.strbnm.accounts_service.repository;

import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.UserDetailResponse;

public interface UserCustomRepository {

    Mono<UserDetailResponse> getUserWithRolesByLogin(String login);
}
