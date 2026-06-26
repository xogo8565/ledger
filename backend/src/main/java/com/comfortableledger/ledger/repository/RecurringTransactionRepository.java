package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByHouseholdIdAndActiveTrueOrderByNextRunDateAscIdAsc(Long householdId);

    List<RecurringTransaction> findByHouseholdIdAndActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDateAscIdAsc(
            Long householdId,
            LocalDate upToDate
    );
}
