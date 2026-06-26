package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MemberRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemberServiceConsumerMigrationTest {

    @Test
    void migratesOnlyEligibleRecordsToOwnerAndBecomesIdempotent() {
        Household household = new Household("테스트");
        Member owner = new Member(household, "기본 명의", MemberRole.OWNER);
        TransactionRecord legacyPersonalExpense = transaction(household, TransactionType.EXPENSE, null, null);
        TransactionRecord sharedExpense = transaction(household, TransactionType.EXPENSE, ConsumptionScope.SHARED, null);

        HouseholdRepository householdRepository = mock(HouseholdRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        when(householdRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdId(household.getId())).thenReturn(List.of(owner));
        when(transactionRepository.findUnassignedPersonalExpenses(household.getId()))
                .thenReturn(List.of(legacyPersonalExpense))
                .thenReturn(List.of());

        MemberService service = new MemberService(
                householdRepository,
                memberRepository,
                mock(AssetRepository.class),
                transactionRepository
        );

        var first = service.migrateUnassignedPersonalExpenses();
        var second = service.migrateUnassignedPersonalExpenses();

        assertThat(first.ownerMemberName()).isEqualTo("기본 명의");
        assertThat(first.migratedCount()).isEqualTo(1);
        assertThat(second.migratedCount()).isZero();
        assertThat(legacyPersonalExpense.getConsumer()).isSameAs(owner);
        assertThat(sharedExpense.getConsumer()).isNull();
    }

    private TransactionRecord transaction(
            Household household,
            TransactionType type,
            ConsumptionScope scope,
            Member consumer
    ) {
        return new TransactionRecord(
                household,
                null,
                type,
                LocalDate.of(2026, 6, 25),
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
