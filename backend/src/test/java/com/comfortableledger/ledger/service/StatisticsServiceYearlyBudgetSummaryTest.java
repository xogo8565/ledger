package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CategoryBudgetRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatisticsServiceYearlyBudgetSummaryTest {

    @Test
    void aggregatesMonthlyBudgetsAndExpensesForSelectedYear() {
        Household household = new Household("테스트");
        HouseholdRepository householdRepository = mock(HouseholdRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        MonthlyBudgetRepository monthlyBudgetRepository = mock(MonthlyBudgetRepository.class);
        when(householdRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                household.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(
                transaction(household, LocalDate.of(2026, 1, 10), "80000"),
                transaction(household, LocalDate.of(2026, 2, 10), "120000")
        ));
        when(monthlyBudgetRepository.findByHouseholdIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(
                household.getId(), "2026-01", "2026-12"
        )).thenReturn(List.of(
                new MonthlyBudget(household, "2026-01", new BigDecimal("100000")),
                new MonthlyBudget(household, "2026-02", new BigDecimal("100000"))
        ));

        StatisticsService service = new StatisticsService(
                householdRepository,
                mock(AssetRepository.class),
                mock(CategoryRepository.class),
                mock(CategoryBudgetRepository.class),
                transactionRepository,
                monthlyBudgetRepository
        );

        var summary = service.yearlyBudgetSummary(2026);

        assertThat(summary.budget()).isEqualByComparingTo("200000");
        assertThat(summary.expense()).isEqualByComparingTo("200000");
        assertThat(summary.remainingBudget()).isEqualByComparingTo("0");
        assertThat(summary.budgetUsageRate()).isEqualByComparingTo("100.0");
        assertThat(summary.monthlyUsages().get(0).remainingBudget()).isEqualByComparingTo("20000");
        assertThat(summary.monthlyUsages().get(1).exceeded()).isTrue();
        assertThat(summary.monthlyUsages()).hasSize(12);
    }

    private TransactionRecord transaction(Household household, LocalDate date, String amount) {
        return new TransactionRecord(
                household,
                null,
                TransactionType.EXPENSE,
                date,
                new BigDecimal(amount),
                null,
                null,
                null,
                null,
                "테스트",
                "",
                "",
                0
        );
    }
}
