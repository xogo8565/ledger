package com.comfortableledger.ledger.repo;

import com.comfortableledger.ledger.domain.MonthlyBudget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
    Optional<MonthlyBudget> findByHouseholdIdAndBudgetMonth(Long householdId, String budgetMonth);
}
