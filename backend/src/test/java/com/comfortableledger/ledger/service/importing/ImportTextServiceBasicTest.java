package com.comfortableledger.ledger.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void parsesBracketSlashDateHeadersAndSlashSeparatedLedgerLines() {
        TextImportPreview preview = service.preview("""
                [6/30]
                3,600원 / 세븐일레븐 / 삼성카드 taptap O
                2,000원 / 다이소 / 삼성카드 taptap O
                2,400원 / CU / 삼성카드 taptap O
                300,000원 / 내 계좌이체(하나→하나)
                2,000원 / CU / 삼성카드 taptap O

                [6/29]
                2,000원 / CU / 삼성카드 taptap O
                2,300원 / CU
                2,300원 / CU
                7,000원 / 삼성본점
                7,000원 / 세븐일레븐
                1,645원 / ORACLE SINGAPORE / KB국민 코웨이Ⅱ카드 (해외결제 취소)
                507,890원 / 대출이자(55898021734142-00001)
                """);

        assertThat(preview.items()).hasSize(12);
        assertThat(preview.transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 30));
        assertThat(preview.merchant()).isEqualTo("세븐일레븐");
        assertThat(preview.items().get(0).assetName()).isEqualTo("삼성카드 taptap O");
        assertThat(preview.items().get(5).transactionDate()).isEqualTo(LocalDate.of(LocalDate.now().getYear(), 6, 29));
        assertThat(preview.items().get(6).assetName()).isBlank();
        assertThat(preview.items().get(10).type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.items().get(10).merchant()).isEqualTo("ORACLE SINGAPORE");
        assertThat(preview.items().get(10).assetName()).isEqualTo("KB국민 코웨이Ⅱ카드 (해외결제 취소)");
    }

    @Test
    void parsesOutOfOrderYearDateHeaders() {
        TextImportPreview preview = service.preview("""
                [2026-07-23]
                3,600원 / CU

                [2026-07-21]
                10,000원 / 네이버페이 충전 (카카오뱅크 → 네이버페이 머니)
                1,800원 / CU

                [2026-07-20]
                5,000원 / 다이소
                128,116원 / 삼성생명 보험료
                51,200원 / 한화손해보험
                72,850원 / 한국전력
                30,000원 / NICE결제대행
                24,950원 / 코웨이

                [2026-07-19]
                48,412원 / KCP-쿠팡
                33,490원 / KCP-쿠팡
                7,200원 / 애플스토어

                [2026-07-22]
                54,963원 / 한국정보통신-쿠팡
                """);

        assertThat(preview.items()).hasSize(13);
        assertThat(preview.items()).extracting(item -> item.transactionDate()).containsExactly(
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 22)
        );
        assertThat(preview.items().getLast().merchant()).isEqualTo("한국정보통신-쿠팡");
    }

    @Test
    void recommendsMartMerchantsAsFoodAndConvenienceStoresAsConvenience() {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", 1L);
        Category food = category(household, 10L, "식비");
        Category convenience = category(household, 11L, "편의점");
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.EXPENSE))
                .thenReturn(List.of(food, convenience));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(1L)).thenReturn(List.of());

        TextImportPreview martPreview = service.preview("[신한카드] 06/22 승인 이마트 8,900원");
        TextImportPreview conveniencePreview = service.preview("[신한카드] 06/22 승인 CU 2,400원");

        assertThat(martPreview.recommendedCategoryName()).isEqualTo("식비");
        assertThat(conveniencePreview.recommendedCategoryName()).isEqualTo("편의점");
    }

    @Test
    void recommendsExpandedExpenseCategoryKeywords() {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", 1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        category(household, 10L, "식비"),
                        category(household, 11L, "편의점"),
                        category(household, 18L, "레오"),
                        category(household, 12L, "교통/차량"),
                        category(household, 13L, "문화생활"),
                        category(household, 14L, "패션/미용"),
                        category(household, 15L, "생활용품"),
                        category(household, 16L, "주거/통신"),
                        category(household, 17L, "건강")
                ));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(1L)).thenReturn(List.of());

        assertThat(service.preview("[신한카드] 06/22 승인 배달의민족 18,900원").recommendedCategoryName()).isEqualTo("식비");
        assertThat(service.preview("[신한카드] 06/22 승인 이마트24 2,400원").recommendedCategoryName()).isEqualTo("편의점");
        assertThat(service.preview("[신한카드] 06/22 승인 몰리스펫샵 31,000원").recommendedCategoryName()).isEqualTo("레오");
        assertThat(service.preview("[신한카드] 06/22 승인 우리동물병원 88,000원").recommendedCategoryName()).isEqualTo("레오");
        assertThat(service.preview("[신한카드] 06/22 승인 코레일 52,000원").recommendedCategoryName()).isEqualTo("교통/차량");
        assertThat(service.preview("[신한카드] 06/22 승인 넷플릭스 17,000원").recommendedCategoryName()).isEqualTo("문화생활");
        assertThat(service.preview("[신한카드] 06/22 승인 올리브영 23,000원").recommendedCategoryName()).isEqualTo("패션/미용");
        assertThat(service.preview("[신한카드] 06/22 승인 쿠팡 34,000원").recommendedCategoryName()).isEqualTo("생활용품");
        assertThat(service.preview("[신한카드] 06/22 승인 코웨이 29,900원").recommendedCategoryName()).isEqualTo("주거/통신");
        assertThat(service.preview("[신한카드] 06/22 승인 약국 8,000원").recommendedCategoryName()).isEqualTo("건강");
    }

    @Test
    void recommendsExpandedIncomeCategoryKeywords() {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", 1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.INCOME))
                .thenReturn(List.of(
                        category(household, CategoryType.INCOME, 20L, "급여"),
                        category(household, CategoryType.INCOME, 21L, "이자"),
                        category(household, CategoryType.INCOME, 22L, "부수입")
                ));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(1L)).thenReturn(List.of());

        assertThat(service.preview("우리은행 입금 2026.06.22 성과급 500,000원").recommendedCategoryName()).isEqualTo("급여");
        assertThat(service.preview("우리은행 입금 2026.06.22 배당 12,000원").recommendedCategoryName()).isEqualTo("이자");
        assertThat(service.preview("우리은행 입금 2026.06.22 캐시백 3,000원").recommendedCategoryName()).isEqualTo("부수입");
    }

    @Test
    void parsesJulySampleTransferIncomeAndMerchantPaymentExceptions() {
        Household household = new Household("테스트");
        ReflectionTestUtils.setField(household, "id", 1L);
        when(households.findFirstByOrderByIdAsc()).thenReturn(Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        category(household, 10L, "식비"),
                        category(household, 11L, "편의점"),
                        category(household, 12L, "문화생활"),
                        category(household, 13L, "주거/통신")
                ));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(1L, CategoryType.INCOME))
                .thenReturn(List.of(category(household, CategoryType.INCOME, 20L, "부수입")));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(1L)).thenReturn(List.of());

        TextImportPreview preview = service.preview("""
                [7/2]
                10,000원 / 내 하나계좌→동행복권_장석수
                10,100원 / 카카오페이 충전(하나→카카오페이머니)
                14,000원 / 애플스토어

                [7/9]
                141,370원 / 내 하나계좌→(주)오성건설건물관

                [7/13]
                46,440원 / 카카오페이 기타입금(장석수→카카오페이머니) [입금]
                46,440원 / 내 하나계좌→카카오페이
                29,450원 / 내 하나계좌→서울가스 장석수

                [7/14]
                35,748원 / 나이스-(주)우아한형제들
                """);

        assertThat(preview.items()).hasSize(8);
        assertThat(preview.items().get(0).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.items().get(0).recommendedCategoryName()).isEqualTo("문화생활");
        assertThat(preview.items().get(1).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(preview.items().get(2).recommendedCategoryName()).isEqualTo("문화생활");
        assertThat(preview.items().get(3).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.items().get(3).recommendedCategoryName()).isEqualTo("주거/통신");
        assertThat(preview.items().get(4).type()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.items().get(5).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(preview.items().get(6).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.items().get(6).recommendedCategoryName()).isEqualTo("주거/통신");
        assertThat(preview.items().get(7).recommendedCategoryName()).isEqualTo("식비");
    }

    private Category category(Household household, Long id, String name) {
        return category(household, CategoryType.EXPENSE, id, name);
    }

    private Category category(Household household, CategoryType type, Long id, String name) {
        Category category = new Category(household, type, name, "•", "#111111", 0);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
