package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerServiceWeeklyTotalsTest {

    @Test
    void groupsMonthlyExpensesIntoSundayEndingWeeks() {
        Household household = new Household("테스트");
        List<TransactionRecord> records = List.of(
                transaction(household, LocalDate.of(2026, 6, 1), TransactionType.EXPENSE, "1000"),
                transaction(household, LocalDate.of(2026, 6, 7), TransactionType.EXPENSE, "2000"),
                transaction(household, LocalDate.of(2026, 6, 8), TransactionType.EXPENSE, "3000"),
                transaction(household, LocalDate.of(2026, 6, 8), TransactionType.INCOME, "9000"),
                transaction(household, LocalDate.of(2026, 6, 30), TransactionType.EXPENSE, "4000")
        );

        var totals = LedgerService.weeklyTotals(YearMonth.of(2026, 6), records);

        assertThat(totals).hasSize(5);
        assertThat(totals)
                .extracting("weekIndex", "startDate", "endDate", "expense", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), new BigDecimal("3000"), 2L),
                        org.assertj.core.groups.Tuple.tuple(2, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), new BigDecimal("3000"), 1L),
                        org.assertj.core.groups.Tuple.tuple(3, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), BigDecimal.ZERO, 0L),
                        org.assertj.core.groups.Tuple.tuple(4, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 28), BigDecimal.ZERO, 0L),
                        org.assertj.core.groups.Tuple.tuple(5, LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30), new BigDecimal("4000"), 1L)
                );
    }

    private TransactionRecord transaction(Household household, LocalDate date, TransactionType type, String amount) {
        return new TransactionRecord(
                household,
                null,
                type,
                date,
                new BigDecimal(amount),
                null,
                null,
                null,
                null,
                "테스트",
                "",
                "",
                0
        );
    }
}
