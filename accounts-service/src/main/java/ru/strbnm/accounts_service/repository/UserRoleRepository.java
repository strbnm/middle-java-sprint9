package ru.strbnm.accounts_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.strbnm.accounts_service.entity.UserRole;

@Repository
public interface UserRoleRepository extends ReactiveCrudRepository<UserRole, Long> {}
