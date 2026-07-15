package com.comfortableledger.ledger.service.importing;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.InitialDataImport;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CardProfileRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.InitialDataImportRepository;
import com.comfortableledger.ledger.repository.MemberRepository;
import com.comfortableledger.ledger.repository.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.util.NumberValues;
import com.comfortableledger.ledger.util.StringValues;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
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
    private static final String RESOURCE_KIND_ASSET = "ASSET";
    private static final String RESOURCE_KIND_TRANSACTION = "TRANSACTION";
    private static final String ASSET_WORKBOOKS = "classpath*:initial-data/assets_*.xlsx";
    private static final String TRANSACTION_WORKBOOKS = "classpath*:initial-data/transactions_*.xlsx";
    private static final String PLAN_ASSET_WORKBOOK = "assets_plan_20260629.xlsx";
    private static final String LEGACY_CONVENIENCE_CATEGORY_NAME = "마트/편의점";
    private static final String CONVENIENCE_CATEGORY_NAME = "편의점";
    private static final String DEFAULT_OWNER_NAME = "석수";
    private static final String SECONDARY_OWNER_NAME = "유진";
    private static final LocalDate EXCEL_DATE_EPOCH = LocalDate.of(1899, 12, 30);
    private static final String[] CATEGORY_COLORS = {
            "#609249", "#95CC5C", "#A6744A", "#CDA570", "#706A5C", "#4F7CAC", "#B45F6A", "#8C6BB1"
    };

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CardProfileRepository cardProfileRepository;
    private final CategoryRepository categoryRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final InitialDataImportRepository initialDataImportRepository;
    private final ResourcePatternResolver resourcePatternResolver;

    public DemoDataInitializer(HouseholdRepository householdRepository,
                               MemberRepository memberRepository,
                               AssetRepository assetRepository,
                               CardProfileRepository cardProfileRepository,
                               CategoryRepository categoryRepository,
                               MonthlyBudgetRepository monthlyBudgetRepository,
                               TransactionRepository transactionRepository,
                               InitialDataImportRepository initialDataImportRepository,
                               ResourcePatternResolver resourcePatternResolver) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.cardProfileRepository = cardProfileRepository;
        this.categoryRepository = categoryRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
        this.transactionRepository = transactionRepository;
        this.initialDataImportRepository = initialDataImportRepository;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Resource[] assetWorkbooks = sortedResources(resourcePatternResolver.getResources(ASSET_WORKBOOKS));
        Resource[] transactionWorkbooks = sortedResources(resourcePatternResolver.getResources(TRANSACTION_WORKBOOKS));
        if (assetWorkbooks.length == 0 && transactionWorkbooks.length == 0) {
            log.warn("Initial data workbooks are missing. assetPattern={}, transactionPattern={}",
                    ASSET_WORKBOOKS, TRANSACTION_WORKBOOKS);
            return;
        }

        Household household = household();
        Member owner = ensureMember(household, DEFAULT_OWNER_NAME, MemberRole.OWNER);
        ensureMember(household, SECONDARY_OWNER_NAME, MemberRole.EDITOR);

        Map<String, Asset> assetsByName = new HashMap<>();
        int assetRowCount = 0;
        int assetFileCount = 0;
        for (Resource assetWorkbook : assetWorkbooks) {
            if (!needsImport(RESOURCE_KIND_ASSET, assetWorkbook)) {
                continue;
            }
            List<Map<String, String>> rows = InitialDataWorkbookReader.readRows(assetWorkbook);
            assetRowCount += seedAssets(household, assetsByName, rows, isPlanAssetWorkbook(assetWorkbook));
            markImported(RESOURCE_KIND_ASSET, assetWorkbook, rows.size());
            assetFileCount++;
        }

        Map<CategoryKey, Category> categoriesByKey = existingCategories(household);
        int transactionCount = 0;
        int transactionFileCount = 0;
        for (Resource transactionWorkbook : transactionWorkbooks) {
            if (!needsImport(RESOURCE_KIND_TRANSACTION, transactionWorkbook)) {
                continue;
            }
            List<Map<String, String>> rows = InitialDataWorkbookReader.readRows(transactionWorkbook);
            transactionCount += seedTransactions(
                    household,
                    owner,
                    assetsByName,
                    categoriesByKey,
                    rows
            );
            markImported(RESOURCE_KIND_TRANSACTION, transactionWorkbook, rows.size());
            transactionFileCount++;
        }

        monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(household.getId(), YearMonth.now().toString())
                .orElseGet(() -> monthlyBudgetRepository.save(
                        new MonthlyBudget(household, YearMonth.now().toString(), BigDecimal.ZERO)
                ));
        log.info("Seeded changed initial data workbooks. assetFiles={}, transactionFiles={}, assetRows={}, transactions={}, categories={}",
                assetFileCount, transactionFileCount, assetRowCount, transactionCount, categoriesByKey.size());
    }

    private Household household() {
        return householdRepository.findAll().stream()
                .min(Comparator.comparing(Household::getId))
                .orElseGet(() -> householdRepository.save(new Household("초기 가계부")));
    }

    private Member ensureMember(Household household, String name, MemberRole role) {
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .filter(member -> member.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> memberRepository.save(new Member(household, name, role)));
    }

    private int seedAssets(Household household, Map<String, Asset> assetsByName, List<Map<String, String>> rows,
                           boolean planAssetWorkbook) {
        int count = 0;
        for (Map<String, String> row : skipHeader(rows)) {
            String name = StringValues.firstNonBlank(row.get("B"), row.get("E"));
            if (name == null) {
                continue;
            }

            String ownerName = ownerName(row.get("J"));
            AssetType assetType = inferAssetType(name);
            BigDecimal balance = assetBalance(row, assetType, planAssetWorkbook);
            Asset asset = assetsByName.get(name);
            if (asset == null) {
                asset = assetRepository.findByHouseholdIdAndNameIgnoreCase(household.getId(), name)
                        .orElseGet(() -> new Asset(household, assetType, name, balance, groupName(name)));
            }
            asset.update(assetType, name, balance, groupName(name), ownerName, asset.getMemo());
            Asset savedAsset = assetRepository.save(asset);
            if (planAssetWorkbook && savedAsset.getType() == AssetType.CARD) {
                ensurePlanCardProfile(savedAsset, row);
            }
            assetsByName.put(name, savedAsset);
            count++;
        }
        return count;
    }

    private BigDecimal assetBalance(Map<String, String> row, AssetType assetType, boolean planAssetWorkbook) {
        if (planAssetWorkbook && assetType == AssetType.CARD) {
            return BigDecimal.ZERO;
        }
        return NumberValues.parseWonAmount(StringValues.firstNonBlank(row.get("F"), row.get("I")));
    }

    private void ensurePlanCardProfile(Asset cardAsset, Map<String, String> row) {
        int statementClosingDay = parseDay(row.get("K"), "Card statement closing day");
        int paymentDay = parseDay(row.get("L"), "Card payment day");
        CardProfile cardProfile = cardAsset.getCardProfile();
        if (cardProfile == null) {
            cardProfile = new CardProfile(
                    cardAsset,
                    null,
                    statementClosingDay,
                    paymentDay,
                    false
            );
            cardProfileRepository.save(cardProfile);
            cardAsset.setCardProfile(cardProfile);
            return;
        }
        cardProfile.update(
                cardProfile.getPaymentAccount(),
                statementClosingDay,
                paymentDay,
                cardProfile.isAutoPayment()
        );
    }

    private int parseDay(String value, String fieldName) {
        BigDecimal parsed = NumberValues.parseWonAmount(value);
        int day = parsed.intValue();
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 31");
        }
        return day;
    }

    private Resource[] sortedResources(Resource[] resources) {
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(resource -> StringValues.firstNonBlank(resource.getFilename(), "")))
                .toArray(Resource[]::new);
    }

    private boolean needsImport(String resourceKind, Resource resource) throws IOException {
        String checksum = checksum(resource);
        return initialDataImportRepository.findByResourceKindAndResourceName(resourceKind, resourceName(resource))
                .map(history -> !history.getChecksum().equals(checksum))
                .orElse(true);
    }

    private void markImported(String resourceKind, Resource resource, int rowCount) throws IOException {
        String resourceName = resourceName(resource);
        String checksum = checksum(resource);
        InitialDataImport history = initialDataImportRepository
                .findByResourceKindAndResourceName(resourceKind, resourceName)
                .orElseGet(() -> new InitialDataImport(resourceName, resourceKind, checksum, rowCount));
        history.markImported(checksum, rowCount);
        initialDataImportRepository.save(history);
    }

    private String resourceName(Resource resource) {
        return StringValues.firstNonBlank(resource.getFilename(), resource.getDescription());
    }

    private boolean isPlanAssetWorkbook(Resource resource) {
        return PLAN_ASSET_WORKBOOK.equals(resourceName(resource));
    }

    private String checksum(Resource resource) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = resource.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private int seedTransactions(Household household,
                                 Member owner,
                                 Map<String, Asset> assetsByName,
                                 Map<CategoryKey, Category> categoriesByKey,
                                 List<Map<String, String>> rows) {
        int count = 0;
        for (Map<String, String> row : skipHeader(rows)) {
            LocalDate transactionDate = parseDate(row.get("A"));
            BigDecimal amount = NumberValues.parseWonAmount(StringValues.firstNonBlank(row.get("F"), row.get("I")));
            if (transactionDate == null || amount.signum() <= 0) {
                continue;
            }

            TransactionType transactionType = parseTransactionType(row.get("G"));
            String categoryName = normalizeCategoryName(StringValues.firstNonBlank(row.get("C"), row.get("D"), "기타"));
            Category category = categoryFor(household, categoriesByKey, transactionType, categoryName);
            String assetName = row.get("B");
            Asset asset = assetFor(household, assetsByName, assetName);
            String title = StringValues.firstNonBlank(row.get("E"), categoryName);
            String memo = row.get("H");
            if (transactionRepository.existsByHouseholdIdAndTransactionDateAndTypeAndAmountAndAssetIdAndTitle(
                    household.getId(),
                    transactionDate,
                    transactionType,
                    amount,
                    asset.getId(),
                    title
            )) {
                continue;
            }

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

    private Map<CategoryKey, Category> existingCategories(Household household) {
        Map<CategoryKey, Category> categoriesByKey = new HashMap<>();
        for (CategoryType categoryType : CategoryType.values()) {
            categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(household.getId(), categoryType)
                    .forEach(category -> {
                        String normalizedName = normalizeCategoryName(category.getName());
                        if (!category.getName().equals(normalizedName)) {
                            category.update(normalizedName, category.getIcon(), category.getColor());
                        }
                        categoriesByKey.put(new CategoryKey(category.getType(), normalizedName), category);
                    });
        }
        return categoriesByKey;
    }

    private String normalizeCategoryName(String categoryName) {
        String normalizedName = StringValues.normalizeWhitespace(categoryName).replaceAll("\\s*/\\s*", "/");
        if (LEGACY_CONVENIENCE_CATEGORY_NAME.equals(normalizedName)) {
            return CONVENIENCE_CATEGORY_NAME;
        }
        return StringValues.normalizeWhitespace(categoryName);
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
        String normalizedAssetName = StringValues.firstNonBlank(assetName, "미분류 자산");
        return assetsByName.computeIfAbsent(normalizedAssetName,
                name -> {
                    Asset existingAsset = assetRepository.findByHouseholdIdAndNameIgnoreCase(household.getId(), name)
                            .orElse(null);
                    if (existingAsset != null) {
                        return existingAsset;
                    }
                    AssetType assetType = inferAssetType(name);
                    Asset asset = new Asset(household, assetType, name, BigDecimal.ZERO, groupName(name));
                    asset.update(assetType, name, BigDecimal.ZERO, groupName(name), DEFAULT_OWNER_NAME, null);
                    return assetRepository.save(asset);
                });
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
        if (name.contains("대출") || name.contains("차입") || name.contains("상환")) {
            return AssetType.DEBT;
        }
        if (name.contains("카드")) {
            return AssetType.CARD;
        }
        if (name.contains("현금")) {
            return AssetType.CASH;
        }
        if (name.contains("증권") || name.contains("ISA") || name.contains("종합매매")
                || name.contains("집합투자") || name.contains("연금저축")) {
            return AssetType.OTHER;
        }
        return AssetType.BANK;
    }

    private String groupName(String name) {
        if (name.contains("증권") || name.contains("ISA") || name.contains("종합매매")
                || name.contains("집합투자") || name.contains("연금저축")) {
            return "증권";
        }
        return switch (inferAssetType(name)) {
            case CARD -> "카드";
            case CASH -> "현금";
            case DEBT -> "부채";
            case BANK -> "계좌";
            case OTHER -> "기타";
        };
    }

    private String ownerName(String value) {
        String ownerName = StringValues.firstNonBlank(value, DEFAULT_OWNER_NAME);
        if (SECONDARY_OWNER_NAME.equals(ownerName)) {
            return SECONDARY_OWNER_NAME;
        }
        return DEFAULT_OWNER_NAME;
    }

    private LocalDate parseDate(String value) {
        String normalizedValue = StringValues.trimToEmpty(value);
        if (normalizedValue.isBlank()) {
            return null;
        }
        if (normalizedValue.matches("\\d+(\\.\\d+)?")) {
            return EXCEL_DATE_EPOCH.plusDays((long) Math.floor(Double.parseDouble(normalizedValue)));
        }
        return LocalDate.parse(normalizedValue);
    }

    private record CategoryKey(CategoryType type, String name) {
    }
}
