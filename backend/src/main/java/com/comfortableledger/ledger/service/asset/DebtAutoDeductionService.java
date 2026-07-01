package com.comfortableledger.ledger.service.asset;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.DebtProfile;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.DebtDtos.DebtAutoDeductionOverviewDto;
import com.comfortableledger.ledger.dto.DebtDtos.DebtAutoDeductionStatusDto;
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
import java.util.List;
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

    @Transactional(readOnly = true)
    public DebtAutoDeductionOverviewDto deductionStatus(LocalDate today) {
        LocalDate targetDate = today == null ? LocalDate.now() : today;
        String targetMonth = YearMonth.from(targetDate).toString();
        List<DebtAutoDeductionStatusDto> items = debtProfileRepository.findAllByOrderByPaymentDayAscIdAsc().stream()
                .map(profile -> status(profile, targetDate, targetMonth))
                .toList();
        long executableCount = items.stream().filter(DebtAutoDeductionStatusDto::executable).count();
        return new DebtAutoDeductionOverviewDto(targetDate, items.size(), Math.toIntExact(executableCount), items);
    }

    private DebtAutoDeductionStatusDto status(DebtProfile profile, LocalDate targetDate, String targetMonth) {
        BigDecimal amount = monthlyInterestAmount(profile);
        Asset paymentAccount = profile.getPaymentAccount();
        boolean due = isDue(profile, targetDate, targetMonth);
        String status = deductionStatusText(profile, amount, paymentAccount, due, targetMonth);
        boolean executable = "EXECUTABLE".equals(status);
        return new DebtAutoDeductionStatusDto(
                profile.getId(),
                profile.getAsset().getId(),
                profile.getAsset().getName(),
                profile.getAsset().getBalance(),
                paymentAccount == null ? null : paymentAccount.getId(),
                paymentAccount == null ? null : paymentAccount.getName(),
                profile.getAnnualInterestRate(),
                profile.getPaymentDay(),
                profile.isAutoDeduct(),
                profile.getLastDeductedMonth(),
                amount,
                due,
                executable,
                status
        );
    }

    private String deductionStatusText(DebtProfile profile, BigDecimal amount, Asset paymentAccount, boolean due, String targetMonth) {
        if (!profile.isAutoDeduct()) {
            return "AUTO_DEDUCT_OFF";
        }
        if (targetMonth.equals(profile.getLastDeductedMonth())) {
            return "ALREADY_DEDUCTED";
        }
        if (!due) {
            return "NOT_DUE";
        }
        if (profile.getAsset().isHidden()) {
            return "DEBT_ASSET_HIDDEN";
        }
        if (paymentAccount == null) {
            return "PAYMENT_ACCOUNT_MISSING";
        }
        if (amount.signum() <= 0) {
            return "NO_INTEREST";
        }
        return "EXECUTABLE";
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
