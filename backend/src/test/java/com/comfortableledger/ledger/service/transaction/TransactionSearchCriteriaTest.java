package com.comfortableledger.ledger.service.transaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionSearchCriteriaTest {

    @Test
    void rejectsInvalidSearchRangesBeforeQueryingRepository() {
        assertThatThrownBy(() -> criteria(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 6, 1),
                null, null, null, null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date cannot be before start date");

        assertThatThrownBy(() -> criteria(
                null, null, null, null, null, null, null,
                new BigDecimal("2000"), new BigDecimal("1000"), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum amount cannot be less than minimum amount");

        assertThatThrownBy(() -> criteria(
                null, null, null, null, null, null, null,
                new BigDecimal("-1"), null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount filters cannot be negative");

        assertThatThrownBy(() -> new TransactionSearchCriteria(
                null, null, null, null, null, null, null,
                null, null, null, 0, 0, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must be positive");
    }

    private TransactionSearchCriteria criteria(
            LocalDate startDate, LocalDate endDate,
            com.comfortableledger.ledger.domain.TransactionType type,
            Long categoryId,
            com.comfortableledger.ledger.domain.ConsumptionScope consumptionScope,
            Long consumerMemberId, Long assetId,
            BigDecimal minAmount, BigDecimal maxAmount, String query
    ) {
        return new TransactionSearchCriteria(
                startDate, endDate, type, categoryId, consumptionScope,
                consumerMemberId, assetId, minAmount, maxAmount, query,
                null, null, null);
    }
}
