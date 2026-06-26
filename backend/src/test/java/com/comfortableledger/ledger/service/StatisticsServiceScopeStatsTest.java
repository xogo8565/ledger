package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsServiceScopeStatsTest {

    @Test
    void aggregatesExpenseAmountAndCountByConsumptionScope() {
        Household household = new Household("테스트");
        List<TransactionRecord> records = List.of(
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, "12000"),
                transaction(household, TransactionType.EXPENSE, null, "8000"),
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.SHARED, "15000"),
                transaction(household, TransactionType.INCOME, ConsumptionScope.SHARED, "50000")
        );

        assertThat(StatisticsService.scopeSpends(records))
                .extracting("scope", "amount", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ConsumptionScope.PERSONAL, new BigDecimal("20000"), 2L),
                        org.assertj.core.groups.Tuple.tuple(ConsumptionScope.SHARED, new BigDecimal("15000"), 1L)
                );
    }

    private TransactionRecord transaction(Household household, TransactionType type, ConsumptionScope scope, String amount) {
        return new TransactionRecord(
                household,
                null,
                type,
                LocalDate.of(2026, 6, 24),
                new BigDecimal(amount),
                null,
                null,
                null,
                null,
                "테스트",
                "",
                "",
                scope,
                0
        );
    }
}
