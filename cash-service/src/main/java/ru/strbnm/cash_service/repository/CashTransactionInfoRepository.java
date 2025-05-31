package ru.strbnm.cash_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.strbnm.cash_service.entity.CashTransactionInfo;

@Repository
public interface CashTransactionInfoRepository extends ReactiveCrudRepository<CashTransactionInfo, Long> {}
