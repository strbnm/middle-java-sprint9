package ru.strbnm.accounts_service.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.strbnm.accounts_service.entity.OutboxNotification;

@Repository
public interface OutboxNotificationRepository extends ReactiveCrudRepository<OutboxNotification, Long> {
    @Query("SELECT * FROM outbox_notifications WHERE is_sent = FALSE ORDER BY id LIMIT :limit")
    Flux<OutboxNotification> findUnsentLimited(@Param("limit") int limit);
}
