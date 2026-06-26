package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionSearchCriteria(
        LocalDate startDate,
        LocalDate endDate,
        TransactionType type,
        Long categoryId,
        ConsumptionScope consumptionScope,
        Long consumerMemberId,
        Long assetId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String query,
        Integer page,
        Integer size,
        TransactionSearchSort sort
) {
    public TransactionSearchCriteria {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if ((minAmount != null && minAmount.signum() < 0) || (maxAmount != null && maxAmount.signum() < 0)) {
            throw new IllegalArgumentException("Amount filters cannot be negative");
        }
        if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException("Maximum amount cannot be less than minimum amount");
        }
        if (size != null && size < 1) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        query = query == null ? "" : query.trim();
        page = page == null || page < 0 ? 0 : page;
        size = size == null ? 50 : Math.min(size, 200);
        sort = sort == null ? TransactionSearchSort.DATE_DESC : sort;
    }
}
