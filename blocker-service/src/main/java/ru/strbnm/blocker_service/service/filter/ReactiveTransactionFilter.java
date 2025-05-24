package ru.strbnm.blocker_service.service.filter;

import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.dto.CheckResult;

@FunctionalInterface
public interface ReactiveTransactionFilter {
  Mono<CheckResult> apply(Object request, ReactiveTransactionFilterChain chain);
}

