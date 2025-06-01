package ru.strbnm.transfer_service.service;

import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;

public interface TransferService {
    Mono<TransferOperationResponse> processTransferTransaction(TransferOperationRequest transferOperationRequest);
}
