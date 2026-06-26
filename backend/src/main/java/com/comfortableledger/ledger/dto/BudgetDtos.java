package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.Category;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class BudgetDtos {
    private BudgetDtos() {
    }

    public record CategoryBudgetDto(Long categoryId, String categoryName, String categoryIcon, BigDecimal amount) {
        public static CategoryBudgetDto empty(Category category) {
            return new CategoryBudgetDto(category.getId(), category.getName(), category.getIcon(), BigDecimal.ZERO);
        }
    }

    public record BudgetSettingsDto(String month, BigDecimal totalAmount, List<CategoryBudgetDto> categories) {
    }

    public record SaveBudgetRequest(String month, @NotNull BigDecimal totalAmount, List<SaveCategoryBudget> categories) {
        public record SaveCategoryBudget(@NotNull Long categoryId, @NotNull BigDecimal amount) {
        }
    }
}
