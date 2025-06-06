package ru.strbnm.api.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<String>> handle(ServerWebExchange exchange, Exception ex) {
        String path = exchange.getRequest().getURI().toString();
        String method = exchange.getRequest().getMethod().toString();
        String hostHeader = exchange.getRequest().getHeaders().getFirst("Host");

        log.error("Ошибка при обработке запроса:");
        log.error("HTTP {} {}", method, path);
        log.error("Host: {}", hostHeader);
        log.error("Exception: ", ex);

        return Mono.just(ResponseEntity
                .badRequest()
                .body("Ошибка при маршрутизации: " + ex.getMessage()));
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Mono.just(ResponseEntity.badRequest().body("Параметр " + ex.getName() + " имеет некорректное значение: " + ex.getValue()));
    }
}
