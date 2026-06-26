package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.RecurrenceFrequency;
import com.comfortableledger.ledger.domain.RecurringTransaction;
import com.comfortableledger.ledger.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecurringDtos {
    private RecurringDtos() {
    }

    public record RecurringTransactionDto(
            Long id,
            TransactionType type,
            BigDecimal amount,
            Long categoryId,
            String categoryName,
            Long assetId,
            String assetName,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            int installmentMonths,
            RecurrenceFrequency frequency,
            int intervalValue,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRunDate,
            boolean active
    ) {
        public static RecurringTransactionDto from(RecurringTransaction rule) {
            return new RecurringTransactionDto(
                    rule.getId(),
                    rule.getType(),
                    rule.getAmount(),
                    rule.getCategory() == null ? null : rule.getCategory().getId(),
                    rule.getCategory() == null ? null : rule.getCategory().getName(),
                    rule.getAsset() == null ? null : rule.getAsset().getId(),
                    rule.getAsset() == null ? null : rule.getAsset().getName(),
                    rule.getFromAsset() == null ? null : rule.getFromAsset().getId(),
                    rule.getToAsset() == null ? null : rule.getToAsset().getId(),
                    rule.getTitle(),
                    rule.getMemo(),
                    rule.getInstallmentMonths(),
                    rule.getFrequency(),
                    rule.getIntervalValue(),
                    rule.getStartDate(),
                    rule.getEndDate(),
                    rule.getNextRunDate(),
                    rule.isActive()
            );
        }
    }

    public record SaveRecurringTransactionRequest(
            @NotNull TransactionType type,
            @Positive @NotNull BigDecimal amount,
            Long categoryId,
            Long assetId,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            Integer installmentMonths,
            @NotNull RecurrenceFrequency frequency,
            @Positive Integer intervalValue,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRunDate
    ) {
    }

    public record RecurringGenerationResult(int generatedCount) {
    }
}
