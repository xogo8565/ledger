package com.comfortableledger.ledger.controller;

import com.comfortableledger.ledger.service.recurring.RecurringTransactionService;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringTransactionDto;
import com.comfortableledger.ledger.dto.RecurringDtos.SaveRecurringTransactionRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {
    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public List<RecurringTransactionDto> recurringTransactions() {
        return recurringTransactionService.recurringTransactions();
    }

    @PostMapping
    public RecurringTransactionDto createRecurringTransaction(@Valid @RequestBody SaveRecurringTransactionRequest request) {
        return recurringTransactionService.createRecurringTransaction(request);
    }

    @PutMapping("/{id}")
    public RecurringTransactionDto updateRecurringTransaction(
            @PathVariable Long id,
            @Valid @RequestBody SaveRecurringTransactionRequest request
    ) {
        return recurringTransactionService.updateRecurringTransaction(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteRecurringTransaction(@PathVariable Long id) {
        recurringTransactionService.deleteRecurringTransaction(id);
    }

    @PostMapping("/generate-due")
    public RecurringGenerationResult generateDueTransactions(@RequestParam(required = false) LocalDate upToDate) {
        return recurringTransactionService.generateDueTransactions(upToDate);
    }
}
