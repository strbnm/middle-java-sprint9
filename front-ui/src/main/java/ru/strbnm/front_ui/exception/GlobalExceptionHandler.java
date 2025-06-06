package ru.strbnm.front_ui.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<String>> handle(Exception ex) {
        ex.printStackTrace(); // печать в лог
        return Mono.just(ResponseEntity.badRequest().body("Ошибка: " + ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Mono.just(ResponseEntity.badRequest().body("Параметр " + ex.getName() + " имеет некорректное значение: " + ex.getValue()));
    }
}
