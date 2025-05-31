package ru.strbnm.cash_service.service;

import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.domain.CashOperationRequest;
import ru.strbnm.cash_service.domain.CashOperationResponse;

public interface CashService {
    Mono<CashOperationResponse> processCashTransaction(CashOperationRequest cashRequest);
}
