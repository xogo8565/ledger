package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MemberRepository;
import com.comfortableledger.ledger.repo.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String ASSET_WORKBOOKS = "classpath*:initial-data/assets_*.xlsx";
    private static final String TRANSACTION_WORKBOOKS = "classpath*:initial-data/transactions_*.xlsx";
    private static final LocalDate EXCEL_DATE_EPOCH = LocalDate.of(1899, 12, 30);
    private static final String[] CATEGORY_COLORS = {
            "#609249", "#95CC5C", "#A6744A", "#CDA570", "#706A5C", "#4F7CAC", "#B45F6A", "#8C6BB1"
    };

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final ResourcePatternResolver resourcePatternResolver;

    public DemoDataInitializer(HouseholdRepository householdRepository,
                               MemberRepository memberRepository,
                               AssetRepository assetRepository,
                               CategoryRepository categoryRepository,
                               MonthlyBudgetRepository monthlyBudgetRepository,
                               TransactionRepository transactionRepository,
                               ResourcePatternResolver resourcePatternResolver) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
        this.transactionRepository = transactionRepository;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!isEmptyDatabase()) {
            return;
        }

        Resource[] assetWorkbooks = sortedResources(resourcePatternResolver.getResources(ASSET_WORKBOOKS));
        Resource[] transactionWorkbooks = sortedResources(resourcePatternResolver.getResources(TRANSACTION_WORKBOOKS));
        if (assetWorkbooks.length == 0 && transactionWorkbooks.length == 0) {
            log.warn("Initial data workbooks are missing. assetPattern={}, transactionPattern={}",
                    ASSET_WORKBOOKS, TRANSACTION_WORKBOOKS);
            return;
        }

        Household household = householdRepository.save(new Household("초기 가계부"));
        Member owner = memberRepository.save(new Member(household, "사용자", MemberRole.OWNER));

        Map<String, Asset> assetsByName = new HashMap<>();
        for (Resource assetWorkbook : assetWorkbooks) {
            seedAssets(household, assetsByName, InitialDataWorkbookReader.readRows(assetWorkbook));
        }

        Map<CategoryKey, Category> categoriesByKey = new HashMap<>();
        int transactionCount = 0;
        for (Resource transactionWorkbook : transactionWorkbooks) {
            transactionCount += seedTransactions(
                    household,
                    owner,
                    assetsByName,
                    categoriesByKey,
                    InitialDataWorkbookReader.readRows(transactionWorkbook)
            );
        }

        monthlyBudgetRepository.save(new MonthlyBudget(household, YearMonth.now().toString(), BigDecimal.ZERO));
        log.info("Seeded initial ledger data from workbooks. assetFiles={}, transactionFiles={}, assets={}, transactions={}, categories={}",
                assetWorkbooks.length, transactionWorkbooks.length, assetsByName.size(), transactionCount, categoriesByKey.size());
    }

    private boolean isEmptyDatabase() {
        return householdRepository.count() == 0
                && assetRepository.count() == 0
                && categoryRepository.count() == 0
                && transactionRepository.count() == 0;
    }

    private void seedAssets(Household household, Map<String, Asset> assetsByName, List<Map<String, String>> rows) {
        for (Map<String, String> row : skipHeader(rows)) {
            String name = firstNonBlank(row.get("B"), row.get("E"));
            if (name == null) {
                continue;
            }

            BigDecimal balance = parseAmount(firstNonBlank(row.get("F"), row.get("I")));
            assetsByName.computeIfAbsent(name,
                    currentName -> assetRepository.save(new Asset(
                            household,
                            inferAssetType(currentName),
                            currentName,
                            balance,
                            groupName(currentName)
                    )));
        }
    }

    private Resource[] sortedResources(Resource[] resources) {
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(resource -> firstNonBlank(resource.getFilename(), "")))
                .toArray(Resource[]::new);
    }

    private int seedTransactions(Household household,
                                 Member owner,
                                 Map<String, Asset> assetsByName,
                                 Map<CategoryKey, Category> categoriesByKey,
                                 List<Map<String, String>> rows) {
        int count = 0;
        for (Map<String, String> row : skipHeader(rows)) {
            LocalDate transactionDate = parseDate(row.get("A"));
            BigDecimal amount = parseAmount(firstNonBlank(row.get("F"), row.get("I")));
            if (transactionDate == null || amount.signum() <= 0) {
                continue;
            }

            TransactionType transactionType = parseTransactionType(row.get("G"));
            String categoryName = firstNonBlank(row.get("C"), row.get("D"), "기타");
            Category category = categoryFor(household, categoriesByKey, transactionType, categoryName);
            String assetName = row.get("B");
            Asset asset = assetFor(household, assetsByName, assetName);
            String title = firstNonBlank(row.get("E"), categoryName);
            String memo = row.get("H");

            transactionRepository.save(new TransactionRecord(
                    household,
                    owner,
                    transactionType,
                    transactionDate,
                    amount,
                    category,
                    asset,
                    null,
                    null,
                    title,
                    memo,
                    null,
                    1
            ));
            count++;
        }
        return count;
    }

    private Category categoryFor(Household household,
                                 Map<CategoryKey, Category> categoriesByKey,
                                 TransactionType transactionType,
                                 String categoryName) {
        CategoryType categoryType = transactionType == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        CategoryKey key = new CategoryKey(categoryType, categoryName);
        return categoriesByKey.computeIfAbsent(key, currentKey -> {
            int sortOrder = categoriesByKey.size();
            return categoryRepository.save(new Category(
                    household,
                    currentKey.type(),
                    currentKey.name(),
                    "•",
                    CATEGORY_COLORS[sortOrder % CATEGORY_COLORS.length],
                    sortOrder
            ));
        });
    }

    private Asset assetFor(Household household, Map<String, Asset> assetsByName, String assetName) {
        String normalizedAssetName = firstNonBlank(assetName, "미분류 자산");
        return assetsByName.computeIfAbsent(normalizedAssetName,
                name -> assetRepository.save(new Asset(household, inferAssetType(name), name, BigDecimal.ZERO, groupName(name))));
    }

    private List<Map<String, String>> skipHeader(List<Map<String, String>> rows) {
        if (rows.size() <= 1) {
            return List.of();
        }
        return rows.subList(1, rows.size());
    }

    private TransactionType parseTransactionType(String value) {
        if (value != null && value.contains("수입")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private AssetType inferAssetType(String name) {
        if (name.contains("카드")) {
            return AssetType.CARD;
        }
        if (name.contains("현금")) {
            return AssetType.CASH;
        }
        if (name.contains("대출") || name.contains("차입")) {
            return AssetType.DEBT;
        }
        return AssetType.BANK;
    }

    private String groupName(String name) {
        return switch (inferAssetType(name)) {
            case CARD -> "카드";
            case CASH -> "현금";
            case DEBT -> "부채";
            case BANK -> "계좌";
            case OTHER -> "기타";
        };
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.matches("\\d+(\\.\\d+)?")) {
            return EXCEL_DATE_EPOCH.plusDays((long) Math.floor(Double.parseDouble(normalizedValue)));
        }
        return LocalDate.parse(normalizedValue);
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalizedValue = value.replace(",", "").replace("원", "").trim();
        if (normalizedValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalizedValue);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record CategoryKey(CategoryType type, String name) {
    }
}
