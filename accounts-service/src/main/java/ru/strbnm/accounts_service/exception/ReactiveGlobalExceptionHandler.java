package ru.strbnm.accounts_service.exception;

import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.AccountErrorListResponse;
import ru.strbnm.accounts_service.domain.AccountErrorResponse;

@Slf4j
@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<AccountErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.badRequest().body(new AccountErrorResponse(exception.getMessage(), 400)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<AccountErrorListResponse>> handleValidation(WebExchangeBindException ex) {
        log.error("Ошибка {}", ex.getMessage(), ex);
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
        AccountErrorListResponse response = new AccountErrorListResponse(400);
        response.setMessages(messages);
        return Mono.just(ResponseEntity
                .badRequest()
                .body(response));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public Mono<ResponseEntity<AccountErrorResponse>> handleUserAlreadyExists(UserAlreadyExistsException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(new AccountErrorResponse(exception.getMessage(), 409)));
    }

    @ExceptionHandler({
            AccountNotFoundForCurrencyException.class,
            UserNotFoundException.class
    })
    public Mono<ResponseEntity<AccountErrorResponse>> handleUserOperationException(UserOperationException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AccountErrorResponse(exception.getMessage(), 404)));
    }
}
