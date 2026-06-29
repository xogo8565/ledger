package com.comfortableledger.ledger.controller;

import static com.comfortableledger.ledger.controller.support.ApiResponses.ok;

import com.comfortableledger.ledger.service.asset.CardService;
import com.comfortableledger.ledger.dto.ApiResponse;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CardDetailDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.dto.CardPaymentDtos.CreatePaymentScheduleRequest;
import com.comfortableledger.ledger.dto.CardPaymentDtos.SchedulePaymentRequest;
import com.comfortableledger.ledger.dto.TransactionDtos.TransactionDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/{cardAssetId}")
    public ResponseEntity<ApiResponse<CardDetailDto>> cardDetail(@PathVariable Long cardAssetId) {
        return ok(cardService.cardDetail(cardAssetId));
    }

    @GetMapping("/{cardAssetId}/unpaid")
    public ResponseEntity<ApiResponse<BigDecimal>> getUnpaidAmount(@PathVariable Long cardAssetId) {
        return ok(cardService.getUnpaidAmount(cardAssetId));
    }

    @GetMapping("/{cardAssetId}/payment-schedule")
    public ResponseEntity<ApiResponse<BigDecimal>> getPaymentScheduleAmount(@PathVariable Long cardAssetId) {
        return ok(cardService.getPaymentScheduleAmount(cardAssetId));
    }

    @GetMapping("/{cardAssetId}/billing-period")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getBillingPeriodTransactions(@PathVariable Long cardAssetId) {
        return ok(cardService.getBillingPeriodTransactions(cardAssetId));
    }

    @PostMapping("/{cardAssetId}/payment-schedules")
    public ResponseEntity<ApiResponse<CardPaymentScheduleDto>> createPaymentSchedule(
            @PathVariable Long cardAssetId,
            @Valid @RequestBody CreatePaymentScheduleRequest request
    ) {
        return ok(cardService.createPaymentSchedule(cardAssetId, request.scheduledDate(), request.amount()));
    }

    @GetMapping("/{cardAssetId}/payment-schedules")
    public ResponseEntity<ApiResponse<List<CardPaymentScheduleDto>>> getPaymentSchedules(@PathVariable Long cardAssetId) {
        return ok(cardService.getPaymentSchedules(cardAssetId));
    }

    @GetMapping("/payment-schedules/due")
    public ResponseEntity<ApiResponse<List<CardPaymentScheduleDto>>> getScheduledPayments(@RequestParam(required = false) LocalDate upToDate) {
        return ok(cardService.getScheduledPayments(upToDate == null ? LocalDate.now() : upToDate));
    }

    @PostMapping("/payment-schedules/execute")
    public ResponseEntity<ApiResponse<CardPaymentScheduleDto>> executePaymentSchedule(@Valid @RequestBody SchedulePaymentRequest request) {
        return ok(cardService.executePaymentSchedule(request.scheduleId()));
    }

    @PostMapping("/payment-schedules/{scheduleId}/reschedule")
    public ResponseEntity<ApiResponse<CardPaymentScheduleDto>> rescheduleFailedPayment(@PathVariable Long scheduleId) {
        return ok(cardService.rescheduleFailedPayment(scheduleId));
    }

    @PostMapping("/payment-schedules/{scheduleId}/retry")
    public ResponseEntity<ApiResponse<CardPaymentScheduleDto>> retryFailedPayment(@PathVariable Long scheduleId) {
        return ok(cardService.retryFailedPayment(scheduleId));
    }

    @DeleteMapping("/payment-schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> cancelPaymentSchedule(@PathVariable Long scheduleId) {
        cardService.cancelPaymentSchedule(scheduleId);
        return ok();
    }
}
