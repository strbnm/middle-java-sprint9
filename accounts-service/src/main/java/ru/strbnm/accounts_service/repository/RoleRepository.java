package ru.strbnm.accounts_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.entity.Role;

@Repository
public interface RoleRepository extends ReactiveCrudRepository<Role, Long> {
    Mono<Role> findByRoleName(String roleName);
}
