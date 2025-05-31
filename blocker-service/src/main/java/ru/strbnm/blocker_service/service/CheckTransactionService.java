package ru.strbnm.blocker_service.service;

import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CheckCashTransactionRequest;
import ru.strbnm.blocker_service.domain.CheckTransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;

public interface CheckTransactionService {
    Mono<CheckResult> checkCashTransaction(CheckCashTransactionRequest checkRequest);

    Mono<CheckResult> checkTransferTransaction(CheckTransferTransactionRequest checkRequest);
}
