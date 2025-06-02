package ru.strbnm.cash_service.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.strbnm.cash_service.client.notifications.api.NotificationsServiceApi;
import ru.strbnm.cash_service.client.notifications.domain.NotificationRequest;
import ru.strbnm.cash_service.repository.OutboxNotificationRepository;

@Slf4j
@Profile("!contracts & !test")
@Component
public class OutboxReactiveScheduler {

  private final OutboxNotificationRepository outboxNotificationRepository;
  private final NotificationsServiceApi notificationsServiceApi;
  private final int limit;

  public OutboxReactiveScheduler(
          OutboxNotificationRepository outboxNotificationRepository,
          NotificationsServiceApi notificationsServiceApi,
          @Value("${application.notification.limit:10}") int limit) {
    this.outboxNotificationRepository = outboxNotificationRepository;
      this.notificationsServiceApi = notificationsServiceApi;
      this.limit = limit;
  }

    public Mono<Void> processSendNotifications() {
        return outboxNotificationRepository
                .findUnsentLimited(limit)
                .flatMap(notification -> {
                    NotificationRequest notificationRequest = new NotificationRequest();
                    notificationRequest.setEmail(notification.getEmail());
                    notificationRequest.setMessage(notification.getMessage());
                    notificationRequest.application(NotificationRequest.ApplicationEnum.CASH_SERVICE);

                    return notificationsServiceApi.notificationCreate(notificationRequest)
                            .retryWhen(
                                    Retry.max(1)
                                            .filter(throwable ->
                                                    (throwable instanceof WebClientResponseException
                                                            && (((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                                                            || ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE))
                                                            || throwable instanceof WebClientRequestException)
                            )
                            .then(Mono.just(notification)) // если отправка успешна — продолжаем
                            .doOnNext(n -> {
                                n.setSent(true);
                                n.setUpdatedAt(Instant.now().getEpochSecond());
                            })
                            .onErrorResume(ex -> {
                                log.error("Ошибка отправки уведомления {}: {}", notification.getId(), ex.getMessage(), ex);
                                return Mono.empty(); // не возвращаем уведомление, значит оно не попадёт в сохранение
                            });
                })
                .collectList()
                .flatMapMany(outboxNotificationRepository::saveAll)
                .then();
    }


  @PostConstruct
  public void scheduleTask() {
    Flux.interval(Duration.ofMinutes(2), Duration.ofSeconds(5))
        .flatMap(tick -> processSendNotifications())
        .subscribe(); // запускаем поток
  }
}
