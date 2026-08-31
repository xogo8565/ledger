package com.comfortableledger.ledger.service.recurring;

import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
import java.time.LocalDate;
import java.time.YearMonth;
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

    // 매일 실행되지만, 대상 날짜를 "오늘"이 아니라 "이번 달 말일"로 잡는다. 그러면 이번 달
    // 들어 처음 실행되는 날(보통 매월 1일)에 이번 달 말까지의 반복 거래가 한 번에 일괄
    // 생성되고, 같은 달의 이후 실행에서는 이미 생성된 내역이라 추가로 할 일이 없다.
    // 1일에 서버가 꺼져 있었더라도 다음 실행 때 이번 달 전체를 그대로 일괄 생성한다.
    @Scheduled(cron = "${app.recurring-transaction.auto-generate-cron:0 20 4 * * *}", zone = "${app.recurring-transaction.zone:Asia/Seoul}")
    public void generateDueTransactions() {
        LocalDate endOfThisMonth = YearMonth.now().atEndOfMonth();
        RecurringGenerationResult result = recurringTransactionService.generateDueTransactions(endOfThisMonth);
        if (result.generatedCount() > 0) {
            log.info("Generated {} recurring transactions (through {})", result.generatedCount(), endOfThisMonth);
        }
    }
}
