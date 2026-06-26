package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.MonthlyBudget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
    Optional<MonthlyBudget> findByHouseholdIdAndBudgetMonth(Long householdId, String budgetMonth);

    List<MonthlyBudget> findByHouseholdIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(
            Long householdId,
            String startMonth,
            String endMonth
    );
}
