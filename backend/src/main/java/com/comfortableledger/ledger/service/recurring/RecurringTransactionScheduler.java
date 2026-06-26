package com.comfortableledger.ledger.service.recurring;

import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.recurring-transaction", name = "auto-generate-enabled", havingValue = "true", matchIfMissing = true)
public class RecurringTransactionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionScheduler(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @Scheduled(cron = "${app.recurring-transaction.auto-generate-cron:0 20 4 * * *}", zone = "${app.recurring-transaction.zone:Asia/Seoul}")
    public void generateDueTransactions() {
        RecurringGenerationResult result = recurringTransactionService.generateDueTransactions(LocalDate.now());
        if (result.generatedCount() > 0) {
            log.info("Generated {} recurring transactions", result.generatedCount());
        }
    }
}
