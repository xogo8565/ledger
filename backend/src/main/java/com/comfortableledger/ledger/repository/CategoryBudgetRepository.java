package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.CategoryBudget;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, Long> {
    List<CategoryBudget> findByMonthlyBudgetId(Long monthlyBudgetId);
}
