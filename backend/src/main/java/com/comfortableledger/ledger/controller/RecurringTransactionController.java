package com.comfortableledger.ledger.controller;

import static com.comfortableledger.ledger.controller.support.ApiResponses.ok;

import com.comfortableledger.ledger.dto.ApiResponse;
import com.comfortableledger.ledger.service.recurring.RecurringTransactionService;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import com.comfortableledger.ledger.dto.RecurringDtos.RecurringTransactionDto;
import com.comfortableledger.ledger.dto.RecurringDtos.SaveRecurringTransactionRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/recurring-transactions")
public class RecurringTransactionController {
    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecurringTransactionDto>>> recurringTransactions() {
        return ok(recurringTransactionService.recurringTransactions());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecurringTransactionDto>> createRecurringTransaction(@Valid @RequestBody SaveRecurringTransactionRequest request) {
        return ok(recurringTransactionService.createRecurringTransaction(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RecurringTransactionDto>> updateRecurringTransaction(
            @PathVariable Long id,
            @Valid @RequestBody SaveRecurringTransactionRequest request
    ) {
        return ok(recurringTransactionService.updateRecurringTransaction(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecurringTransaction(@PathVariable Long id) {
        recurringTransactionService.deleteRecurringTransaction(id);
        return ok();
    }

    @PostMapping("/generate-due")
    public ResponseEntity<ApiResponse<RecurringGenerationResult>> generateDueTransactions(@RequestParam(required = false) LocalDate upToDate) {
        return ok(recurringTransactionService.generateDueTransactions(upToDate));
    }
}
