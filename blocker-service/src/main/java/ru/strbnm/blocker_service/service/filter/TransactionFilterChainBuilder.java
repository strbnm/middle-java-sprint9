package ru.strbnm.blocker_service.service.filter;

import java.util.ArrayList;
import java.util.List;

public class TransactionFilterChainBuilder {

    private final List<ReactiveTransactionFilter> filters = new ArrayList<>();

    public TransactionFilterChainBuilder addFilter(ReactiveTransactionFilter filter) {
        filters.add(filter);
        return this; // позволяет цепочку
    }

    public List<ReactiveTransactionFilter> build() {
        return filters;
    }
}
