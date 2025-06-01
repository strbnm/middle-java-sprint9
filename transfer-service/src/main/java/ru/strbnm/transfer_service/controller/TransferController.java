package ru.strbnm.transfer_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.api.TransferServiceApi;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;
import ru.strbnm.transfer_service.service.TransferService;

@Controller
@RequestMapping("${openapi.cashService.base-path:/}")
public class TransferController implements TransferServiceApi {

    private final TransferService transferService;

    @Autowired
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }


    private Mono<ResponseEntity<TransferOperationResponse>> returnCashOperationResponse(TransferOperationResponse accountOperationResponse) {
        if (accountOperationResponse.getOperationStatus() == TransferOperationResponse.OperationStatusEnum.FAILED) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(accountOperationResponse));
        } else {
            return Mono.just(ResponseEntity.ok().body(accountOperationResponse));
        }
    }

    @Override
    public Mono<ResponseEntity<TransferOperationResponse>> transferTransaction(Mono<TransferOperationRequest> transferOperationRequest, ServerWebExchange exchange) {
        return transferOperationRequest.flatMap(transferService::processTransferTransaction)
                .flatMap(this::returnCashOperationResponse);
    }
}
