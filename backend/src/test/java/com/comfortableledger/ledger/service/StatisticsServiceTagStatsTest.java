package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsServiceTagStatsTest {

    @Test
    void aggregatesCommaSeparatedExpenseTagsAndCountsTransactions() {
        Household household = new Household("테스트");
        List<TransactionRecord> records = List.of(
                transaction(household, TransactionType.EXPENSE, "생활, 고정비", "12000"),
                transaction(household, TransactionType.EXPENSE, "#생활,생활", "8000"),
                transaction(household, TransactionType.INCOME, "생활", "50000"),
                transaction(household, TransactionType.EXPENSE, " ", "3000")
        );

        assertThat(StatisticsService.tagSpends(records))
                .extracting("tagName", "amount", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("생활", new BigDecimal("20000"), 2L),
                        org.assertj.core.groups.Tuple.tuple("고정비", new BigDecimal("12000"), 1L)
                );
    }

    private TransactionRecord transaction(Household household, TransactionType type, String tag, String amount) {
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
                tag,
                0
        );
    }
}
