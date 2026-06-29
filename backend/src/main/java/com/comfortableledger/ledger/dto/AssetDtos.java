package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class AssetDtos {
    private AssetDtos() {
    }

    public record AssetDto(
            Long id,
            AssetType type,
            String name,
            BigDecimal balance,
            String groupName,
            String ownerName,
            String memo,
            CardDto card,
            DebtDto debt
    ) {
        public static AssetDto from(Asset asset) {
            CardDto card = asset.getCardProfile() == null
                    ? null
                    : new CardDto(
                    asset.getCardProfile().getPaymentAccount() == null ? null : asset.getCardProfile().getPaymentAccount().getId(),
                    asset.getCardProfile().getStatementClosingDay(),
                    asset.getCardProfile().getPaymentDay(),
                    asset.getCardProfile().isAutoPayment()
            );
            DebtDto debt = asset.getDebtProfile() == null
                    ? null
                    : new DebtDto(
                    asset.getDebtProfile().getPaymentAccount() == null ? null : asset.getDebtProfile().getPaymentAccount().getId(),
                    asset.getDebtProfile().getAnnualInterestRate(),
                    asset.getDebtProfile().getPaymentDay(),
                    asset.getDebtProfile().isAutoDeduct(),
                    asset.getDebtProfile().getLastDeductedMonth()
            );
            return new AssetDto(
                    asset.getId(),
                    asset.getType(),
                    asset.getName(),
                    asset.getBalance(),
                    asset.getGroupName(),
                    asset.getOwnerName(),
                    asset.getMemo(),
                    card,
                    debt
            );
        }
    }

    public record SaveAssetRequest(
            @NotNull AssetType type,
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String ownerName,
            String memo,
            Long debtPaymentAccountId,
            BigDecimal annualInterestRate,
            @Min(1)
            @Max(31)
            Integer debtPaymentDay,
            Boolean debtAutoDeduct
    ) {
    }

    public record SaveCardAssetRequest(
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String ownerName,
            String memo,
            @NotNull Long paymentAccountId,
            @Min(1)
            @Max(31)
            int statementClosingDay,
            @Min(1)
            @Max(31)
            int paymentDay,
            boolean autoPayment
    ) {
    }

    public record CardDto(Long paymentAccountId, int statementClosingDay, int paymentDay, boolean autoPayment) {
    }

    public record DebtDto(Long paymentAccountId, BigDecimal annualInterestRate, int paymentDay,
                          boolean autoDeduct, String lastDeductedMonth) {
    }

    public record AssetSummaryDto(
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal netWorth,
            List<OwnerAssetSummary> owners
    ) {
        public record OwnerAssetSummary(
                String ownerName,
                BigDecimal totalAssets,
                BigDecimal totalLiabilities,
                BigDecimal netWorth,
                long assetCount
        ) {
        }
    }
}
