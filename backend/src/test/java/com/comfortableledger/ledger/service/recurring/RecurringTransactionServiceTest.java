package com.comfortableledger.ledger.service.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.RecurrenceFrequency;
import com.comfortableledger.ledger.domain.RecurringTransaction;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.TransactionDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.RecurringTransactionRepository;
import com.comfortableledger.ledger.service.transaction.TransactionCommandService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RecurringTransactionServiceTest {
    private final HouseholdRepository households = mock(HouseholdRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final RecurringTransactionRepository recurringTransactions = mock(RecurringTransactionRepository.class);
    private final TransactionCommandService transactionCommandService = mock(TransactionCommandService.class);
    private final RecurringTransactionService service = new RecurringTransactionService(
            households, assets, categories, recurringTransactions, transactionCommandService);

    @Test
    void monthlyRuleOnLastDayOfMonthDoesNotDriftAfterShortMonth() {
        Household household = household(1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));

        // 매달 말일(1월 31일)에 실행되는 반복 거래
        RecurringTransaction rule = monthlyRule(household, LocalDate.of(2026, 1, 31));
        when(recurringTransactions.findByHouseholdIdAndActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDateAscIdAsc(
                1L, LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(rule));

        var result = service.generateDueTransactions(LocalDate.of(2026, 3, 31));

        // 1월 31일, 2월 28일(짧은 달로 클램프), 3월 31일 - 세 달 모두 빠짐없이 실행되어야 한다.
        assertThat(result.generatedCount()).isEqualTo(3);
        ArgumentCaptor<CreateTransactionRequest> captor = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionCommandService, times(3)).createTransaction(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateTransactionRequest::transactionDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31)
                );

        // 2월(짧은 달)을 지났더라도 다음 실행일은 28일에 고정되지 않고 4월 30일로 되돌아와야 한다.
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void legacyRuleWithoutAnchorDateFallsBackToStartDate() {
        Household household = household(1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));

        RecurringTransaction rule = monthlyRule(household, LocalDate.of(2026, 1, 31));
        // anchorDate 컬럼이 생기기 전에 만들어진 기존 데이터를 흉내낸다.
        ReflectionTestUtils.setField(rule, "anchorDate", null);
        // 이미 짧은 달을 한 번 지나 28일로 밀린 상태를 흉내낸다.
        ReflectionTestUtils.setField(rule, "nextRunDate", LocalDate.of(2026, 2, 28));

        when(recurringTransactions.findByHouseholdIdAndActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDateAscIdAsc(
                1L, LocalDate.of(2026, 2, 28)))
                .thenReturn(List.of(rule));

        service.generateDueTransactions(LocalDate.of(2026, 2, 28));

        // startDate(1월 31일)를 기준으로 복귀해 3월 31일로 계산되어야 한다.
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    private RecurringTransaction monthlyRule(Household household, LocalDate startDate) {
        RecurringTransaction rule = new RecurringTransaction(
                household, TransactionType.EXPENSE, new BigDecimal("50000"), null,
                null, null, null, "월세", "", 0,
                RecurrenceFrequency.MONTHLY, 1, startDate, null, false);
        ReflectionTestUtils.setField(rule, "id", 10L);
        return rule;
    }

    private Household household(Long id) {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", id);
        return household;
    }
}
