package ru.strbnm.blocker_service.service;

import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.domain.CashTransactionRequest;
import ru.strbnm.blocker_service.domain.TransferTransactionRequest;
import ru.strbnm.blocker_service.dto.CheckResult;
import ru.strbnm.blocker_service.service.filter.DefaultReactiveTransactionFilterChain;
import ru.strbnm.blocker_service.service.filter.ReactiveTransactionFilter;

@Service
public class CheckTransactionServiceImpl implements CheckTransactionService {

    private final List<ReactiveTransactionFilter> filters;

    public CheckTransactionServiceImpl(List<ReactiveTransactionFilter> filters) {
        this.filters = filters;
    }

    @Override
    public Mono<CheckResult> checkTransaction(CashTransactionRequest checkRequest) {
        return new DefaultReactiveTransactionFilterChain(filters)
                .next(checkRequest);
    }

    @Override
    public Mono<CheckResult> checkTransaction(TransferTransactionRequest checkRequest) {
        return new DefaultReactiveTransactionFilterChain(filters)
                .next(checkRequest);
    }
}
