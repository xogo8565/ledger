package com.comfortableledger.ledger.service.asset;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.DebtProfile;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.DebtProfileRepository;
import com.comfortableledger.ledger.repository.MemberRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DebtAutoDeductionService {
    private static final String INTEREST_CATEGORY_NAME = "대출이자";
    private static final String INTEREST_CATEGORY_ICON = "٪";
    private static final String INTEREST_CATEGORY_COLOR = "#B45F6A";

    private final DebtProfileRepository debtProfileRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;

    public DebtAutoDeductionService(DebtProfileRepository debtProfileRepository,
                                    CategoryRepository categoryRepository,
                                    MemberRepository memberRepository,
                                    TransactionRepository transactionRepository) {
        this.debtProfileRepository = debtProfileRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public RecurringGenerationResult executeDueDeductions(LocalDate today) {
        LocalDate targetDate = today == null ? LocalDate.now() : today;
        String targetMonth = YearMonth.from(targetDate).toString();
        int generatedCount = 0;
        for (DebtProfile profile : debtProfileRepository.findByAutoDeductTrue()) {
            if (!isDue(profile, targetDate, targetMonth)) {
                continue;
            }
            BigDecimal amount = monthlyInterestAmount(profile);
            Asset paymentAccount = profile.getPaymentAccount();
            if (amount.signum() <= 0 || paymentAccount == null || profile.getAsset().isHidden()) {
                profile.markDeducted(targetMonth);
                continue;
            }
            Category category = interestCategory(profile.getAsset());
            Member author = ownerMember(profile.getAsset());
            TransactionRecord record = new TransactionRecord(
                    profile.getAsset().getHousehold(),
                    author,
                    TransactionType.EXPENSE,
                    targetDate,
                    amount,
                    category,
                    paymentAccount,
                    null,
                    null,
                    profile.getAsset().getName() + " 이자 자동 차감",
                    "연 " + profile.getAnnualInterestRate().stripTrailingZeros().toPlainString() + "%",
                    null,
                    1
            );
            paymentAccount.changeBalance(amount.negate());
            transactionRepository.save(record);
            profile.markDeducted(targetMonth);
            generatedCount++;
        }
        return new RecurringGenerationResult(generatedCount);
    }

    private boolean isDue(DebtProfile profile, LocalDate targetDate, String targetMonth) {
        return profile.getPaymentDay() <= targetDate.getDayOfMonth()
                && !targetMonth.equals(profile.getLastDeductedMonth());
    }

    private BigDecimal monthlyInterestAmount(DebtProfile profile) {
        return profile.getAsset().getBalance().abs()
                .multiply(profile.getAnnualInterestRate())
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);
    }

    private Category interestCategory(Asset debtAsset) {
        return categoryRepository
                .findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                        debtAsset.getHousehold().getId(), CategoryType.EXPENSE)
                .stream()
                .filter(category -> INTEREST_CATEGORY_NAME.equals(category.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(
                        debtAsset.getHousehold(),
                        CategoryType.EXPENSE,
                        INTEREST_CATEGORY_NAME,
                        INTEREST_CATEGORY_ICON,
                        INTEREST_CATEGORY_COLOR,
                        categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                                debtAsset.getHousehold().getId(), CategoryType.EXPENSE).size()
                )));
    }

    private Member ownerMember(Asset debtAsset) {
        return memberRepository.findByHouseholdId(debtAsset.getHousehold().getId()).stream()
                .min(Comparator.comparing(member -> member.getRole() == MemberRole.OWNER ? 0 : 1))
                .orElseThrow();
    }
}
