package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.TransactionRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long>, JpaSpecificationExecutor<TransactionRecord> {
    List<TransactionRecord> findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            Long householdId,
            LocalDate start,
            LocalDate end
    );

    List<TransactionRecord> findByHouseholdIdAndTransactionDateOrderByTransactionDateDescIdDesc(
            Long householdId,
            LocalDate transactionDate
    );

    List<TransactionRecord> findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(Long householdId);

    List<TransactionRecord> findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(String installmentGroupId);

    boolean existsByConsumerId(Long consumerId);

    @Query("""
            select transaction
            from TransactionRecord transaction
            where transaction.household.id = :householdId
              and transaction.type = com.comfortableledger.ledger.domain.TransactionType.EXPENSE
              and transaction.consumer is null
              and (
                transaction.consumptionScope is null
                or transaction.consumptionScope = com.comfortableledger.ledger.domain.ConsumptionScope.PERSONAL
              )
            order by transaction.transactionDate asc, transaction.id asc
            """)
    List<TransactionRecord> findUnassignedPersonalExpenses(@Param("householdId") Long householdId);
}
