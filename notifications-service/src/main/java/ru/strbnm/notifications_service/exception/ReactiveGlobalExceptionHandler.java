package ru.strbnm.notifications_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import ru.strbnm.notifications_service.domain.NotificationErrorResponse;

@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<NotificationErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        return Mono.just(ResponseEntity.badRequest().body(NotificationErrorResponse.builder().message(exception.getMessage()).statusCode(400).build()));
    }

}
