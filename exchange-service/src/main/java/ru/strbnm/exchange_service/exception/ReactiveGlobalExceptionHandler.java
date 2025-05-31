package ru.strbnm.exchange_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.domain.ExchangeErrorResponse;

@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ExchangeErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        return Mono.just(ResponseEntity.badRequest().body(new ExchangeErrorResponse(exception.getMessage(), 400)));
    }

}
