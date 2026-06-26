package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.CardPaymentSchedule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CardPaymentDtos {
    private CardPaymentDtos() {
    }

    public record CardDetailDto(Long id, String name, BigDecimal balance, Long paymentAccountId,
                                int statementClosingDay, int paymentDay, boolean autoPayment,
                                BigDecimal unpaidAmount, BigDecimal paymentScheduleAmount,
                                LocalDate nextPaymentDate, LocalDate originalPaymentDate, boolean paymentDateAdjusted,
                                LocalDate billingStartDate, LocalDate billingEndDate) {
    }

    public record CardPaymentScheduleDto(
            Long id,
            Long cardAssetId,
            LocalDate scheduledDate,
            BigDecimal amount,
            String status,
            LocalDateTime completedAt,
            String failureReason
    ) {
        public static CardPaymentScheduleDto from(CardPaymentSchedule schedule) {
            return new CardPaymentScheduleDto(
                    schedule.getId(),
                    schedule.getCardAsset().getId(),
                    schedule.getScheduledDate(),
                    schedule.getAmount(),
                    schedule.getStatus().toString(),
                    schedule.getCompletedAt(),
                    schedule.getFailureReason()
            );
        }
    }

    public record CreatePaymentScheduleRequest(
            @NotNull LocalDate scheduledDate,
            @NotNull @Positive BigDecimal amount
    ) {
    }

    public record SchedulePaymentRequest(
            @NotNull Long scheduleId
    ) {
    }
}
