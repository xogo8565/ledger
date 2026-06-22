package com.comfortableledger.ledger.repo;

import com.comfortableledger.ledger.domain.TransactionRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            Long householdId,
            LocalDate start,
            LocalDate end
    );

    List<TransactionRecord> findByHouseholdIdAndTransactionDateOrderByTransactionDateDescIdDesc(
            Long householdId,
            LocalDate transactionDate
    );
}
