package ru.strbnm.cash_service.exception;

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
import ru.strbnm.cash_service.domain.ErrorListResponse;
import ru.strbnm.cash_service.domain.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), 400)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorListResponse>> handleValidationException(WebExchangeBindException ex) {
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
        ErrorListResponse response = new ErrorListResponse(400);
        response.setMessages(messages);
        return Mono.just(ResponseEntity
                .badRequest()
                .body(response));
    }

    @ExceptionHandler(AccountsServiceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccountsServiceException(AccountsServiceException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage(), 422)));
    }


    @ExceptionHandler({
            UnavailabilityAccountsServiceException.class,
            UnavailabilityBlockerServiceException.class,
            UnavailabilityNotificationsServiceException.class
    })
    public Mono<ResponseEntity<ErrorResponse>> handleCashOperationException(CashOperationException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(exception.getMessage(), 503)));
    }
}
