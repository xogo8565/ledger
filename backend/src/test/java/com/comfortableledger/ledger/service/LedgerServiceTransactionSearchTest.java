package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CardProfileRepository;
import com.comfortableledger.ledger.repo.CategoryBudgetRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MemberRepository;
import com.comfortableledger.ledger.repo.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repo.ReceiptAttachmentRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LedgerServiceTransactionSearchTest {

    private final LedgerService service = new LedgerService(
            mock(HouseholdRepository.class),
            mock(MemberRepository.class),
            mock(AssetRepository.class),
            mock(CategoryRepository.class),
            mock(CategoryBudgetRepository.class),
            mock(TransactionRepository.class),
            mock(MonthlyBudgetRepository.class),
            mock(CardProfileRepository.class),
            mock(ReceiptAttachmentRepository.class)
    );

    @Test
    void rejectsInvalidSearchRangesBeforeQueryingRepository() {
        assertThatThrownBy(() -> service.searchTransactions(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 6, 1),
                null, null, null, null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date cannot be before start date");

        assertThatThrownBy(() -> service.searchTransactions(
                null, null, null, null, null, null, null,
                new BigDecimal("2000"), new BigDecimal("1000"), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum amount cannot be less than minimum amount");

        assertThatThrownBy(() -> service.searchTransactions(
                null, null, null, null, null, null, null,
                new BigDecimal("-1"), null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount filters cannot be negative");
    }
}
