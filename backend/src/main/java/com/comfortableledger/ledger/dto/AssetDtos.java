package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
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
            CardDto card
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
            return new AssetDto(
                    asset.getId(),
                    asset.getType(),
                    asset.getName(),
                    asset.getBalance(),
                    asset.getGroupName(),
                    asset.getOwnerName(),
                    asset.getMemo(),
                    card
            );
        }
    }

    public record SaveAssetRequest(
            @NotNull AssetType type,
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String ownerName,
            String memo
    ) {
    }

    public record SaveCardAssetRequest(
            @NotBlank String name,
            @NotNull BigDecimal balance,
            String groupName,
            String ownerName,
            String memo,
            @NotNull Long paymentAccountId,
            int statementClosingDay,
            int paymentDay,
            boolean autoPayment
    ) {
    }

    public record CardDto(Long paymentAccountId, int statementClosingDay, int paymentDay, boolean autoPayment) {
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
