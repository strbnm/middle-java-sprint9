package ru.strbnm.notifications_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
  private final MeterRegistry meterRegistry;

  public ReactiveScheduler(
          NotificationRepository notificationRepository,
          @Value("${application.notification.limit:10}") int limit, MeterRegistry meterRegistry) {
    this.notificationRepository = notificationRepository;
    this.limit = limit;
      this.meterRegistry = meterRegistry;
  }

  public Mono<Void> processNotifications() {
    return notificationRepository
        .findUnsentLimited(limit)
        .mapNotNull(
            notification -> {
                boolean fail = ThreadLocalRandom.current().nextInt(100) < 30;
                if (fail) {
                  meterRegistry.counter("notification.failed", "email", notification.getEmail()).increment();
                  return null;
                } else {
                  log.info(
                      "Отправка сообщения на электронную почту {}: {}",
                      notification.getEmail(),
                      notification.getMessage());
                  return notification;
                }})
        .map(
            n -> {
              n.setSent(true);
              return n;
            })
        .collectList()
        .flatMapMany(notificationRepository::saveAll)
        .then();
  }

  @PostConstruct
  public void scheduleTask() {
    Flux.interval(Duration.ofSeconds(60), Duration.ofSeconds(30))
        .flatMap(tick -> processNotifications())
        .subscribe(); // запускаем поток
  }
}
