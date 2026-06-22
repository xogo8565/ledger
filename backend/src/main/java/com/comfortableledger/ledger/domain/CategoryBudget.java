package com.comfortableledger.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "category_budgets")
public class CategoryBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private MonthlyBudget monthlyBudget;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Category category;

    @NotNull
    private BigDecimal amount;

    protected CategoryBudget() {
    }

    public CategoryBudget(MonthlyBudget monthlyBudget, Category category, BigDecimal amount) {
        this.monthlyBudget = monthlyBudget;
        this.category = category;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void updateAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
