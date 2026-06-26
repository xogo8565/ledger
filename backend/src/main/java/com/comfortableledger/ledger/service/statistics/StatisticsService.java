package com.comfortableledger.ledger.service.statistics;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryBudget;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MonthlyBudget;
import com.comfortableledger.ledger.domain.TransactionRecord;
import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.repository.AssetRepository;
import com.comfortableledger.ledger.repository.CategoryBudgetRepository;
import com.comfortableledger.ledger.repository.CategoryRepository;
import com.comfortableledger.ledger.repository.HouseholdRepository;
import com.comfortableledger.ledger.repository.MonthlyBudgetRepository;
import com.comfortableledger.ledger.repository.TransactionRepository;
import com.comfortableledger.ledger.dto.SummaryDtos.MonthlySummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.PeriodSummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.YearlyBudgetSummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.YearlySummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
    private final HouseholdRepository householdRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public StatisticsService(HouseholdRepository householdRepository, AssetRepository assetRepository,
                             CategoryRepository categoryRepository,
                             CategoryBudgetRepository categoryBudgetRepository,
                             TransactionRepository transactionRepository,
                             MonthlyBudgetRepository monthlyBudgetRepository) {
        this.householdRepository = householdRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.categoryBudgetRepository = categoryBudgetRepository;
        this.transactionRepository = transactionRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryDto monthlySummary(String month) {
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        List<TransactionRecord> records = monthlyRecords(yearMonth);
        BigDecimal income = sum(records, TransactionType.INCOME);
        BigDecimal expense = sum(records, TransactionType.EXPENSE);
        BigDecimal transfer = sum(records, TransactionType.TRANSFER);
        List<Asset> assets = assetRepository
                .findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(defaultHousehold().getId());
        BigDecimal assetTotal = assets.stream()
                .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                .map(Asset::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabilityTotal = assets.stream()
                .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                .map(Asset::getBalance).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);

        Household household = defaultHousehold();
        MonthlyBudget monthlyBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), yearMonth.toString()).orElse(null);
        BigDecimal budget = monthlyBudget == null ? BigDecimal.ZERO : monthlyBudget.getTotalAmount();
        return new MonthlySummaryDto(
                yearMonth.toString(), income, expense, transfer, assetTotal, liabilityTotal,
                assetTotal.subtract(liabilityTotal), budget, budget.subtract(expense),
                usageRate(expense, budget), categorySpends(records), tagSpends(records),
                scopeSpends(records), memberSpends(records),
                categoryBudgetUsages(household, monthlyBudget, records), weeklyTotals(yearMonth, records));
    }

    @Transactional(readOnly = true)
    public YearlySummaryDto yearlySummary(Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        List<TransactionRecord> records = records(
                LocalDate.of(targetYear, 1, 1), LocalDate.of(targetYear, 12, 31));
        List<YearlySummaryDto.MonthlyTotals> monthlyTotals = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(monthNumber -> {
                    YearMonth yearMonth = YearMonth.of(targetYear, monthNumber);
                    List<TransactionRecord> monthRecords = records.stream()
                            .filter(record -> YearMonth.from(record.getTransactionDate()).equals(yearMonth)).toList();
                    return new YearlySummaryDto.MonthlyTotals(
                            yearMonth.toString(), sum(monthRecords, TransactionType.INCOME),
                            sum(monthRecords, TransactionType.EXPENSE),
                            sum(monthRecords, TransactionType.TRANSFER));
                }).toList();
        return new YearlySummaryDto(
                targetYear, sum(records, TransactionType.INCOME), sum(records, TransactionType.EXPENSE),
                sum(records, TransactionType.TRANSFER), monthlyTotals, categorySpends(records),
                tagSpends(records), scopeSpends(records), memberSpends(records));
    }

    @Transactional(readOnly = true)
    public YearlyBudgetSummaryDto yearlyBudgetSummary(Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        Household household = defaultHousehold();
        List<TransactionRecord> records = records(
                LocalDate.of(targetYear, 1, 1), LocalDate.of(targetYear, 12, 31));
        Map<String, BigDecimal> budgets = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonthBetweenOrderByBudgetMonthAsc(
                        household.getId(), YearMonth.of(targetYear, 1).toString(),
                        YearMonth.of(targetYear, 12).toString())
                .stream().collect(Collectors.toMap(MonthlyBudget::getBudgetMonth, MonthlyBudget::getTotalAmount));
        List<YearlyBudgetSummaryDto.MonthlyBudgetUsage> monthlyUsages =
                java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(monthNumber -> {
                    YearMonth yearMonth = YearMonth.of(targetYear, monthNumber);
                    BigDecimal monthlyBudget = budgets.getOrDefault(yearMonth.toString(), BigDecimal.ZERO);
                    BigDecimal monthlyExpense = sum(records.stream()
                            .filter(record -> YearMonth.from(record.getTransactionDate()).equals(yearMonth)).toList(),
                            TransactionType.EXPENSE);
                    return new YearlyBudgetSummaryDto.MonthlyBudgetUsage(
                            yearMonth.toString(), monthlyBudget, monthlyExpense,
                            monthlyBudget.subtract(monthlyExpense), usageRate(monthlyExpense, monthlyBudget),
                            monthlyBudget.signum() > 0 && monthlyExpense.compareTo(monthlyBudget) > 0);
                }).toList();
        BigDecimal totalBudget = monthlyUsages.stream()
                .map(YearlyBudgetSummaryDto.MonthlyBudgetUsage::budget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = monthlyUsages.stream()
                .map(YearlyBudgetSummaryDto.MonthlyBudgetUsage::expense).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new YearlyBudgetSummaryDto(
                targetYear, totalBudget, totalExpense, totalBudget.subtract(totalExpense),
                usageRate(totalExpense, totalBudget), monthlyUsages);
    }

    @Transactional(readOnly = true)
    public PeriodSummaryDto rangeSummary(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        return periodSummary(startDate, endDate, records(startDate, endDate));
    }

    static PeriodSummaryDto periodSummary(
            LocalDate startDate, LocalDate endDate, List<TransactionRecord> records) {
        return new PeriodSummaryDto(
                startDate + " ~ " + endDate, startDate, endDate,
                sum(records, TransactionType.INCOME), sum(records, TransactionType.EXPENSE),
                sum(records, TransactionType.TRANSFER), categorySpends(records), tagSpends(records),
                scopeSpends(records), memberSpends(records));
    }

    static List<MonthlySummaryDto.WeeklyTotals> weeklyTotals(
            YearMonth yearMonth, List<TransactionRecord> records) {
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        List<MonthlySummaryDto.WeeklyTotals> totals = new java.util.ArrayList<>();
        LocalDate weekStart = yearMonth.atDay(1);
        int weekIndex = 1;
        while (!weekStart.isAfter(monthEnd)) {
            LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            if (weekEnd.isAfter(monthEnd)) weekEnd = monthEnd;
            LocalDate currentStart = weekStart;
            LocalDate currentEnd = weekEnd;
            List<TransactionRecord> weekRecords = records.stream()
                    .filter(record -> !record.getTransactionDate().isBefore(currentStart))
                    .filter(record -> !record.getTransactionDate().isAfter(currentEnd)).toList();
            totals.add(new MonthlySummaryDto.WeeklyTotals(
                    weekIndex, currentStart, currentEnd, sum(weekRecords, TransactionType.EXPENSE),
                    weekRecords.stream().filter(record -> record.getType() == TransactionType.EXPENSE).count()));
            weekStart = weekEnd.plusDays(1);
            weekIndex++;
        }
        return totals;
    }

    static List<MonthlySummaryDto.CategorySpend> categorySpends(List<TransactionRecord> records) {
        Map<Category, BigDecimal> totals = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getCategory,
                        Collectors.mapping(TransactionRecord::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        return totals.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new MonthlySummaryDto.CategorySpend(
                        entry.getKey().getId(), entry.getKey().getName(), entry.getValue())).toList();
    }

    static List<MonthlySummaryDto.TagSpend> tagSpends(List<TransactionRecord> records) {
        Map<String, TagSpendAccumulator> totals = new LinkedHashMap<>();
        records.stream().filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getSpendingTag() != null && !record.getSpendingTag().isBlank())
                .forEach(record -> java.util.Arrays.stream(record.getSpendingTag().split("[,竊?"))
                        .map(String::trim).map(tag -> tag.startsWith("#") ? tag.substring(1).trim() : tag)
                        .filter(tag -> !tag.isBlank()).map(tag -> tag.replaceAll("\\s+", " ")).distinct()
                        .forEach(tag -> totals.computeIfAbsent(
                                tag.toLowerCase(java.util.Locale.ROOT), ignored -> new TagSpendAccumulator(tag))
                                .add(record.getAmount())));
        return totals.values().stream()
                .map(item -> new MonthlySummaryDto.TagSpend(
                        item.tagName, item.amount, item.transactionCount))
                .sorted(Comparator.comparing(MonthlySummaryDto.TagSpend::amount).reversed()
                        .thenComparing(MonthlySummaryDto.TagSpend::transactionCount, Comparator.reverseOrder())
                        .thenComparing(MonthlySummaryDto.TagSpend::tagName)).toList();
    }

    static List<MonthlySummaryDto.ScopeSpend> scopeSpends(List<TransactionRecord> records) {
        return records.stream().filter(record -> record.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getConsumptionScope,
                        Collectors.collectingAndThen(Collectors.toList(), grouped ->
                                new MonthlySummaryDto.ScopeSpend(
                                        grouped.getFirst().getConsumptionScope(),
                                        grouped.stream().map(TransactionRecord::getAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                                        grouped.size()))))
                .values().stream()
                .sorted(Comparator.comparing(MonthlySummaryDto.ScopeSpend::amount).reversed()
                        .thenComparing(item -> item.scope().name())).toList();
    }

    static List<MonthlySummaryDto.MemberSpend> memberSpends(List<TransactionRecord> records) {
        Map<MemberSpendKey, MemberSpendAccumulator> totals = new LinkedHashMap<>();
        records.stream().filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getConsumptionScope() == ConsumptionScope.PERSONAL)
                .forEach(record -> {
                    Member consumer = record.getConsumer();
                    MemberSpendKey key = consumer == null
                            ? new MemberSpendKey(null, "명의 미지정")
                            : new MemberSpendKey(consumer.getId(), consumer.getName());
                    totals.computeIfAbsent(key, ignored -> new MemberSpendAccumulator(key))
                            .add(record.getAmount());
                });
        return totals.values().stream()
                .map(item -> new MonthlySummaryDto.MemberSpend(
                        item.key.memberId, item.key.memberName, item.amount, item.transactionCount))
                .sorted(Comparator.comparing(MonthlySummaryDto.MemberSpend::amount).reversed()
                        .thenComparing(MonthlySummaryDto.MemberSpend::transactionCount, Comparator.reverseOrder())
                        .thenComparing(item -> item.memberName() == null ? "" : item.memberName())).toList();
    }

    private List<MonthlySummaryDto.CategoryBudgetUsage> categoryBudgetUsages(
            Household household, MonthlyBudget monthlyBudget, List<TransactionRecord> records) {
        Map<Long, BigDecimal> spentByCategoryId = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getCategory().getId(),
                        Collectors.mapping(TransactionRecord::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<Long, BigDecimal> budgetByCategoryId = monthlyBudget == null ? Map.of()
                : categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), CategoryBudget::getAmount));
        return categoryRepository.findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                        household.getId(), CategoryType.EXPENSE).stream()
                .map(category -> categoryBudgetUsage(category, budgetByCategoryId, spentByCategoryId))
                .filter(item -> item.budgetAmount().signum() > 0 || item.spentAmount().signum() > 0)
                .sorted(Comparator.comparing(MonthlySummaryDto.CategoryBudgetUsage::exceeded).reversed()
                        .thenComparing(MonthlySummaryDto.CategoryBudgetUsage::usageRate, Comparator.reverseOrder())
                        .thenComparing(MonthlySummaryDto.CategoryBudgetUsage::spentAmount, Comparator.reverseOrder()))
                .toList();
    }

    private MonthlySummaryDto.CategoryBudgetUsage categoryBudgetUsage(
            Category category, Map<Long, BigDecimal> budgets, Map<Long, BigDecimal> spent) {
        BigDecimal budget = budgets.getOrDefault(category.getId(), BigDecimal.ZERO);
        BigDecimal expense = spent.getOrDefault(category.getId(), BigDecimal.ZERO);
        return new MonthlySummaryDto.CategoryBudgetUsage(
                category.getId(), category.getName(), category.getIcon(), budget, expense,
                budget.subtract(expense), usageRate(expense, budget),
                budget.signum() > 0 && expense.compareTo(budget) > 0);
    }

    private static BigDecimal usageRate(BigDecimal spent, BigDecimal budget) {
        return budget.signum() == 0 ? BigDecimal.ZERO
                : spent.multiply(new BigDecimal("100")).divide(budget, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<TransactionRecord> records, TransactionType type) {
        return records.stream().filter(record -> record.getType() == type)
                .map(TransactionRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TransactionRecord> monthlyRecords(YearMonth month) {
        return records(month.atDay(1), month.atEndOfMonth());
    }

    private List<TransactionRecord> records(LocalDate start, LocalDate end) {
        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(), start, end);
    }

    private Household defaultHousehold() {
        return householdRepository.findFirstByOrderByIdAsc().orElseThrow();
    }

    private record MemberSpendKey(Long memberId, String memberName) {
    }

    private static class MemberSpendAccumulator {
        private final MemberSpendKey key;
        private BigDecimal amount = BigDecimal.ZERO;
        private long transactionCount;
        private MemberSpendAccumulator(MemberSpendKey key) { this.key = key; }
        private void add(BigDecimal value) { amount = amount.add(value); transactionCount++; }
    }

    private static class TagSpendAccumulator {
        private final String tagName;
        private BigDecimal amount = BigDecimal.ZERO;
        private long transactionCount;
        private TagSpendAccumulator(String tagName) { this.tagName = tagName; }
        private void add(BigDecimal value) { amount = amount.add(value); transactionCount++; }
    }
}
