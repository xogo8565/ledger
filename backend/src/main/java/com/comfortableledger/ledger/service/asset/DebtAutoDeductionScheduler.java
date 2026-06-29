package com.comfortableledger.ledger.service.asset;

import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.debt-auto-deduction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DebtAutoDeductionScheduler {
    private static final Logger log = LoggerFactory.getLogger(DebtAutoDeductionScheduler.class);

    private final DebtAutoDeductionService debtAutoDeductionService;

    public DebtAutoDeductionScheduler(DebtAutoDeductionService debtAutoDeductionService) {
        this.debtAutoDeductionService = debtAutoDeductionService;
    }

    @Scheduled(cron = "${app.debt-auto-deduction.cron:0 30 4 * * *}", zone = "${app.debt-auto-deduction.zone:Asia/Seoul}")
    public void executeDueDeductions() {
        RecurringGenerationResult result = debtAutoDeductionService.executeDueDeductions(LocalDate.now());
        if (result.generatedCount() > 0) {
            log.info("Executed {} debt auto deductions", result.generatedCount());
        }
    }
}
