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
@Table(name = "monthly_budgets")
public class MonthlyBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Household household;

    @NotNull
    private String budgetMonth;

    @NotNull
    private BigDecimal totalAmount;

    protected MonthlyBudget() {
    }

    public MonthlyBudget(Household household, String budgetMonth, BigDecimal totalAmount) {
        this.household = household;
        this.budgetMonth = budgetMonth;
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public String getBudgetMonth() {
        return budgetMonth;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void updateTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
