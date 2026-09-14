//package com.comfortableledger.ledger.service.recurring;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import com.comfortableledger.ledger.dto.RecurringDtos.RecurringGenerationResult;
//import java.time.LocalDate;
//import java.time.YearMonth;
//import org.junit.jupiter.api.Test;
//
//class RecurringTransactionSchedulerTest {
//    private final RecurringTransactionService recurringTransactionService = mock(RecurringTransactionService.class);
//    private final RecurringTransactionScheduler scheduler = new RecurringTransactionScheduler(recurringTransactionService);
//
////    @Test
////    void generatesThroughEndOfCurrentMonthInsteadOfJustToday() {
////        when(recurringTransactionService.generateDueTransactions(any())).thenReturn(new RecurringGenerationResult(0));
////
////        scheduler.generateDueTransactions();
////
////        LocalDate endOfThisMonth = YearMonth.now().atEndOfMonth();
////        verify(recurringTransactionService).generateDueTransactions(endOfThisMonth);
////    }
//}
