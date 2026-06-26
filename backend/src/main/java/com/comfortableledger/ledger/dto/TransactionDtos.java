package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TransactionDtos {
    private TransactionDtos() {
    }

    public record TransactionDto(
            Long id,
            TransactionType type,
            LocalDate transactionDate,
            BigDecimal amount,
            Long categoryId,
            String categoryName,
            String categoryIcon,
            Long assetId,
            String assetName,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            String spendingTag,
            ConsumptionScope consumptionScope,
            Long consumerMemberId,
            String consumerMemberName,
            int installmentMonths,
            int installmentIndex,
            String installmentGroupId
    ) {
        public static TransactionDto from(TransactionRecord record) {
            return new TransactionDto(
                    record.getId(),
                    record.getType(),
                    record.getTransactionDate(),
                    record.getAmount(),
                    record.getCategory() == null ? null : record.getCategory().getId(),
                    record.getCategory() == null ? null : record.getCategory().getName(),
                    record.getCategory() == null ? null : record.getCategory().getIcon(),
                    record.getAsset() == null ? null : record.getAsset().getId(),
                    record.getAsset() == null ? null : record.getAsset().getName(),
                    record.getFromAsset() == null ? null : record.getFromAsset().getId(),
                    record.getToAsset() == null ? null : record.getToAsset().getId(),
                    record.getTitle(),
                    record.getMemo(),
                    record.getSpendingTag(),
                    record.getConsumptionScope(),
                    record.getConsumer() == null ? null : record.getConsumer().getId(),
                    record.getConsumer() == null ? null : record.getConsumer().getName(),
                    record.getInstallmentMonths(),
                    record.getInstallmentIndex(),
                    record.getInstallmentGroupId()
            );
        }
    }

    public record TransactionSearchResultDto(
            List<TransactionDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            String sort
    ) {
    }

    public record CreateTransactionRequest(
            @NotNull TransactionType type,
            @NotNull LocalDate transactionDate,
            @Positive @NotNull BigDecimal amount,
            Long categoryId,
            Long assetId,
            Long fromAssetId,
            Long toAssetId,
            String title,
            String memo,
            String spendingTag,
            ConsumptionScope consumptionScope,
            Long consumerMemberId,
            Integer installmentMonths
    ) {
    }
}
