package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

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

class StatisticsServiceMemberStatsTest {

    @Test
    void aggregatesOnlyPersonalExpenseAmountAndCountByConsumerMember() {
        Household household = new Household("테스트");
        Member owner = new Member(household, "기본 명의", MemberRole.OWNER);
        Member partner = new Member(household, "가족", MemberRole.EDITOR);
        List<TransactionRecord> records = List.of(
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, owner, "12000"),
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, partner, "8000"),
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, owner, "5000"),
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, null, "3000"),
                transaction(household, TransactionType.EXPENSE, ConsumptionScope.SHARED, partner, "15000"),
                transaction(household, TransactionType.INCOME, ConsumptionScope.PERSONAL, owner, "50000")
        );

        assertThat(StatisticsService.memberSpends(records))
                .extracting("memberName", "amount", "transactionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("기본 명의", new BigDecimal("17000"), 2L),
                        org.assertj.core.groups.Tuple.tuple("가족", new BigDecimal("8000"), 1L),
                        org.assertj.core.groups.Tuple.tuple("명의 미지정", new BigDecimal("3000"), 1L)
                );
    }

    private TransactionRecord transaction(Household household, TransactionType type, ConsumptionScope scope,
                                          Member consumer, String amount) {
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
                consumer,
                0
        );
    }
}
