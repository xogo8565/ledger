package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.CardProfile;
import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryBudget;
import com.comfortableledger.ledger.domain.CategoryType;
import com.comfortableledger.ledger.domain.Household;
import com.comfortableledger.ledger.domain.Member;
import com.comfortableledger.ledger.domain.MemberRole;
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
import com.comfortableledger.ledger.web.ApiDtos.MemberDto;
import com.comfortableledger.ledger.web.ApiDtos.SaveAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveBudgetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCardAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCategoryRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveMemberRequest;
import com.comfortableledger.ledger.web.ApiDtos.TransactionDto;
import com.comfortableledger.ledger.web.ApiDtos.YearlySummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        return summarizeAssets(assets);
    }

    static AssetSummaryDto summarizeAssets(List<Asset> assets) {
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
        Map<String, List<Asset>> byOwner = assets.stream()
                .collect(Collectors.groupingBy(
                        asset -> normalizedOwnerName(asset.getOwnerName()),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<AssetSummaryDto.OwnerAssetSummary> owners = byOwner.entrySet().stream()
                .map(entry -> {
                    BigDecimal ownerAssets = entry.getValue().stream()
                            .filter(asset -> asset.getType() != AssetType.CARD && asset.getType() != AssetType.DEBT)
                            .map(Asset::getBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal ownerLiabilities = entry.getValue().stream()
                            .filter(asset -> asset.getType() == AssetType.CARD || asset.getType() == AssetType.DEBT)
                            .map(Asset::getBalance)
                            .map(BigDecimal::abs)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AssetSummaryDto.OwnerAssetSummary(
                            entry.getKey(),
                            ownerAssets,
                            ownerLiabilities,
                            ownerAssets.subtract(ownerLiabilities),
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator
                        .comparing((AssetSummaryDto.OwnerAssetSummary item) -> "명의 미지정".equals(item.ownerName()))
                        .thenComparing(AssetSummaryDto.OwnerAssetSummary::netWorth, Comparator.reverseOrder())
                        .thenComparing(AssetSummaryDto.OwnerAssetSummary::ownerName))
                .toList();
        return new AssetSummaryDto(totalAssets, totalLiabilities, netWorth, owners);
    }

    private static String normalizedOwnerName(String ownerName) {
        if (ownerName == null || ownerName.isBlank()) {
            return "명의 미지정";
        }
        return ownerName.trim().replaceAll("\\s+", " ");
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categoryRepository.findByHouseholdIdAndActiveTrueOrderBySortOrderAscIdAsc(defaultHousehold().getId())
                .stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members() {
        return memberRepository.findByHouseholdId(defaultHousehold().getId()).stream()
                .sorted(Comparator
                        .comparing((Member member) -> member.getRole() != MemberRole.OWNER)
                        .thenComparing(Member::getName)
                        .thenComparing(Member::getId))
                .map(MemberDto::from)
                .toList();
    }

    @Transactional
    public MemberDto createMember(SaveMemberRequest request) {
        Household household = defaultHousehold();
        String name = normalizedMemberName(request.name());
        if (memberRepository.existsByHouseholdIdAndNameIgnoreCase(household.getId(), name)) {
            throw new IllegalArgumentException("Member name already exists");
        }
        return MemberDto.from(memberRepository.save(new Member(household, name, MemberRole.EDITOR)));
    }

    @Transactional
    public MemberDto updateMember(Long id, SaveMemberRequest request) {
        Member member = memberRepository.findById(id).orElseThrow();
        String name = normalizedMemberName(request.name());
        boolean duplicate = memberRepository.findByHouseholdId(member.getHousehold().getId()).stream()
                .anyMatch(item -> !item.getId().equals(id) && item.getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new IllegalArgumentException("Member name already exists");
        }
        String oldName = member.getName();
        member.rename(name);
        assetRepository.findAll().stream()
                .filter(asset -> asset.getHousehold().getId().equals(member.getHousehold().getId()))
                .filter(asset -> normalizedMemberNameOrEmpty(asset.getOwnerName()).equalsIgnoreCase(oldName))
                .forEach(asset -> asset.update(
                        asset.getType(),
                        asset.getName(),
                        asset.getBalance(),
                        asset.getGroupName(),
                        name,
                        asset.getMemo()
                ));
        return MemberDto.from(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepository.findById(id).orElseThrow();
        if (member.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("Owner member cannot be deleted");
        }
        boolean usedByAsset = assetRepository.findAll().stream()
                .filter(asset -> asset.getHousehold().getId().equals(member.getHousehold().getId()))
                .anyMatch(asset -> normalizedMemberNameOrEmpty(asset.getOwnerName()).equalsIgnoreCase(member.getName()));
        if (usedByAsset) {
            throw new IllegalArgumentException("Member is used by an asset");
        }
        if (transactionRepository.existsByConsumerId(member.getId())) {
            throw new IllegalArgumentException("Member is used by a personal expense");
        }
        memberRepository.delete(member);
    }

    private String normalizedMemberName(String name) {
        String normalized = normalizedMemberNameOrEmpty(name);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Member name is required");
        }
        return normalized;
    }

    private String normalizedMemberNameOrEmpty(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private String registeredOwnerName(Household household, String ownerName) {
        String normalized = normalizedMemberNameOrEmpty(ownerName);
        if (normalized.isBlank()) {
            return null;
        }
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .filter(member -> member.getName().equalsIgnoreCase(normalized))
                .map(Member::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Asset owner must be a registered member"));
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(String month) {
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return monthlyRecords(yearMonth).stream().map(TransactionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactionsBetween(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate;
        LocalDate end = endDate == null ? start : endDate;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        return transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(),
                start,
                end
        ).stream().map(TransactionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public String exportTransactionsCsv(String month, Integer year) {
        List<TransactionRecord> records;
        String period;
        if (year != null) {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            records = transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                    defaultHousehold().getId(),
                    start,
                    end
            );
            period = String.valueOf(year);
        } else {
            YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
            records = monthlyRecords(yearMonth);
            period = yearMonth.toString();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("기간,거래일,유형,금액,카테고리,소비태그,소비구분,소비명의,자산,출금자산,입금자산,제목,메모,할부회차,할부개월\n");
        records.stream()
                .sorted(Comparator.comparing(TransactionRecord::getTransactionDate).thenComparing(TransactionRecord::getId))
                .forEach(record -> csv.append(csvRow(
                        period,
                        record.getTransactionDate().toString(),
                        record.getType().name(),
                        record.getAmount().toPlainString(),
                        record.getCategory() == null ? "" : record.getCategory().getName(),
                        record.getSpendingTag(),
                        record.getConsumptionScope() == null ? "" : record.getConsumptionScope().name(),
                        record.getConsumer() == null ? "" : record.getConsumer().getName(),
                        record.getAsset() == null ? "" : record.getAsset().getName(),
                        record.getFromAsset() == null ? "" : record.getFromAsset().getName(),
                        record.getToAsset() == null ? "" : record.getToAsset().getName(),
                        record.getTitle(),
                        record.getMemo(),
                        record.getInstallmentIndex() == 0 ? "" : String.valueOf(record.getInstallmentIndex()),
                        record.getInstallmentMonths() == 0 ? "" : String.valueOf(record.getInstallmentMonths())
                )).append('\n'));
        return csv.toString();
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

    @Transactional(readOnly = true)
    public List<TransactionDto> installmentTransactions(String installmentGroupId) {
        return transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId)
                .stream()
                .map(TransactionDto::from)
                .toList();
    }

    @Transactional
    public AssetDto createAsset(SaveAssetRequest request) {
        Household household = defaultHousehold();
        String ownerName = registeredOwnerName(household, request.ownerName());
        Asset asset = new Asset(
                household,
                request.type(),
                request.name(),
                request.balance(),
                normalizedAssetGroup(request.type(), request.groupName())
        );
        asset.update(asset.getType(), asset.getName(), asset.getBalance(), asset.getGroupName(), ownerName, request.memo());
        return AssetDto.from(assetRepository.save(asset));
    }

    @Transactional
    public AssetDto createCardAsset(SaveCardAssetRequest request) {
        Household household = defaultHousehold();
        String ownerName = registeredOwnerName(household, request.ownerName());
        Asset paymentAccount = assetRepository.findById(request.paymentAccountId()).orElseThrow();
        
        Asset cardAsset = new Asset(
                household,
                AssetType.CARD,
                request.name(),
                request.balance(),
                normalizedAssetGroup(AssetType.CARD, request.groupName())
        );
        cardAsset.update(cardAsset.getType(), cardAsset.getName(), cardAsset.getBalance(),
                cardAsset.getGroupName(), ownerName, request.memo());
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
        cardAsset.setCardProfile(cardProfile);
        
        return AssetDto.from(cardAsset);
    }

    @Transactional
    public AssetDto updateAsset(Long id, SaveAssetRequest request) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        String ownerName = registeredOwnerName(asset.getHousehold(), request.ownerName());
        asset.update(
                request.type(),
                request.name(),
                request.balance(),
                normalizedAssetGroup(request.type(), request.groupName()),
                ownerName,
                request.memo()
        );
        return AssetDto.from(asset);
    }

    @Transactional
    public AssetDto updateCardAsset(Long id, SaveCardAssetRequest request) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        String ownerName = registeredOwnerName(asset.getHousehold(), request.ownerName());
        Asset paymentAccount = assetRepository.findById(request.paymentAccountId()).orElseThrow();

        asset.update(
                AssetType.CARD,
                request.name(),
                request.balance(),
                normalizedAssetGroup(AssetType.CARD, request.groupName()),
                ownerName,
                request.memo()
        );

        CardProfile cardProfile = asset.getCardProfile();
        if (cardProfile == null) {
            cardProfile = new CardProfile(
                    asset,
                    paymentAccount,
                    request.statementClosingDay(),
                    request.paymentDay(),
                    request.autoPayment()
            );
            cardProfileRepository.save(cardProfile);
            asset.setCardProfile(cardProfile);
        } else {
            cardProfile.update(
                    paymentAccount,
                    request.statementClosingDay(),
                    request.paymentDay(),
                    request.autoPayment()
            );
        }

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
        int installmentMonths = request.installmentMonths() == null ? 0 : request.installmentMonths();
        if (request.type() == TransactionType.EXPENSE && installmentMonths > 1) {
            return createInstallmentTransactions(request, installmentMonths);
        }
        return TransactionDto.from(createSingleTransaction(request, request.transactionDate(), request.amount(), 0, 0, null));
    }

    private TransactionDto createInstallmentTransactions(CreateTransactionRequest request, int installmentMonths) {
        String groupId = UUID.randomUUID().toString();
        BigDecimal baseAmount = request.amount().divide(BigDecimal.valueOf(installmentMonths), 2, RoundingMode.DOWN);
        BigDecimal remainingAmount = request.amount();
        TransactionRecord firstRecord = null;

        for (int index = 1; index <= installmentMonths; index++) {
            BigDecimal amount = index == installmentMonths ? remainingAmount : baseAmount;
            remainingAmount = remainingAmount.subtract(amount);
            TransactionRecord record = createSingleTransaction(
                    request,
                    request.transactionDate().plusMonths(index - 1L),
                    amount,
                    index,
                    installmentMonths,
                    groupId
            );
            if (firstRecord == null) {
                firstRecord = record;
            }
        }
        return TransactionDto.from(firstRecord);
    }

    private TransactionRecord createSingleTransaction(CreateTransactionRequest request, LocalDate transactionDate,
                                                      BigDecimal amount, int installmentIndex,
                                                      int installmentMonths, String installmentGroupId) {
        Household household = defaultHousehold();
        Member author = memberRepository.findByHouseholdId(household.getId()).stream().findFirst().orElseThrow();
        Member consumer = personalExpenseConsumer(household, request);
        Category category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId()).orElseThrow();
        Asset asset = request.assetId() == null ? null : assetRepository.findById(request.assetId()).orElseThrow();
        Asset fromAsset = request.fromAssetId() == null ? null : assetRepository.findById(request.fromAssetId()).orElseThrow();
        Asset toAsset = request.toAssetId() == null ? null : assetRepository.findById(request.toAssetId()).orElseThrow();

        TransactionRecord record = new TransactionRecord(
                household,
                author,
                request.type(),
                transactionDate,
                amount,
                category,
                asset,
                fromAsset,
                toAsset,
                request.title(),
                request.memo(),
                request.spendingTag(),
                request.consumptionScope(),
                consumer,
                installmentMonths
        );
        if (installmentGroupId != null) {
            record.assignInstallment(installmentGroupId, installmentIndex, installmentMonths);
        }
        applyAssetChange(record);
        return transactionRepository.save(record);
    }

    @Transactional
    public List<TransactionDto> updateInstallmentTransactions(String installmentGroupId, CreateTransactionRequest request) {
        List<TransactionRecord> records = transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Installment group not found");
        }
        if (request.type() != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Installment group must be an expense");
        }
        int installmentMonths = request.installmentMonths() == null ? records.size() : request.installmentMonths();
        if (installmentMonths <= 1) {
            throw new IllegalArgumentException("Installment month count must be greater than 1");
        }
        BigDecimal baseAmount = request.amount().divide(BigDecimal.valueOf(installmentMonths), 2, RoundingMode.DOWN);
        BigDecimal remainingAmount = request.amount();

        records.forEach(this::reverseAssetChange);
        transactionRepository.deleteAll(records);
        transactionRepository.flush();

        for (int index = 1; index <= installmentMonths; index++) {
            BigDecimal amount = index == installmentMonths ? remainingAmount : baseAmount;
            remainingAmount = remainingAmount.subtract(amount);
            createSingleTransaction(
                    request,
                    request.transactionDate().plusMonths(index - 1L),
                    amount,
                    index,
                    installmentMonths,
                    installmentGroupId
            );
        }

        return transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId).stream()
                .map(TransactionDto::from)
                .toList();
    }

    @Transactional
    public void deleteInstallmentTransactions(String installmentGroupId) {
        List<TransactionRecord> records = transactionRepository.findByInstallmentGroupIdOrderByTransactionDateAscIdAsc(installmentGroupId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Installment group not found");
        }
        records.forEach(this::reverseAssetChange);
        transactionRepository.deleteAll(records);
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
        Member consumer = personalExpenseConsumer(record.getHousehold(), request);

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
                request.spendingTag(),
                request.consumptionScope(),
                consumer,
                request.installmentMonths() == null ? 0 : request.installmentMonths()
        );
        
        // 새로운 거래의 자산 변경사항 적용
        applyAssetChange(record);
        
        return TransactionDto.from(transactionRepository.save(record));
    }

    private Member personalExpenseConsumer(Household household, CreateTransactionRequest request) {
        if (request.type() != TransactionType.EXPENSE
                || request.consumptionScope() == com.comfortableledger.ledger.domain.ConsumptionScope.SHARED) {
            return null;
        }
        if (request.consumerMemberId() != null) {
            Member member = memberRepository.findById(request.consumerMemberId()).orElseThrow();
            if (!member.getHousehold().getId().equals(household.getId())) {
                throw new IllegalArgumentException("Consumer member must belong to the household");
            }
            return member;
        }
        return memberRepository.findByHouseholdId(household.getId()).stream()
                .min(Comparator.comparing(member -> member.getRole() == MemberRole.OWNER ? 0 : 1))
                .orElseThrow();
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

        Map<Category, BigDecimal> byCategory = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(TransactionRecord::getCategory,
                        Collectors.mapping(TransactionRecord::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        Household household = defaultHousehold();
        MonthlyBudget monthlyBudget = monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(household.getId(), yearMonth.toString())
                .orElse(null);
        BigDecimal budget = monthlyBudget == null ? BigDecimal.ZERO : monthlyBudget.getTotalAmount();
        BigDecimal budgetUsageRate = budget.signum() == 0
                ? BigDecimal.ZERO
                : expense.multiply(new BigDecimal("100")).divide(budget, 1, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> spentByCategoryId = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(record -> record.getCategory().getId(),
                        Collectors.mapping(TransactionRecord::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<Long, BigDecimal> budgetByCategoryId = monthlyBudget == null
                ? Map.of()
                : categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), CategoryBudget::getAmount));
        List<MonthlySummaryDto.CategoryBudgetUsage> categoryBudgetUsages = categoryRepository
                .findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(household.getId(), CategoryType.EXPENSE)
                .stream()
                .map(category -> {
                    BigDecimal categoryBudget = budgetByCategoryId.getOrDefault(category.getId(), BigDecimal.ZERO);
                    BigDecimal categorySpent = spentByCategoryId.getOrDefault(category.getId(), BigDecimal.ZERO);
                    BigDecimal usageRate = categoryBudget.signum() == 0
                            ? BigDecimal.ZERO
                            : categorySpent.multiply(new BigDecimal("100")).divide(categoryBudget, 1, RoundingMode.HALF_UP);
                    return new MonthlySummaryDto.CategoryBudgetUsage(
                            category.getId(),
                            category.getName(),
                            category.getIcon(),
                            categoryBudget,
                            categorySpent,
                            categoryBudget.subtract(categorySpent),
                            usageRate,
                            categoryBudget.signum() > 0 && categorySpent.compareTo(categoryBudget) > 0
                    );
                })
                .filter(item -> item.budgetAmount().signum() > 0 || item.spentAmount().signum() > 0)
                .sorted(Comparator
                        .comparing(MonthlySummaryDto.CategoryBudgetUsage::exceeded).reversed()
                        .thenComparing(MonthlySummaryDto.CategoryBudgetUsage::usageRate, Comparator.reverseOrder())
                        .thenComparing(MonthlySummaryDto.CategoryBudgetUsage::spentAmount, Comparator.reverseOrder()))
                .toList();

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
                        .sorted(Map.Entry.<Category, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                        .map(entry -> new MonthlySummaryDto.CategorySpend(entry.getKey().getId(), entry.getKey().getName(), entry.getValue()))
                        .toList(),
                tagSpends(records),
                scopeSpends(records),
                categoryBudgetUsages
        );
    }

    @Transactional(readOnly = true)
    public YearlySummaryDto yearlySummary(Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        LocalDate start = LocalDate.of(targetYear, 1, 1);
        LocalDate end = LocalDate.of(targetYear, 12, 31);
        List<TransactionRecord> records = transactionRepository.findByHouseholdIdAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                defaultHousehold().getId(),
                start,
                end
        );

        List<YearlySummaryDto.MonthlyTotals> monthlyTotals = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(monthNumber -> {
                    YearMonth yearMonth = YearMonth.of(targetYear, monthNumber);
                    List<TransactionRecord> monthRecords = records.stream()
                            .filter(record -> YearMonth.from(record.getTransactionDate()).equals(yearMonth))
                            .toList();
                    return new YearlySummaryDto.MonthlyTotals(
                            yearMonth.toString(),
                            sum(monthRecords, TransactionType.INCOME),
                            sum(monthRecords, TransactionType.EXPENSE),
                            sum(monthRecords, TransactionType.TRANSFER)
                    );
                })
                .toList();

        Map<Category, BigDecimal> byCategory = records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getCategory() != null)
                .collect(Collectors.groupingBy(TransactionRecord::getCategory,
                        Collectors.mapping(TransactionRecord::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return new YearlySummaryDto(
                targetYear,
                sum(records, TransactionType.INCOME),
                sum(records, TransactionType.EXPENSE),
                sum(records, TransactionType.TRANSFER),
                monthlyTotals,
                byCategory.entrySet().stream()
                        .sorted(Map.Entry.<Category, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                        .map(entry -> new MonthlySummaryDto.CategorySpend(entry.getKey().getId(), entry.getKey().getName(), entry.getValue()))
                        .toList(),
                tagSpends(records),
                scopeSpends(records)
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
        if (request.totalAmount() == null || request.totalAmount().signum() < 0) {
            throw new IllegalArgumentException("Budget total amount cannot be negative");
        }
        Household household = defaultHousehold();
        String budgetMonth = request.month() == null || request.month().isBlank() ? YearMonth.now().toString() : request.month();
        MonthlyBudget monthlyBudget = monthlyBudgetRepository.findByHouseholdIdAndBudgetMonth(household.getId(), budgetMonth)
                .orElseGet(() -> monthlyBudgetRepository.save(new MonthlyBudget(household, budgetMonth, BigDecimal.ZERO)));
        monthlyBudget.updateTotalAmount(request.totalAmount());

        Map<Long, CategoryBudget> existing = categoryBudgetRepository.findByMonthlyBudgetId(monthlyBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));
        if (request.categories() != null) {
            for (SaveBudgetRequest.SaveCategoryBudget item : request.categories()) {
                if (item.amount() == null || item.amount().signum() < 0) {
                    throw new IllegalArgumentException("Category budget amount cannot be negative");
                }
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

    @Transactional
    public BudgetSettingsDto copyPreviousBudget(String month) {
        Household household = defaultHousehold();
        YearMonth targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        YearMonth previousMonth = targetMonth.minusMonths(1);
        MonthlyBudget previousBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), previousMonth.toString())
                .orElseThrow(() -> new IllegalArgumentException("Previous month budget not found"));
        MonthlyBudget targetBudget = monthlyBudgetRepository
                .findByHouseholdIdAndBudgetMonth(household.getId(), targetMonth.toString())
                .orElseGet(() -> monthlyBudgetRepository.save(new MonthlyBudget(household, targetMonth.toString(), BigDecimal.ZERO)));

        targetBudget.updateTotalAmount(previousBudget.getTotalAmount());

        Map<Long, CategoryBudget> targetCategories = categoryBudgetRepository.findByMonthlyBudgetId(targetBudget.getId()).stream()
                .collect(Collectors.toMap(item -> item.getCategory().getId(), Function.identity()));
        for (CategoryBudget previousCategoryBudget : categoryBudgetRepository.findByMonthlyBudgetId(previousBudget.getId())) {
            Long categoryId = previousCategoryBudget.getCategory().getId();
            CategoryBudget targetCategoryBudget = targetCategories.get(categoryId);
            if (targetCategoryBudget == null) {
                categoryBudgetRepository.save(new CategoryBudget(
                        targetBudget,
                        previousCategoryBudget.getCategory(),
                        previousCategoryBudget.getAmount()
                ));
            } else {
                targetCategoryBudget.updateAmount(previousCategoryBudget.getAmount());
            }
        }

        return budgetSettings(targetMonth.toString());
    }

    private BigDecimal sum(List<TransactionRecord> records, TransactionType type) {
        return records.stream()
                .filter(record -> record.getType() == type)
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static List<MonthlySummaryDto.TagSpend> tagSpends(List<TransactionRecord> records) {
        Map<String, TagSpendAccumulator> totals = new LinkedHashMap<>();
        records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .filter(record -> record.getSpendingTag() != null && !record.getSpendingTag().isBlank())
                .forEach(record -> java.util.Arrays.stream(record.getSpendingTag().split("[,，]"))
                        .map(String::trim)
                        .map(tag -> tag.startsWith("#") ? tag.substring(1).trim() : tag)
                        .filter(tag -> !tag.isBlank())
                        .map(tag -> tag.replaceAll("\\s+", " "))
                        .distinct()
                        .forEach(tag -> {
                            String key = tag.toLowerCase(java.util.Locale.ROOT);
                            totals.computeIfAbsent(key, ignored -> new TagSpendAccumulator(tag))
                                    .add(record.getAmount());
                        }));
        return totals.values().stream()
                .map(item -> new MonthlySummaryDto.TagSpend(item.tagName, item.amount, item.transactionCount))
                .sorted(Comparator.comparing(MonthlySummaryDto.TagSpend::amount).reversed()
                        .thenComparing(MonthlySummaryDto.TagSpend::transactionCount, Comparator.reverseOrder())
                        .thenComparing(MonthlySummaryDto.TagSpend::tagName))
                .toList();
    }

    static List<MonthlySummaryDto.ScopeSpend> scopeSpends(List<TransactionRecord> records) {
        return records.stream()
                .filter(record -> record.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getConsumptionScope,
                        Collectors.collectingAndThen(Collectors.toList(), grouped -> new MonthlySummaryDto.ScopeSpend(
                                grouped.getFirst().getConsumptionScope(),
                                grouped.stream().map(TransactionRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                                grouped.size()
                        ))
                ))
                .values().stream()
                .sorted(Comparator.comparing(MonthlySummaryDto.ScopeSpend::amount).reversed()
                        .thenComparing(item -> item.scope().name()))
                .toList();
    }

    private static class TagSpendAccumulator {
        private final String tagName;
        private BigDecimal amount = BigDecimal.ZERO;
        private long transactionCount;

        private TagSpendAccumulator(String tagName) {
            this.tagName = tagName;
        }

        private void add(BigDecimal value) {
            amount = amount.add(value);
            transactionCount++;
        }
    }

    private String csvRow(String... values) {
        return java.util.Arrays.stream(values)
                .map(this::csvCell)
                .collect(Collectors.joining(","));
    }

    private String csvCell(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
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
