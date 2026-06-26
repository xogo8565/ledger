package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Household;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetManagementServiceSummaryTest {
    @Test
    void summarizesAssetsAndLiabilitiesByOwner() {
        Household household = new Household("테스트");
        Asset bank = asset(household, AssetType.BANK, "계좌", "1000000", "본인");
        Asset debt = asset(household, AssetType.DEBT, "대출", "-200000", "본인");
        Asset card = asset(household, AssetType.CARD, "카드", "50000", null);

        var summary = AssetManagementService.summarizeAssets(List.of(bank, debt, card));

        assertThat(summary.totalAssets()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(summary.totalLiabilities()).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(summary.netWorth()).isEqualByComparingTo(new BigDecimal("750000"));
        assertThat(summary.owners()).extracting("ownerName")
                .contains("본인", "명의 미지정");
    }

    private Asset asset(Household household, AssetType type, String name, String balance, String ownerName) {
        Asset asset = new Asset(household, type, name, new BigDecimal(balance), type.name());
        asset.update(type, name, new BigDecimal(balance), type.name(), ownerName, "");
        return asset;
    }
}
