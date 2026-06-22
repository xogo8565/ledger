package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CardProfileRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MemberRepository;
import com.comfortableledger.ledger.repo.MonthlyBudgetRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CardProfileRepository cardProfileRepository;
    private final CategoryRepository categoryRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public DemoDataInitializer(HouseholdRepository householdRepository, MemberRepository memberRepository,
                               AssetRepository assetRepository, CardProfileRepository cardProfileRepository,
                               CategoryRepository categoryRepository, MonthlyBudgetRepository monthlyBudgetRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.cardProfileRepository = cardProfileRepository;
        this.categoryRepository = categoryRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (householdRepository.count() > 0) {
            return;
        }

        Household household = householdRepository.save(new Household("우리집 가계부"));
        memberRepository.save(new Member(household, "나", MemberRole.OWNER));

        Asset cash = assetRepository.save(new Asset(household, AssetType.CASH, "현금", new BigDecimal("300000"), "현금"));
        Asset bank = assetRepository.save(new Asset(household, AssetType.BANK, "생활비 계좌", new BigDecimal("2500000"), "은행"));
        Asset card = assetRepository.save(new Asset(household, AssetType.CARD, "주사용 카드", BigDecimal.ZERO, "카드"));
        cardProfileRepository.save(new CardProfile(card, bank, 1, 1, false));

        String[][] expenseCategories = {
                {"식비", "🍜", "#95CC5C"}, {"교통/차량", "🚕", "#A6744A"}, {"마트/편의점", "🛒", "#CDA570"},
                {"문화생활", "🖼", "#609249"}, {"패션/미용", "🧥", "#95CC5C"}, {"생활용품", "🪑", "#A6744A"},
                {"주거/통신", "🏠", "#CDA570"}, {"건강", "🧘", "#609249"}, {"기타", "⋯", "#706A5C"}
        };
        for (int i = 0; i < expenseCategories.length; i++) {
            categoryRepository.save(new Category(household, CategoryType.EXPENSE, expenseCategories[i][0],
                    expenseCategories[i][1], expenseCategories[i][2], i));
        }

        String[][] incomeCategories = {
                {"급여", "💼", "#609249"}, {"부수입", "＋", "#95CC5C"}, {"이자", "％", "#CDA570"}, {"기타", "⋯", "#706A5C"}
        };
        for (int i = 0; i < incomeCategories.length; i++) {
            categoryRepository.save(new Category(household, CategoryType.INCOME, incomeCategories[i][0],
                    incomeCategories[i][1], incomeCategories[i][2], i));
        }

        monthlyBudgetRepository.save(new MonthlyBudget(household, YearMonth.now().toString(), new BigDecimal("1800000")));
        cash.changeBalance(BigDecimal.ZERO);
    }
}
