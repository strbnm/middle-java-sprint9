package ru.strbnm.cash_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.api.CashServiceApi;
import ru.strbnm.cash_service.domain.CashOperationRequest;
import ru.strbnm.cash_service.domain.CashOperationResponse;
import ru.strbnm.cash_service.service.CashService;

@Controller
@RequestMapping("${openapi.cashService.base-path:/}")
public class CashController implements CashServiceApi {

    private final CashService cashService;

    @Autowired
    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @Override
    public Mono<ResponseEntity<CashOperationResponse>> cashTransaction(Mono<CashOperationRequest> cashOperationRequest, ServerWebExchange exchange) {
        return cashOperationRequest.flatMap(cashService::processCashTransaction)
                .flatMap(this::returnCashOperationResponse);
    }

    private Mono<ResponseEntity<CashOperationResponse>> returnCashOperationResponse(CashOperationResponse accountOperationResponse) {
        if (accountOperationResponse.getOperationStatus() == CashOperationResponse.OperationStatusEnum.FAILED) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(accountOperationResponse));
        } else {
            return Mono.just(ResponseEntity.ok().body(accountOperationResponse));
        }
    }
}
