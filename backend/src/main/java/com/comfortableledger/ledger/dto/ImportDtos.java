package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ImportDtos {
    private ImportDtos() {
    }

    public record TextImportRequest(String rawText) {
    }

    public record TextImportPreview(
            String rawText,
            TransactionType type,
            LocalDate transactionDate,
            BigDecimal amount,
            String merchant,
            String memo,
            Long recommendedCategoryId,
            String recommendedCategoryName,
            String categoryRecommendationReason,
            List<TextImportItem> items
    ) {
        public TextImportPreview(
                String rawText,
                TransactionType type,
                LocalDate transactionDate,
                BigDecimal amount,
                String merchant,
                String memo,
                Long recommendedCategoryId,
                String recommendedCategoryName,
                String categoryRecommendationReason
        ) {
            this(
                    rawText,
                    type,
                    transactionDate,
                    amount,
                    merchant,
                    memo,
                    recommendedCategoryId,
                    recommendedCategoryName,
                    categoryRecommendationReason,
                    List.of()
            );
        }
    }

    public record TextImportItem(
            String rawLine,
            TransactionType type,
            LocalDate transactionDate,
            BigDecimal amount,
            String merchant,
            String assetName,
            String memo,
            Long recommendedCategoryId,
            String recommendedCategoryName,
            String categoryRecommendationReason
    ) {
    }
}
