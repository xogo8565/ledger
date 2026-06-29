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
        assertThat(preview.memo()).contains("품목:");
    }

    @Test
    void parsesMultipleLedgerLinesGroupedByKoreanDateHeaders() {
        TextImportPreview preview = service.preview("""
                6월 28일 일요일
                -25,950원 | 주식회사 원플러스마트서부 | 삼성카드 taptap O
                +21원 | 예금이자 → 내 NH농협계좌

                6월 27일 토요일
                -7,800원 | 이마트 신월점 | 삼성카드 taptap O
                0원 | 나이스_머니포인트
                취소 -1,653원 (-1.07 USD) | ORACLE SINGAPORE | KB국민 코웨이III 카드 | 해외 결제
                """);

        assertThat(preview.items()).hasSize(4);
        assertThat(preview.memo()).isEqualTo("다건 거래 자동 입력 후보 · 4건");
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 28));
        assertThat(preview.amount()).isEqualByComparingTo(new BigDecimal("25950"));
        assertThat(preview.merchant()).isEqualTo("주식회사 원플러스마트서부");

        assertThat(preview.items().get(0).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.items().get(0).assetName()).isEqualTo("삼성카드 taptap O");
        assertThat(preview.items().get(1).type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.items().get(1).merchant()).isEqualTo("예금이자 → 내 NH농협계좌");
        assertThat(preview.items().get(2).transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 27));
        assertThat(preview.items().get(3).type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.items().get(3).amount()).isEqualByComparingTo(new BigDecimal("1653"));
        assertThat(preview.items().get(3).memo()).contains("해외 결제", "취소/환불");
    }
}
