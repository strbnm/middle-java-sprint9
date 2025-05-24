package ru.strbnm.blocker_service.service.filter;

import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.dto.CheckResult;

public interface ReactiveTransactionFilterChain {
    Mono<CheckResult> next(Object request);
}