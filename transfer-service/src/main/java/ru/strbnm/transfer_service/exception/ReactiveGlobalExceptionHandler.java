package ru.strbnm.transfer_service.exception;

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
import ru.strbnm.transfer_service.domain.TransferErrorListResponse;
import ru.strbnm.transfer_service.domain.TransferErrorResponse;

@Slf4j
@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<TransferErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.badRequest().body(new TransferErrorResponse(exception.getMessage(), 400)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<TransferErrorListResponse>> handleValidationException(WebExchangeBindException ex) {
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
        TransferErrorListResponse response = new TransferErrorListResponse(400);
        response.setMessages(messages);
        return Mono.just(ResponseEntity
                .badRequest()
                .body(response));
    }

    @ExceptionHandler({
            UnavailabilityAccountsServiceException.class,
            UnavailabilityBlockerServiceException.class,
            UnavailabilityNotificationsServiceException.class,
            UnavailabilityExchangeServiceException.class
    })
    public Mono<ResponseEntity<TransferErrorResponse>> handleCashOperationException(CashOperationException exception) {
        log.error("Ошибка {}", exception.getMessage(), exception);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new TransferErrorResponse(exception.getMessage(), 503)));
    }
}
