package ru.strbnm.notifications_service.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.kafka.dto.NotificationMessage;
import ru.strbnm.notifications_service.entity.Notification;
import ru.strbnm.notifications_service.repository.NotificationRepository;

@Slf4j
@Service
public class KafkaConsumerService {

    private final NotificationRepository notificationRepository;

    @Autowired
    public KafkaConsumerService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @RetryableTopic(
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000),
            dltTopicSuffix = "-for-analys-dlt",
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = {"notifications"},
            idIsGroup = false
    )
    public Mono<Void> listen(NotificationMessage message) {
        log.info(String.valueOf(message));
        return processNotification(message)
                .doOnError(error -> log.error("Ошибка при сохранении уведомлений в БД", error))
                .then();
    }

    private Mono<Void> processNotification(NotificationMessage message) {
        return Mono.just(message)
                .map(this::convertToEntity)
                .flatMap(notificationRepository::save)
                .then();
    }

    private Notification convertToEntity(NotificationMessage msg) {
        return Notification.builder()
                .email(msg.getEmail())
                .message(msg.getMessage())
                .application(msg.getApplication())
                .isSent(false)
                .build();
    }
}
