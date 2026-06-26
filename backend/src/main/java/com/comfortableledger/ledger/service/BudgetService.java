package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryBudget;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.repository.CategoryBudgetRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MonthlyBudgetRepository;
import com.comfortableledger.ledger.dto.ApiDtos.BudgetSettingsDto;
import com.comfortableledger.ledger.dto.ApiDtos.CategoryBudgetDto;
import com.comfortableledger.ledger.dto.ApiDtos.SaveBudgetRequest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    private final HouseholdRepository householdRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public BudgetService(HouseholdRepository householdRepository, CategoryRepository categoryRepository,
                         CategoryBudgetRepository categoryBudgetRepository,
                         MonthlyBudgetRepository monthlyBudgetRepository) {
        this.householdRepository = householdRepository;
        this.categoryRepository = categoryRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional(readOnly = true)
    public BudgetSettingsDto budgetSettings(String month) {
        Household household = defaultHousehold();
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        MonthlyBudget monthlyBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), yearMonth.toString())
                .orElse(null);
        Map<Long, CategoryBudget> existing = monthlyBudget == null
                ? Map.of()
                : categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));

        var categories = categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                        household.getId(), CategoryType.EXPENSE)
                .stream()
                .map(category -> {
                    CategoryBudget budget = existing.get(category.getId());
                    return budget == null
                            ? CategoryBudgetDto.empty(category)
                            : new CategoryBudgetDto(
                                    category.getId(), category.getName(), category.getIcon(), budget.getAmount());
                })
                .toList();

        return new BudgetSettingsDto(
                yearMonth.toString(),
                monthlyBudget == null ? BigDecimal.ZERO : monthlyBudget.getTotalAmount(),
                categories
        );
    }

    @Transactional
    public BudgetSettingsDto saveBudget(SaveBudgetRequest request) {
        if (request.totalAmount() == null || request.totalAmount().signum() < 0) {
            throw new IllegalArgumentException("Budget total amount cannot be negative");
        }
        Household household = defaultHousehold();
        String budgetMonth = request.month() == null || request.month().isBlank()
                ? YearMonth.now().toString()
                : request.month();
        MonthlyBudget monthlyBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), budgetMonth)
                .orElseGet(() -> monthlyBudgetRepository.save(
                        new MonthlyBudget(household, budgetMonth, BigDecimal.ZERO)));
        monthlyBudget.updateTotalAmount(request.totalAmount());

        Map<Long, CategoryBudget> existing = categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId())
                .stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));
        if (request.categories() != null) {
            for (SaveBudgetRequest.SaveCategoryBudget item : request.categories()) {
                saveCategoryBudget(monthlyBudget, existing, item);
            }
        }
        return budgetSettings(budgetMonth);
    }

    @Transactional
    public BudgetSettingsDto copyPreviousBudget(String month) {
        Household household = defaultHousehold();
        YearMonth targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        YearMonth previousMonth = targetMonth.minusMonths(1);
        MonthlyBudget previousBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), previousMonth.toString())
                .orElseThrow(() -> new IllegalArgumentException("Previous month budget not found"));
        MonthlyBudget targetBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), targetMonth.toString())
                .orElseGet(() -> monthlyBudgetRepository.save(
                        new MonthlyBudget(household, targetMonth.toString(), BigDecimal.ZERO)));
        targetBudget.updateTotalAmount(previousBudget.getTotalAmount());

        Map<Long, CategoryBudget> targetCategories = categoryBudgetRepository.findByMonthlyBudgetId(targetBudget.getId())
                .stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));
        for (CategoryBudget previousCategoryBudget
                : categoryBudgetRepository.findByMonthlyBudgetId(previousBudget.getId())) {
            copyCategoryBudget(targetBudget, targetCategories, previousCategoryBudget);
        }
        return budgetSettings(targetMonth.toString());
    }

    private void saveCategoryBudget(MonthlyBudget monthlyBudget, Map<Long, CategoryBudget> existing,
                                    SaveBudgetRequest.SaveCategoryBudget item) {
        if (item.amount() == null || item.amount().signum() < 0) {
            throw new IllegalArgumentException("Category budget amount cannot be negative");
        }
        Category category = categoryRepository.findById(item.categoryId()).orElseThrow();
        CategoryBudget categoryBudget = existing.get(category.getId());
        if (categoryBudget == null) {
            categoryBudgetRepository.save(new CategoryBudget(monthlyBudget, category, item.amount()));
        } else {
            categoryBudget.updateAmount(item.amount());
        }
    }

    private void copyCategoryBudget(MonthlyBudget targetBudget, Map<Long, CategoryBudget> targetCategories,
                                    CategoryBudget previousCategoryBudget) {
        Long categoryId = previousCategoryBudget.getCategory().getId();
        CategoryBudget targetCategoryBudget = targetCategories.get(categoryId);
        if (targetCategoryBudget == null) {
            categoryBudgetRepository.save(new CategoryBudget(
                    targetBudget,
                    previousCategoryBudget.getCategory(),
                    previousCategoryBudget.getAmount()
            ));
        } else {
            targetCategoryBudget.updateAmount(previousCategoryBudget.getAmount());
        }
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
