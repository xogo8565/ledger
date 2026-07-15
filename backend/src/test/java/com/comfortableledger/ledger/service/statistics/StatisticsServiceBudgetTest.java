package com.comfortableledger.ledger.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryBudget;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CategoryBudgetRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StatisticsServiceBudgetTest {
    private final HouseholdRepository households = mock(HouseholdRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CategoryBudgetRepository categoryBudgets = mock(CategoryBudgetRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final MonthlyBudgetRepository monthlyBudgets = mock(MonthlyBudgetRepository.class);
    private final StatisticsService service = new StatisticsService(
            households, assets, categories, categoryBudgets, transactions, monthlyBudgets);

    @Test
    void monthlyRemainingBudgetUsesOnlyMonthlyExpenses() {
        Household household = household(1L);
        Category food = category(household, 10L, "식비");
        Category living = category(household, 11L, "생활");
        MonthlyBudget budget = monthlyBudget(household, 100L, "2026-06", "500000");

        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(transactions.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                1L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(
                        transaction(household, TransactionType.EXPENSE, LocalDate.of(2026, 6, 1), "120000", food),
                        transaction(household, TransactionType.EXPENSE, LocalDate.of(2026, 6, 2), "80000", living),
                        transaction(household, TransactionType.INCOME, LocalDate.of(2026, 6, 3), "1000000", null),
                        transaction(household, TransactionType.TRANSFER, LocalDate.of(2026, 6, 4), "300000", null)
                ));
        when(assets.findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(asset(household, AssetType.BANK, "1000000")));
        when(monthlyBudgets.findByHouseholdIdAndBudgetMonth(1L, "2026-06"))
                .thenReturn(Optional.of(budget));
        when(categoryBudgets.findByMonthlyBudgetId(100L))
                .thenReturn(List.of(
                        categoryBudget(budget, food, "150000"),
                        categoryBudget(budget, living, "50000")
                ));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.EXPENSE))
                .thenReturn(List.of(food, living));

        var summary = service.monthlySummary("2026-06");

        assertThat(summary.expense()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(summary.budget()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(summary.remainingBudget()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(summary.budgetUsageRate()).isEqualByComparingTo(new BigDecimal("40.0"));

        var foodUsage = summary.categoryBudgetUsages().stream()
                .filter(item -> item.categoryId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(foodUsage.spentAmount()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(foodUsage.remainingAmount()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(foodUsage.exceeded()).isFalse();

        var livingUsage = summary.categoryBudgetUsages().stream()
                .filter(item -> item.categoryId().equals(11L))
                .findFirst()
                .orElseThrow();
        assertThat(livingUsage.spentAmount()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(livingUsage.remainingAmount()).isEqualByComparingTo(new BigDecimal("-30000"));
        assertThat(livingUsage.exceeded()).isTrue();
    }

    @Test
    void yearlyRemainingBudgetSumsMonthlyBudgetsMinusExpenses() {
        Household household = household(1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(monthlyBudgets.findByHouseholdIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(
                1L, "2026-01", "2026-12"))
                .thenReturn(List.of(
                        monthlyBudget(household, 101L, "2026-01", "100000"),
                        monthlyBudget(household, 102L, "2026-02", "200000")
                ));
        when(transactions.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(
                        transaction(household, TransactionType.EXPENSE, LocalDate.of(2026, 1, 10), "70000", null),
                        transaction(household, TransactionType.EXPENSE, LocalDate.of(2026, 2, 10), "250000", null),
                        transaction(household, TransactionType.INCOME, LocalDate.of(2026, 2, 11), "500000", null),
                        transaction(household, TransactionType.TRANSFER, LocalDate.of(2026, 3, 1), "40000", null)
                ));

        var summary = service.yearlyBudgetSummary(2026);

        assertThat(summary.budget()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(summary.expense()).isEqualByComparingTo(new BigDecimal("320000"));
        assertThat(summary.remainingBudget()).isEqualByComparingTo(new BigDecimal("-20000"));
        assertThat(summary.budgetUsageRate()).isEqualByComparingTo(new BigDecimal("106.7"));
        assertThat(summary.monthlyUsages().get(0).remainingBudget()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(summary.monthlyUsages().get(1).remainingBudget()).isEqualByComparingTo(new BigDecimal("-50000"));
        assertThat(summary.monthlyUsages().get(1).exceeded()).isTrue();
    }

    private Household household(Long id) {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", id);
        return household;
    }

    private Category category(Household household, Long id, String name) {
        Category category = new Category(household, CategoryType.EXPENSE, name, "•", "#111111", 0);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MonthlyBudget monthlyBudget(Household household, Long id, String month, String amount) {
        MonthlyBudget budget = new MonthlyBudget(household, month, new BigDecimal(amount));
        ReflectionTestUtils.setField(budget, "id", id);
        return budget;
    }

    private CategoryBudget categoryBudget(MonthlyBudget monthlyBudget, Category category, String amount) {
        return new CategoryBudget(monthlyBudget, category, new BigDecimal(amount));
    }

    private Asset asset(Household household, AssetType type, String balance) {
        return new Asset(household, type, type.name(), new BigDecimal(balance), type.name());
    }

    private TransactionRecord transaction(
            Household household, TransactionType type, LocalDate date, String amount, Category category) {
        return new TransactionRecord(
                household, null, type, date, new BigDecimal(amount), category,
                null, null, null, type.name(), "", "", 0);
    }
}
