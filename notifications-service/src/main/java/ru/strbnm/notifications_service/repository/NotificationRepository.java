package ru.strbnm.notifications_service.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.strbnm.notifications_service.entity.Notification;

@Repository
public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {

    @Query("SELECT * FROM notifications WHERE is_sent = FALSE ORDER BY id LIMIT :limit")
    Flux<Notification> findUnsentLimited(@Param("limit") int limit);
}