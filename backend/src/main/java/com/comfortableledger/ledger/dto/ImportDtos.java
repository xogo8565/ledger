package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

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
            String categoryRecommendationReason
    ) {
    }
}
