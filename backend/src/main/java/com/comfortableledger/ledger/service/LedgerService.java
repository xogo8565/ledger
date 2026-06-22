package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryBudget;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repo.AssetRepository;
import com.comfortableledger.ledger.repo.CardProfileRepository;
import com.comfortableledger.ledger.repo.CategoryBudgetRepository;
import com.comfortableledger.ledger.repo.CategoryRepository;
import com.comfortableledger.ledger.repo.HouseholdRepository;
import com.comfortableledger.ledger.repo.MemberRepository;
import com.comfortableledger.ledger.repo.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repo.TransactionRepository;
import com.comfortableledger.ledger.web.ApiDtos.AssetDto;
import com.comfortableledger.ledger.web.ApiDtos.AssetSummaryDto;
import com.comfortableledger.ledger.web.ApiDtos.BudgetSettingsDto;
import com.comfortableledger.ledger.web.ApiDtos.CategoryBudgetDto;
import com.comfortableledger.ledger.web.ApiDtos.CategoryDto;
import com.comfortableledger.ledger.web.ApiDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.web.ApiDtos.MonthlySummaryDto;
import com.comfortableledger.ledger.web.ApiDtos.SaveAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveBudgetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCardAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCategoryRequest;
import com.comfortableledger.ledger.web.ApiDtos.TransactionDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final CardProfileRepository cardProfileRepository;

    public LedgerService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                         AssetRepository assetRepository, CategoryRepository categoryRepository,
                         CategoryBudgetRepository categoryBudgetRepository, TransactionRepository transactionRepository,
                         MonthlyBudgetRepository monthlyBudgetRepository, CardProfileRepository cardProfileRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.transactionRepository = transactionRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
        this.cardProfileRepository = cardProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetDto> assets() {
        return assetRepository.findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId())
                .stream()
                .map(AssetDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetSummaryDto assetSummary() {
        List<Asset> assets = assetRepository.findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId());
        BigDecimal totalAssets = assets.stream()
                .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                .map(Asset::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = assets.stream()
                .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                .map(Asset::getBalance)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);
        return new AssetSummaryDto(totalAssets, totalLiabilities, netWorth);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categoryRepository.findByHouseholdIdAndActiveTrueOrderBySortOrderAscIdAsc(defaultHousehold().getId())
                .stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(String month) {
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return monthlyRecords(yearMonth).stream().map(TransactionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(Long id) {
        TransactionRecord record = transactionRepository.findById(id).orElseThrow();
        return TransactionDto.from(record);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> dailyTransactions(String date) {
        LocalDate targetDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return transactionRepository.findByHouseholdIdAndTransactionDateOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(),
                targetDate
        ).stream().map(TransactionDto::from).toList();
    }

    @Transactional
    public AssetDto createAsset(SaveAssetRequest request) {
        Household household = defaultHousehold();
        Asset asset = new Asset(
                household,
                request.type(),
                request.name(),
                request.balance(),
                normalizedAssetGroup(request.type(), request.groupName())
        );
        asset.update(asset.getType(), asset.getName(), asset.getBalance(), asset.getGroupName(), request.memo());
        return AssetDto.from(assetRepository.save(asset));
    }

    @Transactional
    public AssetDto createCardAsset(SaveCardAssetRequest request) {
        Household household = defaultHousehold();
        Asset paymentAccount = assetRepository.findById(request.paymentAccountId()).orElseThrow();
        
        Asset cardAsset = new Asset(
                household,
                AssetType.CARD,
                request.name(),
                request.balance(),
                normalizedAssetGroup(AssetType.CARD, request.groupName())
        );
        cardAsset.update(cardAsset.getType(), cardAsset.getName(), cardAsset.getBalance(), 
                        cardAsset.getGroupName(), request.memo());
        assetRepository.save(cardAsset);
        
        // CardProfile 생성
        CardProfile cardProfile = new CardProfile(
                cardAsset,
                paymentAccount,
                request.statementClosingDay(),
                request.paymentDay(),
                request.autoPayment()
        );
        cardProfileRepository.save(cardProfile);
        
        return AssetDto.from(cardAsset);
    }

    @Transactional
    public AssetDto updateAsset(Long id, SaveAssetRequest request) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        asset.update(
                request.type(),
                request.name(),
                request.balance(),
                normalizedAssetGroup(request.type(), request.groupName()),
                request.memo()
        );
        return AssetDto.from(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        assetRepository.findById(id).orElseThrow().hide();
    }

    @Transactional
    public CategoryDto createCategory(SaveCategoryRequest request) {
        Household household = defaultHousehold();
        int sortOrder = categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                household.getId(), request.type()).size();
        Category category = new Category(
                household,
                request.type(),
                request.name(),
                normalizedCategoryIcon(request.type(), request.icon()),
                request.color() == null || request.color().isBlank() ? "#ff625c" : request.color(),
                sortOrder
        );
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, SaveCategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow();
        category.update(
                request.name(),
                normalizedCategoryIcon(request.type(), request.icon()),
                request.color() == null || request.color().isBlank() ? "#ff625c" : request.color()
        );
        return CategoryDto.from(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.findById(id).orElseThrow().deactivate();
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
        Household household = defaultHousehold();
        Member author = memberRepository.findByHouseholdId(household.getId()).stream().findFirst().orElseThrow();
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId()).orElseThrow();
        Asset asset = request.assetId() == null ? null : assetRepository.findById(request.assetId()).orElseThrow();
        Asset fromAsset = request.fromAssetId() == null ? null : assetRepository.findById(request.fromAssetId()).orElseThrow();
        Asset toAsset = request.toAssetId() == null ? null : assetRepository.findById(request.toAssetId()).orElseThrow();

        TransactionRecord record = new TransactionRecord(
                household,
                author,
                request.type(),
                request.transactionDate(),
                request.amount(),
                category,
                asset,
                fromAsset,
                toAsset,
                request.title(),
                request.memo(),
                request.installmentMonths() == null ? 0 : request.installmentMonths()
        );
        applyAssetChange(record);
        return TransactionDto.from(transactionRepository.save(record));
    }

    @Transactional
    public TransactionDto updateTransaction(Long id, CreateTransactionRequest request) {
        TransactionRecord record = transactionRepository.findById(id).orElseThrow();
        
        // 기존 거래의 자산 변경사항을 반대로 처리 (원상복구)
        reverseAssetChange(record);
        
        // 새로운 정보로 거래 업데이트
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId()).orElseThrow();
        Asset asset = request.assetId() == null ? null : assetRepository.findById(request.assetId()).orElseThrow();
        Asset fromAsset = request.fromAssetId() == null ? null : assetRepository.findById(request.fromAssetId()).orElseThrow();
        Asset toAsset = request.toAssetId() == null ? null : assetRepository.findById(request.toAssetId()).orElseThrow();

        record.update(
                request.type(),
                request.transactionDate(),
                request.amount(),
                category,
                asset,
                fromAsset,
                toAsset,
                request.title(),
                request.memo(),
                request.installmentMonths() == null ? 0 : request.installmentMonths()
        );
        
        // 새로운 거래의 자산 변경사항 적용
        applyAssetChange(record);
        
        return TransactionDto.from(transactionRepository.save(record));
    }

    @Transactional
    public void deleteTransaction(Long id) {
        TransactionRecord record = transactionRepository.findById(id).orElseThrow();
        
        // 거래의 자산 변경사항을 반대로 처리 (원상복구)
        reverseAssetChange(record);
        
        // 거래 삭제
        transactionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public MonthlySummaryDto monthlySummary(String month) {
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        List<TransactionRecord> records = monthlyRecords(yearMonth);
        BigDecimal income = sum(records, TransactionType.INCOME);
        BigDecimal expense = sum(records, TransactionType.EXPENSE);
        BigDecimal transfer = sum(records, TransactionType.TRANSFER);

        List<Asset> assets = assetRepository.findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId());
        BigDecimal assetTotal = assets.stream()
                .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                .map(Asset::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabilityTotal = assets.stream()
                .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                .map(Asset::getBalance)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(record -> record.getCategory().getName(),
                        Collectors.mapping(TransactionRecord::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        BigDecimal budget = monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(defaultHousehold().getId(), yearMonth.toString())
                .map(MonthlyBudget::getTotalAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal budgetUsageRate = budget.signum() == 0
                ? BigDecimal.ZERO
                : expense.multiply(new BigDecimal("100")).divide(budget, 1, RoundingMode.HALF_UP);

        return new MonthlySummaryDto(
                yearMonth.toString(),
                income,
                expense,
                transfer,
                assetTotal,
                liabilityTotal,
                assetTotal.subtract(liabilityTotal),
                budget,
                budget.subtract(expense),
                budgetUsageRate,
                byCategory.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                        .map(entry -> new MonthlySummaryDto.CategorySpend(entry.getKey(), entry.getValue()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public BudgetSettingsDto budgetSettings(String month) {
        Household household = defaultHousehold();
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        MonthlyBudget monthlyBudget = monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(household.getId(), yearMonth.toString())
                .orElse(null);
        Map<Long, CategoryBudget> existing = monthlyBudget == null
                ? Map.of()
                : categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));

        List<CategoryBudgetDto> categories = categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                        household.getId(), CategoryType.EXPENSE)
                .stream()
                .map(category -> {
                    CategoryBudget budget = existing.get(category.getId());
                    return budget == null
                            ? CategoryBudgetDto.empty(category)
                            : new CategoryBudgetDto(category.getId(), category.getName(), category.getIcon(), budget.getAmount());
                })
                .toList();

        return new BudgetSettingsDto(
                yearMonth.toString(),
                monthlyBudget == null ? BigDecimal.ZERO : monthlyBudget.getTotalAmount(),
                categories
        );
    }

    @Transactional
    public BudgetSettingsDto saveBudget(SaveBudgetRequest request) {
        Household household = defaultHousehold();
        String budgetMonth = request.month() == null || request.month().isBlank() ? YearMonth.now().toString() : request.month();
        MonthlyBudget monthlyBudget = monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(household.getId(), budgetMonth)
                .orElseGet(() -> monthlyBudgetRepository.save(new MonthlyBudget(household, budgetMonth, BigDecimal.ZERO)));
        monthlyBudget.updateTotalAmount(request.totalAmount());

        Map<Long, CategoryBudget> existing = categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));
        if (request.categories() != null) {
            for (SaveBudgetRequest.SaveCategoryBudget item : request.categories()) {
                Category category = categoryRepository.findById(item.categoryId()).orElseThrow();
                CategoryBudget categoryBudget = existing.get(category.getId());
                if (categoryBudget == null) {
                    categoryBudgetRepository.save(new CategoryBudget(monthlyBudget, category, item.amount()));
                } else {
                    categoryBudget.updateAmount(item.amount());
                }
            }
        }

        return budgetSettings(budgetMonth);
    }

    private BigDecimal sum(List<TransactionRecord> records, TransactionType type) {
        return records.stream()
                .filter(record -> record.getType() == type)
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TransactionRecord> monthlyRecords(YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(),
                start,
                end
        );
    }

    private void applyAssetChange(TransactionRecord record) {
        if (record.getType() == TransactionType.INCOME && record.getAsset() != null) {
            record.getAsset().changeBalance(record.getAmount());
        }
        if (record.getType() == TransactionType.EXPENSE && record.getAsset() != null) {
            // 카드 자산은 balance 변경 없음 (카드 사용액은 별도 추적)
            if (record.getAsset().getType() != AssetType.CARD) {
                record.getAsset().changeBalance(record.getAmount().negate());
            }
        }
        if (record.getType() == TransactionType.TRANSFER) {
            if (record.getFromAsset() != null) {
                record.getFromAsset().changeBalance(record.getAmount().negate());
            }
            if (record.getToAsset() != null) {
                record.getToAsset().changeBalance(record.getAmount());
            }
        }
    }

    private void reverseAssetChange(TransactionRecord record) {
        if (record.getType() == TransactionType.INCOME && record.getAsset() != null) {
            record.getAsset().changeBalance(record.getAmount().negate());
        }
        if (record.getType() == TransactionType.EXPENSE && record.getAsset() != null) {
            // 카드 자산은 balance 변경이 없었으므로 반대 처리도 없음
            if (record.getAsset().getType() != AssetType.CARD) {
                record.getAsset().changeBalance(record.getAmount());
            }
        }
        if (record.getType() == TransactionType.TRANSFER) {
            if (record.getFromAsset() != null) {
                record.getFromAsset().changeBalance(record.getAmount());
            }
            if (record.getToAsset() != null) {
                record.getToAsset().changeBalance(record.getAmount().negate());
            }
        }
    }

    private String normalizedAssetGroup(AssetType type, String groupName) {
        if (groupName != null && !groupName.isBlank()) {
            return groupName;
        }
        return switch (type) {
            case CASH -> "현금";
            case BANK -> "은행";
            case CARD -> "카드";
            case OTHER -> "기타";
            case DEBT -> "부채";
        };
    }

    private String normalizedCategoryIcon(CategoryType type, String icon) {
        if (icon != null && !icon.isBlank()) {
            return icon;
        }
        return type == CategoryType.INCOME ? "💰" : "•";
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }
}
