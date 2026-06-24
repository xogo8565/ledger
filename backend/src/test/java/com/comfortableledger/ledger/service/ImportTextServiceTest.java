package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ImportTextServiceTest {
    private final ImportTextService service = new ImportTextService();

    @Test
    void parsesCardApprovalWithBalanceAmount() {
        TextImportPreview preview = service.preview("[신한카드] 06/22 12:34 승인 스타벅스 8,900원 잔액 100,000원");

        assertThat(preview.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 22));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("8900"));
        assertThat(preview.merchant()).isEqualTo("스타벅스");
        assertThat(preview.memo()).contains("카드 승인");
    }

    @Test
    void parsesCheckCardWithdrawal() {
        TextImportPreview preview = service.preview("KB체크카드 출금 6월22일 편의점CU 4,500원");

        assertThat(preview.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("4500"));
        assertThat(preview.merchant()).isEqualTo("편의점CU");
        assertThat(preview.memo()).contains("체크/출금");
    }

    @Test
    void parsesBankDeposit() {
        TextImportPreview preview = service.preview("우리은행 입금 2026.06.22 김철수 100,000원 잔액 1,000,000원");

        assertThat(preview.type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(preview.merchant()).isEqualTo("김철수");
        assertThat(preview.memo()).contains("입금");
    }

    @Test
    void parsesApprovalCancelAsIncome() {
        TextImportPreview preview = service.preview("삼성카드 승인취소 06/22 쿠팡 15,000원");

        assertThat(preview.type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(preview.merchant()).isEqualTo("쿠팡");
        assertThat(preview.memo()).contains("취소/환불");
    }
}
