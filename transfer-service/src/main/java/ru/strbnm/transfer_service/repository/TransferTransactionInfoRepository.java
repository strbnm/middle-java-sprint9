package ru.strbnm.transfer_service.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.strbnm.transfer_service.entity.TransferTransactionInfo;

@Repository
public interface TransferTransactionInfoRepository extends ReactiveCrudRepository<TransferTransactionInfo, Long> {}
