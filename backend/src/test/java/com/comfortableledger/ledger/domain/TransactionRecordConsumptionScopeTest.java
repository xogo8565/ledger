package com.comfortableledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionRecordConsumptionScopeTest {

    @Test
    void defaultsLegacyExpenseToPersonalAndKeepsSharedExpense() {
        Household household = new Household("테스트");
        TransactionRecord legacyExpense = transaction(household, TransactionType.EXPENSE, null);
        TransactionRecord sharedExpense = transaction(household, TransactionType.EXPENSE, ConsumptionScope.SHARED);

        assertThat(legacyExpense.getConsumptionScope()).isEqualTo(ConsumptionScope.PERSONAL);
        assertThat(sharedExpense.getConsumptionScope()).isEqualTo(ConsumptionScope.SHARED);
    }

    @Test
    void ignoresConsumptionScopeForNonExpenseTransactions() {
        Household household = new Household("테스트");
        TransactionRecord income = transaction(household, TransactionType.INCOME, ConsumptionScope.SHARED);

        assertThat(income.getConsumptionScope()).isNull();
    }

    @Test
    void keepsConsumerOnlyForPersonalExpenses() {
        Household household = new Household("테스트");
        Member consumer = new Member(household, "개인 명의", MemberRole.EDITOR);
        TransactionRecord personalExpense = transaction(household, TransactionType.EXPENSE, ConsumptionScope.PERSONAL, consumer);
        TransactionRecord sharedExpense = transaction(household, TransactionType.EXPENSE, ConsumptionScope.SHARED, consumer);
        TransactionRecord income = transaction(household, TransactionType.INCOME, ConsumptionScope.PERSONAL, consumer);

        assertThat(personalExpense.getConsumer()).isSameAs(consumer);
        assertThat(sharedExpense.getConsumer()).isNull();
        assertThat(income.getConsumer()).isNull();
    }

    private TransactionRecord transaction(Household household, TransactionType type, ConsumptionScope scope) {
        return transaction(household, type, scope, null);
    }

    private TransactionRecord transaction(Household household, TransactionType type, ConsumptionScope scope, Member consumer) {
        return new TransactionRecord(
                household,
                null,
                type,
                LocalDate.of(2026, 6, 24),
                new BigDecimal("1000"),
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
