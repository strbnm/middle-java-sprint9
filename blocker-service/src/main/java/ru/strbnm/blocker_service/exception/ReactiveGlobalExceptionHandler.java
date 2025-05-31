package ru.strbnm.blocker_service.exception;

import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.BlockerErrorResponse;

@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<BlockerErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        return Mono.just(ResponseEntity.badRequest().body(new BlockerErrorResponse(exception.getMessage(), 400)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<BlockerErrorResponse>> handleValidationException(WebExchangeBindException ex) {
        List<String> messages = ex.getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return String.format("Поле '%s': %s", fieldError.getField(), fieldError.getDefaultMessage());
                    } else {
                        return error.getDefaultMessage(); // например, ошибка уровня объекта, а не поля
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        return Mono.just(ResponseEntity
                .badRequest()
                .body(new BlockerErrorResponse(messages.toString(), 400)));
    }
}
