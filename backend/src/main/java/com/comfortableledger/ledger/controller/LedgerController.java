package com.comfortableledger.ledger.controller;

import com.comfortableledger.ledger.service.asset.BudgetService;
import com.comfortableledger.ledger.service.asset.CardService;
import com.comfortableledger.ledger.service.asset.AssetManagementService;
import com.comfortableledger.ledger.service.member.MemberService;
import com.comfortableledger.ledger.service.statistics.StatisticsService;
import com.comfortableledger.ledger.service.transaction.TransactionQueryService;
import com.comfortableledger.ledger.service.transaction.TransactionSearchCriteria;
import com.comfortableledger.ledger.service.transaction.TransactionSearchSort;
import com.comfortableledger.ledger.service.transaction.TransactionCommandService;
import com.comfortableledger.ledger.dto.AssetDtos.AssetDto;
import com.comfortableledger.ledger.dto.AssetDtos.AssetSummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.BootstrapDto;
import com.comfortableledger.ledger.dto.BudgetDtos.BudgetSettingsDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.dto.CategoryDtos.CategoryDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CreatePaymentScheduleRequest;
import com.comfortableledger.ledger.dto.TransactionDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.dto.MemberDtos.ConsumerMigrationDto;
import com.comfortableledger.ledger.dto.SummaryDtos.MonthlySummaryDto;
import com.comfortableledger.ledger.dto.MemberDtos.MemberDto;
import com.comfortableledger.ledger.dto.SummaryDtos.PeriodSummaryDto;
import com.comfortableledger.ledger.dto.AssetDtos.SaveAssetRequest;
import com.comfortableledger.ledger.dto.BudgetDtos.SaveBudgetRequest;
import com.comfortableledger.ledger.dto.AssetDtos.SaveCardAssetRequest;
import com.comfortableledger.ledger.dto.CategoryDtos.SaveCategoryRequest;
import com.comfortableledger.ledger.dto.MemberDtos.SaveMemberRequest;
import com.comfortableledger.ledger.dto.CardPaymentDtos.SchedulePaymentRequest;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionDto;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionSearchResultDto;
import com.comfortableledger.ledger.dto.SummaryDtos.YearlySummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.YearlyBudgetSummaryDto;
import com.comfortableledger.ledger.domain.ConsumptionScope;
import com.comfortableledger.ledger.domain.TransactionType;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final AssetManagementService assetManagementService;
    private final StatisticsService statisticsService;
    private final MemberService memberService;
    private final BudgetService budgetService;
    private final TransactionQueryService transactionQueryService;
    private final TransactionCommandService transactionCommandService;
    private final CardService cardService;

    public LedgerController(AssetManagementService assetManagementService, StatisticsService statisticsService,
                            MemberService memberService,
                            BudgetService budgetService, TransactionQueryService transactionQueryService,
                            TransactionCommandService transactionCommandService, CardService cardService) {
        this.assetManagementService = assetManagementService;
        this.statisticsService = statisticsService;
        this.memberService = memberService;
        this.budgetService = budgetService;
        this.transactionQueryService = transactionQueryService;
        this.transactionCommandService = transactionCommandService;
        this.cardService = cardService;
    }

    @GetMapping("/bootstrap")
    public BootstrapDto bootstrap(@RequestParam(required = false) String month) {
        String targetMonth = month == null ? YearMonth.now().toString() : month;
        return new BootstrapDto(
                assetManagementService.assets(),
                assetManagementService.categories(),
                transactionQueryService.transactions(targetMonth),
                statisticsService.monthlySummary(targetMonth)
        );
    }

    @GetMapping("/assets")
    public List<AssetDto> assets() {
        return assetManagementService.assets();
    }

    @GetMapping("/assets/summary")
    public AssetSummaryDto assetSummary() {
        return assetManagementService.assetSummary();
    }

    @PostMapping("/assets")
    public AssetDto createAsset(@Valid @RequestBody SaveAssetRequest request) {
        return assetManagementService.createAsset(request);
    }

    @PostMapping("/assets/card")
    public AssetDto createCardAsset(@Valid @RequestBody SaveCardAssetRequest request) {
        return assetManagementService.createCardAsset(request);
    }

    @PutMapping("/assets/{id}/card")
    public AssetDto updateCardAsset(@PathVariable Long id, @Valid @RequestBody SaveCardAssetRequest request) {
        return assetManagementService.updateCardAsset(id, request);
    }

    @PutMapping("/assets/{id}")
    public AssetDto updateAsset(@PathVariable Long id, @Valid @RequestBody SaveAssetRequest request) {
        return assetManagementService.updateAsset(id, request);
    }

    @DeleteMapping("/assets/{id}")
    public void deleteAsset(@PathVariable Long id) {
        assetManagementService.deleteAsset(id);
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return assetManagementService.categories();
    }

    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        return assetManagementService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody SaveCategoryRequest request) {
        return assetManagementService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        assetManagementService.deleteCategory(id);
    }

    @GetMapping("/members")
    public List<MemberDto> members() {
        return memberService.members();
    }

    @PostMapping("/members")
    public MemberDto createMember(@Valid @RequestBody SaveMemberRequest request) {
        return memberService.createMember(request);
    }

    @PutMapping("/members/{id}")
    public MemberDto updateMember(@PathVariable Long id, @Valid @RequestBody SaveMemberRequest request) {
        return memberService.updateMember(id, request);
    }

    @DeleteMapping("/members/{id}")
    public void deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
    }

    @GetMapping("/members/consumer-migration")
    public ConsumerMigrationDto consumerMigrationStatus() {
        return memberService.consumerMigrationStatus();
    }

    @PostMapping("/members/consumer-migration")
    public ConsumerMigrationDto migrateUnassignedPersonalExpenses() {
        return memberService.migrateUnassignedPersonalExpenses();
    }

    @GetMapping("/transactions")
    public List<TransactionDto> transactions(@RequestParam(required = false) String month) {
        return transactionQueryService.transactions(month);
    }

    @GetMapping("/transactions/range")
    public List<TransactionDto> transactionsBetween(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return transactionQueryService.transactionsBetween(startDate, endDate);
    }

    @GetMapping("/transactions/search")
    public TransactionSearchResultDto searchTransactions(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ConsumptionScope consumptionScope,
            @RequestParam(required = false) Long consumerMemberId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) TransactionSearchSort sort
    ) {
        return transactionQueryService.searchTransactions(new TransactionSearchCriteria(
                startDate, endDate, type, categoryId, consumptionScope,
                consumerMemberId, assetId, minAmount, maxAmount, query,
                page, size, sort));
    }

    @GetMapping("/transactions/daily")
    public List<TransactionDto> dailyTransactions(@RequestParam(required = false) String date) {
        return transactionQueryService.dailyTransactions(date);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDto getTransaction(@PathVariable Long id) {
        return transactionQueryService.getTransaction(id);
    }

    @GetMapping("/transactions/installments/{installmentGroupId}")
    public List<TransactionDto> installmentTransactions(@PathVariable String installmentGroupId) {
        return transactionQueryService.installmentTransactions(installmentGroupId);
    }

    @PutMapping("/transactions/installments/{installmentGroupId}")
    public List<TransactionDto> updateInstallmentTransactions(
            @PathVariable String installmentGroupId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        return transactionCommandService.updateInstallmentTransactions(installmentGroupId, request);
    }

    @DeleteMapping("/transactions/installments/{installmentGroupId}")
    public void deleteInstallmentTransactions(@PathVariable String installmentGroupId) {
        transactionCommandService.deleteInstallmentTransactions(installmentGroupId);
    }

    @PostMapping("/transactions")
    public TransactionDto createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return transactionCommandService.createTransaction(request);
    }

    @PutMapping("/transactions/{id}")
    public TransactionDto updateTransaction(@PathVariable Long id, @Valid @RequestBody CreateTransactionRequest request) {
        return transactionCommandService.updateTransaction(id, request);
    }

    @DeleteMapping("/transactions/{id}")
    public void deleteTransaction(@PathVariable Long id) {
        transactionCommandService.deleteTransaction(id);
    }

    @GetMapping("/summary/monthly")
    public MonthlySummaryDto monthlySummary(@RequestParam(required = false) String month) {
        return statisticsService.monthlySummary(month);
    }

    @GetMapping("/summary/yearly")
    public YearlySummaryDto yearlySummary(@RequestParam(required = false) Integer year) {
        return statisticsService.yearlySummary(year);
    }

    @GetMapping("/budgets/summary/yearly")
    public YearlyBudgetSummaryDto yearlyBudgetSummary(@RequestParam(required = false) Integer year) {
        return statisticsService.yearlyBudgetSummary(year);
    }

    @GetMapping("/summary/range")
    public PeriodSummaryDto rangeSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return statisticsService.rangeSummary(startDate, endDate);
    }

    @GetMapping("/export/transactions.csv")
    public ResponseEntity<byte[]> exportTransactionsCsv(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year
    ) {
        String csv = "\uFEFF" + transactionQueryService.exportTransactionsCsv(month, year);
        String period = year != null ? String.valueOf(year) : month == null || month.isBlank() ? YearMonth.now().toString() : month;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ledger-transactions-" + period + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/budgets/settings")
    public BudgetSettingsDto budgetSettings(@RequestParam(required = false) String month) {
        return budgetService.budgetSettings(month);
    }

    @PostMapping("/budgets/settings")
    public BudgetSettingsDto saveBudget(@Valid @RequestBody SaveBudgetRequest request) {
        return budgetService.saveBudget(request);
    }

    @PostMapping("/budgets/settings/copy-previous")
    public BudgetSettingsDto copyPreviousBudget(@RequestParam(required = false) String month) {
        return budgetService.copyPreviousBudget(month);
    }
}
