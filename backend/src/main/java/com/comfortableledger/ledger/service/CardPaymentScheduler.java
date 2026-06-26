package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.dto.ApiDtos.CardPaymentScheduleDto;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.card-payment", name = "auto-execute-enabled", havingValue = "true", matchIfMissing = true)
public class CardPaymentScheduler {
    private static final Logger log = LoggerFactory.getLogger(CardPaymentScheduler.class);

    private final CardService cardService;

    public CardPaymentScheduler(CardService cardService) {
        this.cardService = cardService;
    }

    @Scheduled(cron = "${app.card-payment.auto-execute-cron:0 10 4 * * *}", zone = "${app.card-payment.zone:Asia/Seoul}")
    public void executeDuePayments() {
        LocalDate today = LocalDate.now();
        List<CardPaymentScheduleDto> duePayments = cardService.getScheduledPayments(today);
        for (CardPaymentScheduleDto duePayment : duePayments) {
            try {
                CardPaymentScheduleDto result = cardService.executePaymentSchedule(duePayment.id());
                if ("FAILED".equals(result.status())) {
                    log.warn("Failed to execute card payment schedule {}: {}", result.id(), result.failureReason());
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to execute card payment schedule {}", duePayment.id(), exception);
            }
        }
    }
}
