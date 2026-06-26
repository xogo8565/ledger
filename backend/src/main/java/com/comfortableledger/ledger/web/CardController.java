package com.comfortableledger.ledger.web;

import com.comfortableledger.ledger.service.CardService;
import com.comfortableledger.ledger.web.ApiDtos.CardDetailDto;
import com.comfortableledger.ledger.web.ApiDtos.CardPaymentScheduleDto;
import com.comfortableledger.ledger.web.ApiDtos.CreatePaymentScheduleRequest;
import com.comfortableledger.ledger.web.ApiDtos.SchedulePaymentRequest;
import com.comfortableledger.ledger.web.ApiDtos.TransactionDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    public CardDetailDto cardDetail(@PathVariable Long cardAssetId) {
        return cardService.cardDetail(cardAssetId);
    }

    @GetMapping("/{cardAssetId}/unpaid")
    public BigDecimal getUnpaidAmount(@PathVariable Long cardAssetId) {
        return cardService.getUnpaidAmount(cardAssetId);
    }

    @GetMapping("/{cardAssetId}/payment-schedule")
    public BigDecimal getPaymentScheduleAmount(@PathVariable Long cardAssetId) {
        return cardService.getPaymentScheduleAmount(cardAssetId);
    }

    @GetMapping("/{cardAssetId}/billing-period")
    public List<TransactionDto> getBillingPeriodTransactions(@PathVariable Long cardAssetId) {
        return cardService.getBillingPeriodTransactions(cardAssetId);
    }

    @PostMapping("/{cardAssetId}/payment-schedules")
    public CardPaymentScheduleDto createPaymentSchedule(
            @PathVariable Long cardAssetId,
            @Valid @RequestBody CreatePaymentScheduleRequest request
    ) {
        return cardService.createPaymentSchedule(cardAssetId, request.scheduledDate(), request.amount());
    }

    @GetMapping("/{cardAssetId}/payment-schedules")
    public List<CardPaymentScheduleDto> getPaymentSchedules(@PathVariable Long cardAssetId) {
        return cardService.getPaymentSchedules(cardAssetId);
    }

    @GetMapping("/payment-schedules/due")
    public List<CardPaymentScheduleDto> getScheduledPayments(@RequestParam(required = false) LocalDate upToDate) {
        return cardService.getScheduledPayments(upToDate == null ? LocalDate.now() : upToDate);
    }

    @PostMapping("/payment-schedules/execute")
    public CardPaymentScheduleDto executePaymentSchedule(@Valid @RequestBody SchedulePaymentRequest request) {
        return cardService.executePaymentSchedule(request.scheduleId());
    }

    @PostMapping("/payment-schedules/{scheduleId}/reschedule")
    public CardPaymentScheduleDto rescheduleFailedPayment(@PathVariable Long scheduleId) {
        return cardService.rescheduleFailedPayment(scheduleId);
    }

    @PostMapping("/payment-schedules/{scheduleId}/retry")
    public CardPaymentScheduleDto retryFailedPayment(@PathVariable Long scheduleId) {
        return cardService.retryFailedPayment(scheduleId);
    }

    @DeleteMapping("/payment-schedules/{scheduleId}")
    public void cancelPaymentSchedule(@PathVariable Long scheduleId) {
        cardService.cancelPaymentSchedule(scheduleId);
    }
}
