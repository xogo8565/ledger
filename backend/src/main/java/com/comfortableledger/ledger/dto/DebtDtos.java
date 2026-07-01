package com.comfortableledger.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DebtDtos {
    private DebtDtos() {
    }

    public record DebtAutoDeductionStatusDto(
            Long debtProfileId,
            Long debtAssetId,
            String debtAssetName,
            BigDecimal debtBalance,
            Long paymentAccountId,
            String paymentAccountName,
            BigDecimal annualInterestRate,
            int paymentDay,
            boolean autoDeduct,
            String lastDeductedMonth,
            BigDecimal estimatedMonthlyInterest,
            boolean due,
            boolean executable,
            String status
    ) {
    }

    public record DebtAutoDeductionOverviewDto(
            LocalDate asOfDate,
            int totalProfiles,
            int executableProfiles,
            List<DebtAutoDeductionStatusDto> items
    ) {
    }
}
