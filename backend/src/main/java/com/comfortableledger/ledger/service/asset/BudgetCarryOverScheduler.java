package com.comfortableledger.ledger.service.asset;

import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.budget-carry-over", name = "auto-copy-enabled", havingValue = "true", matchIfMissing = true)
public class BudgetCarryOverScheduler {
    private static final Logger log = LoggerFactory.getLogger(BudgetCarryOverScheduler.class);

    private final BudgetService budgetService;

    public BudgetCarryOverScheduler(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Scheduled(cron = "${app.budget-carry-over.auto-copy-cron:0 40 4 * * *}", zone = "${app.budget-carry-over.zone:Asia/Seoul}")
    public void carryOverCurrentMonthBudget() {
        YearMonth currentMonth = YearMonth.now();
        boolean copied = budgetService.carryOverBudgetIfUnset(currentMonth);
        if (copied) {
            log.info("Carried over previous month's budget into {}", currentMonth);
        }
    }
}
