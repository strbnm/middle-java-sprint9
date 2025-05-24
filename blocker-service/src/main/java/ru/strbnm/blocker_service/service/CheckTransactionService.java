package ru.strbnm.blocker_service.service;

import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.TransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;

public interface CheckTransactionService {
    Mono<CheckResult> checkTransaction(CashTransactionRequest checkRequest);

    Mono<CheckResult> checkTransaction(TransferTransactionRequest checkRequest);
}
