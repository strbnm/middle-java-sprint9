package ru.strbnm.accounts_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.strbnm.accounts_service.entity.OutboxNotification;

@Repository
public interface OutboxNotificationRepository extends ReactiveCrudRepository<OutboxNotification, Long> {}
