package com.comfortableledger.ledger.controller;

import static com.comfortableledger.ledger.controller.support.ApiResponses.ok;

import com.comfortableledger.ledger.service.asset.BudgetService;
import com.comfortableledger.ledger.service.asset.CardService;
import com.comfortableledger.ledger.service.asset.DebtAutoDeductionService;
import com.comfortableledger.ledger.service.asset.AssetManagementService;
import com.comfortableledger.ledger.service.member.MemberService;
import com.comfortableledger.ledger.service.importing.InitialDataImportService;
import com.comfortableledger.ledger.service.statistics.StatisticsService;
import com.comfortableledger.ledger.service.transaction.TransactionQueryService;
import com.comfortableledger.ledger.service.transaction.TransactionSearchCriteria;
import com.comfortableledger.ledger.service.transaction.TransactionSearchSort;
import com.comfortableledger.ledger.service.transaction.TransactionCommandService;
import com.comfortableledger.ledger.dto.ApiResponse;
import com.comfortableledger.ledger.dto.AssetDtos.AssetDto;
import com.comfortableledger.ledger.dto.AssetDtos.AssetSummaryDto;
import com.comfortableledger.ledger.dto.SummaryDtos.BootstrapDto;
import com.comfortableledger.ledger.dto.BudgetDtos.BudgetSettingsDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.dto.CategoryDtos.CategoryDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CreatePaymentScheduleRequest;
import com.comfortableledger.ledger.dto.DebtDtos.DebtAutoDeductionOverviewDto;
import com.comfortableledger.ledger.dto.InitialDataDtos.InitialDataImportDto;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
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
    private final InitialDataImportService initialDataImportService;
    private final DebtAutoDeductionService debtAutoDeductionService;

    public LedgerController(AssetManagementService assetManagementService, StatisticsService statisticsService,
                            MemberService memberService,
                            BudgetService budgetService, TransactionQueryService transactionQueryService,
                            TransactionCommandService transactionCommandService, CardService cardService,
                            InitialDataImportService initialDataImportService,
                            DebtAutoDeductionService debtAutoDeductionService) {
        this.assetManagementService = assetManagementService;
        this.statisticsService = statisticsService;
        this.memberService = memberService;
        this.budgetService = budgetService;
        this.transactionQueryService = transactionQueryService;
        this.transactionCommandService = transactionCommandService;
        this.cardService = cardService;
        this.initialDataImportService = initialDataImportService;
        this.debtAutoDeductionService = debtAutoDeductionService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<BootstrapDto>> bootstrap(@RequestParam(required = false) String month) {
        String targetMonth = month == null ? YearMonth.now().toString() : month;
        return ok(new BootstrapDto(
                assetManagementService.assets(),
                assetManagementService.categories(),
                transactionQueryService.transactions(targetMonth),
                statisticsService.monthlySummary(targetMonth)
        ));
    }

    @GetMapping("/initial-data/imports")
    public ResponseEntity<ApiResponse<List<InitialDataImportDto>>> initialDataImportHistory() {
        return ok(initialDataImportService.importHistory());
    }

    @GetMapping("/debts/auto-deductions")
    public ResponseEntity<ApiResponse<DebtAutoDeductionOverviewDto>> debtAutoDeductionStatus(
            @RequestParam(required = false) LocalDate date
    ) {
        return ok(debtAutoDeductionService.deductionStatus(date));
    }

    @PostMapping("/debts/auto-deductions/execute")
    public ResponseEntity<ApiResponse<RecurringGenerationResult>> executeDebtAutoDeductions(
            @RequestParam(required = false) LocalDate date
    ) {
        return ok(debtAutoDeductionService.executeDueDeductions(date));
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<AssetDto>>> assets() {
        return ok(assetManagementService.assets());
    }

    @GetMapping("/assets/summary")
    public ResponseEntity<ApiResponse<AssetSummaryDto>> assetSummary() {
        return ok(assetManagementService.assetSummary());
    }

    @PostMapping("/assets")
    public ResponseEntity<ApiResponse<AssetDto>> createAsset(@Valid @RequestBody SaveAssetRequest request) {
        return ok(assetManagementService.createAsset(request));
    }

    @PostMapping("/assets/card")
    public ResponseEntity<ApiResponse<AssetDto>> createCardAsset(@Valid @RequestBody SaveCardAssetRequest request) {
        return ok(assetManagementService.createCardAsset(request));
    }

    @PutMapping("/assets/{id}/card")
    public ResponseEntity<ApiResponse<AssetDto>> updateCardAsset(@PathVariable Long id, @Valid @RequestBody SaveCardAssetRequest request) {
        return ok(assetManagementService.updateCardAsset(id, request));
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<AssetDto>> updateAsset(@PathVariable Long id, @Valid @RequestBody SaveAssetRequest request) {
        return ok(assetManagementService.updateAsset(id, request));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable Long id) {
        assetManagementService.deleteAsset(id);
        return ok();
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> categories() {
        return ok(assetManagementService.categories());
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        return ok(assetManagementService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(@PathVariable Long id, @Valid @RequestBody SaveCategoryRequest request) {
        return ok(assetManagementService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        assetManagementService.deleteCategory(id);
        return ok();
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<MemberDto>>> members() {
        return ok(memberService.members());
    }

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberDto>> createMember(@Valid @RequestBody SaveMemberRequest request) {
        return ok(memberService.createMember(request));
    }

    @PutMapping("/members/{id}")
    public ResponseEntity<ApiResponse<MemberDto>> updateMember(@PathVariable Long id, @Valid @RequestBody SaveMemberRequest request) {
        return ok(memberService.updateMember(id, request));
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ok();
    }

    @GetMapping("/members/consumer-migration")
    public ResponseEntity<ApiResponse<ConsumerMigrationDto>> consumerMigrationStatus() {
        return ok(memberService.consumerMigrationStatus());
    }

    @PostMapping("/members/consumer-migration")
    public ResponseEntity<ApiResponse<ConsumerMigrationDto>> migrateUnassignedPersonalExpenses() {
        return ok(memberService.migrateUnassignedPersonalExpenses());
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> transactions(@RequestParam(required = false) String month) {
        return ok(transactionQueryService.transactions(month));
    }

    @GetMapping("/transactions/range")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> transactionsBetween(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return ok(transactionQueryService.transactionsBetween(startDate, endDate));
    }

    @GetMapping("/transactions/search")
    public ResponseEntity<ApiResponse<TransactionSearchResultDto>> searchTransactions(
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
        return ok(transactionQueryService.searchTransactions(new TransactionSearchCriteria(
                startDate, endDate, type, categoryId, consumptionScope,
                consumerMemberId, assetId, minAmount, maxAmount, query,
                page, size, sort)));
    }

    @GetMapping("/transactions/daily")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> dailyTransactions(@RequestParam(required = false) String date) {
        return ok(transactionQueryService.dailyTransactions(date));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransaction(@PathVariable Long id) {
        return ok(transactionQueryService.getTransaction(id));
    }

    @GetMapping("/transactions/installments/{installmentGroupId}")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> installmentTransactions(@PathVariable String installmentGroupId) {
        return ok(transactionQueryService.installmentTransactions(installmentGroupId));
    }

    @PutMapping("/transactions/installments/{installmentGroupId}")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> updateInstallmentTransactions(
            @PathVariable String installmentGroupId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        return ok(transactionCommandService.updateInstallmentTransactions(installmentGroupId, request));
    }

    @DeleteMapping("/transactions/installments/{installmentGroupId}")
    public ResponseEntity<ApiResponse<Void>> deleteInstallmentTransactions(@PathVariable String installmentGroupId) {
        transactionCommandService.deleteInstallmentTransactions(installmentGroupId);
        return ok();
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<TransactionDto>> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return ok(transactionCommandService.createTransaction(request));
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> updateTransaction(@PathVariable Long id, @Valid @RequestBody CreateTransactionRequest request) {
        return ok(transactionCommandService.updateTransaction(id, request));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(@PathVariable Long id) {
        transactionCommandService.deleteTransaction(id);
        return ok();
    }

    @GetMapping("/summary/monthly")
    public ResponseEntity<ApiResponse<MonthlySummaryDto>> monthlySummary(@RequestParam(required = false) String month) {
        return ok(statisticsService.monthlySummary(month));
    }

    @GetMapping("/summary/yearly")
    public ResponseEntity<ApiResponse<YearlySummaryDto>> yearlySummary(@RequestParam(required = false) Integer year) {
        return ok(statisticsService.yearlySummary(year));
    }

    @GetMapping("/budgets/summary/yearly")
    public ResponseEntity<ApiResponse<YearlyBudgetSummaryDto>> yearlyBudgetSummary(@RequestParam(required = false) Integer year) {
        return ok(statisticsService.yearlyBudgetSummary(year));
    }

    @GetMapping("/summary/range")
    public ResponseEntity<ApiResponse<PeriodSummaryDto>> rangeSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return ok(statisticsService.rangeSummary(startDate, endDate));
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
    public ResponseEntity<ApiResponse<BudgetSettingsDto>> budgetSettings(@RequestParam(required = false) String month) {
        return ok(budgetService.budgetSettings(month));
    }

    @PostMapping("/budgets/settings")
    public ResponseEntity<ApiResponse<BudgetSettingsDto>> saveBudget(@Valid @RequestBody SaveBudgetRequest request) {
        return ok(budgetService.saveBudget(request));
    }

    @PostMapping("/budgets/settings/copy-previous")
    public ResponseEntity<ApiResponse<BudgetSettingsDto>> copyPreviousBudget(@RequestParam(required = false) String month) {
        return ok(budgetService.copyPreviousBudget(month));
    }
}
