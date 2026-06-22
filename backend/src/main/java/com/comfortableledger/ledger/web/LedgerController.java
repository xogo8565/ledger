package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.service.CardService;
import com.comfortableledger.ledger.service.LedgerService;
import com.comfortableledger.ledger.web.ApiDtos.AssetDto;
import com.comfortableledger.ledger.web.ApiDtos.AssetSummaryDto;
import com.comfortableledger.ledger.web.ApiDtos.BootstrapDto;
import com.comfortableledger.ledger.web.ApiDtos.BudgetSettingsDto;
import com.comfortableledger.ledger.web.ApiDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.web.ApiDtos.CategoryDto;
import com.comfortableledger.ledger.web.ApiDtos.CreatePaymentScheduleRequest;
import com.comfortableledger.ledger.web.ApiDtos.CreateTransactionRequest;
import com.comfortableledger.ledger.web.ApiDtos.MonthlySummaryDto;
import com.comfortableledger.ledger.web.ApiDtos.SaveAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveBudgetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCardAssetRequest;
import com.comfortableledger.ledger.web.ApiDtos.SaveCategoryRequest;
import com.comfortableledger.ledger.web.ApiDtos.SchedulePaymentRequest;
import com.comfortableledger.ledger.web.ApiDtos.TransactionDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
    private final LedgerService ledgerService;
    private final CardService cardService;

    public LedgerController(LedgerService ledgerService, CardService cardService) {
        this.ledgerService = ledgerService;
        this.cardService = cardService;
    }

    @GetMapping("/bootstrap")
    public BootstrapDto bootstrap(@RequestParam(required = false) String month) {
        String targetMonth = month == null ? YearMonth.now().toString() : month;
        return new BootstrapDto(
                ledgerService.assets(),
                ledgerService.categories(),
                ledgerService.transactions(targetMonth),
                ledgerService.monthlySummary(targetMonth)
        );
    }

    @GetMapping("/assets")
    public List<AssetDto> assets() {
        return ledgerService.assets();
    }

    @GetMapping("/assets/summary")
    public AssetSummaryDto assetSummary() {
        return ledgerService.assetSummary();
    }

    @PostMapping("/assets")
    public AssetDto createAsset(@Valid @RequestBody SaveAssetRequest request) {
        return ledgerService.createAsset(request);
    }

    @PostMapping("/assets/card")
    public AssetDto createCardAsset(@Valid @RequestBody SaveCardAssetRequest request) {
        return ledgerService.createCardAsset(request);
    }

    @PutMapping("/assets/{id}")
    public AssetDto updateAsset(@PathVariable Long id, @Valid @RequestBody SaveAssetRequest request) {
        return ledgerService.updateAsset(id, request);
    }

    @DeleteMapping("/assets/{id}")
    public void deleteAsset(@PathVariable Long id) {
        ledgerService.deleteAsset(id);
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return ledgerService.categories();
    }

    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        return ledgerService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody SaveCategoryRequest request) {
        return ledgerService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        ledgerService.deleteCategory(id);
    }

    @GetMapping("/transactions")
    public List<TransactionDto> transactions(@RequestParam(required = false) String month) {
        return ledgerService.transactions(month);
    }

    @GetMapping("/transactions/daily")
    public List<TransactionDto> dailyTransactions(@RequestParam(required = false) String date) {
        return ledgerService.dailyTransactions(date);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDto getTransaction(@PathVariable Long id) {
        return ledgerService.getTransaction(id);
    }

    @GetMapping("/transactions/installments/{installmentGroupId}")
    public List<TransactionDto> installmentTransactions(@PathVariable String installmentGroupId) {
        return ledgerService.installmentTransactions(installmentGroupId);
    }

    @PostMapping("/transactions")
    public TransactionDto createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return ledgerService.createTransaction(request);
    }

    @PutMapping("/transactions/{id}")
    public TransactionDto updateTransaction(@PathVariable Long id, @Valid @RequestBody CreateTransactionRequest request) {
        return ledgerService.updateTransaction(id, request);
    }

    @DeleteMapping("/transactions/{id}")
    public void deleteTransaction(@PathVariable Long id) {
        ledgerService.deleteTransaction(id);
    }

    @GetMapping("/summary/monthly")
    public MonthlySummaryDto monthlySummary(@RequestParam(required = false) String month) {
        return ledgerService.monthlySummary(month);
    }

    @GetMapping("/budgets/settings")
    public BudgetSettingsDto budgetSettings(@RequestParam(required = false) String month) {
        return ledgerService.budgetSettings(month);
    }

    @PostMapping("/budgets/settings")
    public BudgetSettingsDto saveBudget(@Valid @RequestBody SaveBudgetRequest request) {
        return ledgerService.saveBudget(request);
    }
}
