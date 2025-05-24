package ru.strbnm.blocker_service.controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.CheckTransactionRequest;
import ru.strbnm.blocker_service.domain.CheckTransactionResponse;
import ru.strbnm.blocker_service.domain.TransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;
import ru.strbnm.blocker_service.service.CheckTransactionService;

@Controller
@RequestMapping("${openapi.blockerService.base-path:/}")
public class CheckTransactionController {
    private final CheckTransactionService checkTransactionService;

    @Autowired
    public CheckTransactionController(CheckTransactionService checkTransactionService) {
        this.checkTransactionService = checkTransactionService;
    }

    @RequestMapping(
            method = RequestMethod.POST,
            value = "/api/v1/check_transaction",
            produces = { "application/json" },
            consumes = { "application/json" }
    )
    public Mono<ResponseEntity<CheckTransactionResponse>> checkTransaction(
            @Parameter(name = "CheckTransactionRequest", description = "", required = true) @Valid @RequestBody Mono<CheckTransactionRequest> checkTransactionRequest,
            @Parameter(hidden = true) final ServerWebExchange exchange
    ) {
        return checkTransactionRequest
                .flatMap(req -> {
                    Mono<CheckResult> checkResultMono;
                    Long transactionId;

                    if (req instanceof CashTransactionRequest request) {
                        checkResultMono = checkTransactionService.checkTransaction(request);
                        transactionId = request.getTransactionId();
                    } else if (req instanceof TransferTransactionRequest request) {
                        checkResultMono = checkTransactionService.checkTransaction(request);
                        transactionId = request.getTransactionId();
                    } else {
                        return Mono.error(new IllegalArgumentException("Неподдерживаемый тип запроса"));
                    }

                    return Mono.zip(Mono.just(transactionId), checkResultMono); // сохранить req
                })
                .map(tuple -> {
                    Long transactionId = tuple.getT1();
                    CheckResult result = tuple.getT2();

                    CheckTransactionResponse response = new CheckTransactionResponse();
                    response.setTransactionId(transactionId);
                    response.setIsBlocked(result.isBlocked());
                    response.setReason(result.reason());

                    return ResponseEntity.ok(response);
                });

    }

}
