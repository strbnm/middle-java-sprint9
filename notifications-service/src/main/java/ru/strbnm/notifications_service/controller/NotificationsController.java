package ru.strbnm.notifications_service.controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.strbnm.notifications_service.api.NotificationsServiceApi;
import ru.strbnm.notifications_service.domain.NotificationRequest;
import ru.strbnm.notifications_service.entity.Notification;
import ru.strbnm.notifications_service.repository.NotificationRepository;

@Controller
@RequestMapping("${openapi.notificationsService.base-path:/}")
public class NotificationsController implements NotificationsServiceApi {
  private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationsController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

  @Transactional
  @Override
  public Mono<ResponseEntity<String>> notificationCreate(
          @Parameter(name = "NotificationRequest", description = "", required = true) @Valid @RequestBody Mono<NotificationRequest> notificationRequest,
          @Parameter(hidden = true) final ServerWebExchange exchange
  ) {
      return notificationRequest.flatMap(request -> {
          Notification notification = Notification.builder()
                  .email(request.getEmail())
                  .message(request.getMessage())
                  .application(request.getApplication())
                  .build();

          return notificationRepository.save(notification)
                  .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("Success"));
      });
  }



}
