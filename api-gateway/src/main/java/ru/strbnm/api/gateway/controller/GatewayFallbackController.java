package ru.strbnm.api.gateway.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(String serviceName) {
        Map<String, Object> body = Map.of(
                "operationStatus", "FAILED",
                "errors", List.of("Сервис " + serviceName + " временно недоступен")
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }

    @GetMapping("/accounts-service")
    public Mono<ResponseEntity<Map<String, Object>>> accountsServiceFallback() {
        return buildFallbackResponse("аккаунтов");
    }

    @GetMapping("/cash-service")
    public Mono<ResponseEntity<Map<String, Object>>> cashServiceFallback() {
        return buildFallbackResponse("обналичивания");
    }

    @GetMapping("/blocker-service")
    public Mono<ResponseEntity<Map<String, Object>>> blockerServiceFallback() {
        return buildFallbackResponse("проверки подозрительных операций");
    }

    @GetMapping("/exchange-service")
    public Mono<ResponseEntity<Map<String, Object>>> exchangeServiceFallback() {
        return buildFallbackResponse("конвертации валют");
    }

    @GetMapping("/transfer-service")
    public Mono<ResponseEntity<Map<String, Object>>> transferServiceFallback() {
        return buildFallbackResponse("переводов");
    }
}
