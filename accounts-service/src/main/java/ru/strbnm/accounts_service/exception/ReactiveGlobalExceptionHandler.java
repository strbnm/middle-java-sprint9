package ru.strbnm.accounts_service.exception;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.ErrorListResponse;
import ru.strbnm.accounts_service.domain.ErrorResponse;

@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException exception) {
        return Mono.just(ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), 400)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorListResponse>> handleValidationException(WebExchangeBindException ex) {
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

    @ExceptionHandler(UserAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(UserAlreadyExistsException exception) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage(), 409)));
    }


    @ExceptionHandler({
            AccountNotFoundForCurrencyException.class,
            UserNotFoundException.class
    })
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(UserOperationException exception) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage(), 404)));
    }
}
