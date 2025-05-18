package ru.strbnm.notifications_service.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import ru.strbnm.notifications_service.repository.NotificationRepository;

@Slf4j
@Component
public class ReactiveScheduler {

    /*
    Компонент для имитации отправки уведомлений на электронную почту. Сообщения выводятся в лог.
    Для шедулинга с заданным интервалом используется Flux.interval().
    */

  private final NotificationRepository notificationRepository;
  private final int limit;

  public ReactiveScheduler(
      NotificationRepository notificationRepository,
      @Value("${application.notification.limit:10}") int limit) {
    this.notificationRepository = notificationRepository;
    this.limit = limit;
  }

  @PostConstruct
  public void scheduleTask() {
    Flux.interval(Duration.ofSeconds(60), Duration.ofSeconds(30))
        .flatMap(
            tick ->
                notificationRepository
                    .findUnsentLimited(limit)
                    .doOnNext(notification -> log.info("Отправка сообщения на электронную почту {}: {}", notification.getEmail(), notification.getMessage()))
                    .map(
                        n -> {
                          n.setSent(true);
                          return n;
                        })
                    .collectList()
                    .flatMapMany(notificationRepository::saveAll))
        .subscribe(); // запускаем поток
  }
}
