package ru.strbnm.blocker_service.service.filter;

import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.dto.CheckResult;

import java.util.List;

public class DefaultReactiveTransactionFilterChain implements ReactiveTransactionFilterChain {

    private final List<ReactiveTransactionFilter> filters;
    private final int index;

    public DefaultReactiveTransactionFilterChain(List<ReactiveTransactionFilter> filters) {
        this(filters, 0);
    }

    private DefaultReactiveTransactionFilterChain(List<ReactiveTransactionFilter> filters, int index) {
        this.filters = filters;
        this.index = index;
    }

    @Override
    public Mono<CheckResult> next(Object request) {
        if (index >= filters.size()) {
            return Mono.just(CheckResult.allowed());
        }

        ReactiveTransactionFilter current = filters.get(index);
        return current.apply(request, new DefaultReactiveTransactionFilterChain(filters, index + 1));
    }
}
