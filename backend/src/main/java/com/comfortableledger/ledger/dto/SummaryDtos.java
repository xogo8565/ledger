package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.dto.AssetDtos.AssetDto;
import com.comfortableledger.ledger.dto.CategoryDtos.CategoryDto;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SummaryDtos {
    private SummaryDtos() {
    }

    public record MonthlySummaryDto(
            String month,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal transfer,
            BigDecimal assetTotal,
            BigDecimal liabilityTotal,
            BigDecimal netWorth,
            BigDecimal budget,
            BigDecimal remainingBudget,
            BigDecimal budgetUsageRate,
            List<CategorySpend> categorySpends,
            List<TagSpend> tagSpends,
            List<ScopeSpend> scopeSpends,
            List<MemberSpend> memberSpends,
            List<CategoryBudgetUsage> categoryBudgetUsages,
            List<WeeklyTotals> weeklyTotals
    ) {
        public record CategorySpend(Long categoryId, String categoryName, BigDecimal amount) {
        }

        public record TagSpend(String tagName, BigDecimal amount, long transactionCount) {
        }

        public record ScopeSpend(ConsumptionScope scope, BigDecimal amount, long transactionCount) {
        }

        public record MemberSpend(Long memberId, String memberName, BigDecimal amount, long transactionCount) {
        }

        public record WeeklyTotals(
                int weekIndex,
                LocalDate startDate,
                LocalDate endDate,
                BigDecimal expense,
                long transactionCount
        ) {
        }

        public record CategoryBudgetUsage(
                Long categoryId,
                String categoryName,
                String categoryIcon,
                BigDecimal budgetAmount,
                BigDecimal spentAmount,
                BigDecimal remainingAmount,
                BigDecimal usageRate,
                boolean exceeded
        ) {
        }
    }

    public record BootstrapDto(
            List<AssetDto> assets,
            List<CategoryDto> categories,
            List<TransactionDto> transactions,
            MonthlySummaryDto summary
    ) {
    }

    public record YearlySummaryDto(
            int year,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal transfer,
            List<MonthlyTotals> monthlyTotals,
            List<MonthlySummaryDto.CategorySpend> categorySpends,
            List<MonthlySummaryDto.TagSpend> tagSpends,
            List<MonthlySummaryDto.ScopeSpend> scopeSpends,
            List<MonthlySummaryDto.MemberSpend> memberSpends
    ) {
        public record MonthlyTotals(String month, BigDecimal income, BigDecimal expense, BigDecimal transfer) {
        }
    }

    public record YearlyBudgetSummaryDto(
            int year,
            BigDecimal budget,
            BigDecimal expense,
            BigDecimal remainingBudget,
            BigDecimal budgetUsageRate,
            List<MonthlyBudgetUsage> monthlyUsages
    ) {
        public record MonthlyBudgetUsage(
                String month,
                BigDecimal budget,
                BigDecimal expense,
                BigDecimal remainingBudget,
                BigDecimal budgetUsageRate,
                boolean exceeded
        ) {
        }
    }

    public record PeriodSummaryDto(
            String period,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal transfer,
            List<MonthlySummaryDto.CategorySpend> categorySpends,
            List<MonthlySummaryDto.TagSpend> tagSpends,
            List<MonthlySummaryDto.ScopeSpend> scopeSpends,
            List<MonthlySummaryDto.MemberSpend> memberSpends
    ) {
    }
}
