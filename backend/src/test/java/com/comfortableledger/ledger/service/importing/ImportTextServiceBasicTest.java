package com.comfortableledger.ledger.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ImportTextServiceBasicTest {
    private final HouseholdRepository households = mock(HouseholdRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final ImportTextService service = new ImportTextService(households, categories, transactions);

    ImportTextServiceBasicTest() {
        when(households.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.empty());
    }

    @Test
    void parsesCardApprovalAmountAndDate() {
        TextImportPreview preview = service.preview("[신한카드] 06/22 12:34 승인 스타벅스 8,900원 잔액 100,000원");

        assertThat(preview.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 22));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("8900"));
        assertThat(preview.merchant()).contains("스타벅스");
    }

    @Test
    void parsesDepositAsIncome() {
        TextImportPreview preview = service.preview("우리은행 입금 2026.06.22 김철수 100,000원 잔액 1,000,000원");

        assertThat(preview.type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    void prefersReceiptItemNameAndTotalAmount() {
        TextImportPreview preview = service.preview("""
                스타벅스 강남
                2026-06-26 12:34
                품명 단가 수량 금액
                Americano 4,500 1 4,500
                Latte 5,000 1 5,000
                TOTAL 9,500WON
                """);

        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(2026, 6, 26));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("9500"));
        assertThat(preview.merchant()).isEqualTo("Americano");
        assertThat(preview.memo()).contains("항목:");
    }
}
