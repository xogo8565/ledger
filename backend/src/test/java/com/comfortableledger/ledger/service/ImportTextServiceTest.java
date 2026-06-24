package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ImportTextServiceTest {
    private final HouseholdRepository households = mock(HouseholdRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final ImportTextService service = new ImportTextService(households, categories, transactions);

    ImportTextServiceTest() {
        when(households.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.empty());
    }

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

    @Test
    void recommendsExpenseCategoryFromMerchantKeyword() {
        Household household = new Household("테스트");
        Category food = new Category(household, CategoryType.EXPENSE, "식비", "🍜", "#fff", 0);
        when(households.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(null, CategoryType.EXPENSE))
                .thenReturn(java.util.List.of(food));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(null))
                .thenReturn(java.util.List.of());

        TextImportPreview preview = service.preview("[신한카드] 06/22 스타벅스 8,900원");

        assertThat(preview.recommendedCategoryName()).isEqualTo("식비");
        assertThat(preview.categoryRecommendationReason()).contains("키워드");
    }

    @Test
    void prefersPreviousMerchantCategoryOverKeywordRule() {
        Household household = new Household("테스트");
        Category food = new Category(household, CategoryType.EXPENSE, "식비", "🍜", "#fff", 0);
        Category culture = new Category(household, CategoryType.EXPENSE, "문화생활", "🎬", "#fff", 1);
        TransactionRecord previous = new TransactionRecord(
                household,
                null,
                TransactionType.EXPENSE,
                LocalDate.now().minusDays(1),
                new BigDecimal("8900"),
                culture,
                null,
                null,
                null,
                "스타벅스 강남점",
                "",
                "",
                0
        );
        when(households.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(household));
        when(categories.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(null, CategoryType.EXPENSE))
                .thenReturn(java.util.List.of(food, culture));
        when(transactions.findTop200ByHouseholdIdOrderByTransactionDateDescIdDesc(null))
                .thenReturn(java.util.List.of(previous));

        TextImportPreview preview = service.preview("[신한카드] 06/22 스타벅스 8,900원");

        assertThat(preview.recommendedCategoryName()).isEqualTo("문화생활");
        assertThat(preview.categoryRecommendationReason()).contains("이전 분류");
    }
}
