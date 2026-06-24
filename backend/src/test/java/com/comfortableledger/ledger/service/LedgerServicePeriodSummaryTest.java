package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerServicePeriodSummaryTest {

    @Test
    void summarizesTotalsAndExpenseBreakdownsForCustomPeriodRecords() {
        Household household = new Household("테스트");
        Category food = new Category(household, CategoryType.EXPENSE, "식비", "•", "#609249", 0);
        Member owner = new Member(household, "나", MemberRole.OWNER);
        List<TransactionRecord> records = List.of(
                transaction(household, TransactionType.EXPENSE, food, ConsumptionScope.PERSONAL, owner, "생활", "12000"),
                transaction(household, TransactionType.EXPENSE, food, ConsumptionScope.SHARED, null, "공동", "8000"),
                transaction(household, TransactionType.INCOME, null, null, null, "", "50000"),
                transaction(household, TransactionType.TRANSFER, null, null, null, "", "30000")
        );

        var summary = LedgerService.periodSummary(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 24),
                records
        );

        assertThat(summary.period()).isEqualTo("2026-06-01 ~ 2026-06-24");
        assertThat(summary.income()).isEqualByComparingTo("50000");
        assertThat(summary.expense()).isEqualByComparingTo("20000");
        assertThat(summary.transfer()).isEqualByComparingTo("30000");
        assertThat(summary.categorySpends())
                .extracting("categoryName", "amount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("식비", new BigDecimal("20000")));
        assertThat(summary.tagSpends())
                .extracting("tagName", "amount", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("생활", new BigDecimal("12000"), 1L),
                        org.assertj.core.groups.Tuple.tuple("공동", new BigDecimal("8000"), 1L)
                );
        assertThat(summary.scopeSpends())
                .extracting("scope", "amount", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ConsumptionScope.PERSONAL, new BigDecimal("12000"), 1L),
                        org.assertj.core.groups.Tuple.tuple(ConsumptionScope.SHARED, new BigDecimal("8000"), 1L)
                );
        assertThat(summary.memberSpends())
                .extracting("memberName", "amount", "transactionCount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("나", new BigDecimal("12000"), 1L));
    }

    private TransactionRecord transaction(Household household, TransactionType type, Category category,
                                          ConsumptionScope scope, Member consumer, String tag, String amount) {
        return new TransactionRecord(
                household,
                null,
                type,
                LocalDate.of(2026, 6, 24),
                new BigDecimal(amount),
                category,
                null,
                null,
                null,
                "테스트",
                "",
                tag,
                scope,
                consumer,
                0
        );
    }
}
