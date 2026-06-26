package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class CategoryDtos {
    private CategoryDtos() {
    }

    public record CategoryDto(Long id, CategoryType type, String name, String icon, String color) {
        public static CategoryDto from(Category category) {
            return new CategoryDto(category.getId(), category.getType(), category.getName(), category.getIcon(), category.getColor());
        }
    }

    public record SaveCategoryRequest(
            @NotNull CategoryType type,
            @NotBlank String name,
            String icon,
            String color
    ) {
    }
}
