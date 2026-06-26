package com.comfortableledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.comfortableledger.ledger.domain.Asset;
import com.comfortableledger.ledger.domain.AssetType;
import com.comfortableledger.ledger.domain.Household;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetManagementServiceAssetSummaryTest {

    @Test
    void aggregatesAssetsAndLiabilitiesByNormalizedOwnerName() {
        Household household = new Household("테스트");
        Asset ownBank = asset(household, AssetType.BANK, "본인 계좌", "1000000", " 본인 ");
        Asset ownDebt = asset(household, AssetType.DEBT, "본인 대출", "-200000", "본인");
        Asset sharedCash = asset(household, AssetType.CASH, "공동 현금", "300000", "공동");
        Asset unassignedCard = asset(household, AssetType.CARD, "미지정 카드", "50000", " ");

        var summary = AssetManagementService.summarizeAssets(List.of(ownBank, ownDebt, sharedCash, unassignedCard));

        assertThat(summary.totalAssets()).isEqualByComparingTo("1300000");
        assertThat(summary.totalLiabilities()).isEqualByComparingTo("250000");
        assertThat(summary.netWorth()).isEqualByComparingTo("1050000");
        assertThat(summary.owners())
                .extracting("ownerName", "totalAssets", "totalLiabilities", "netWorth", "assetCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("본인", new BigDecimal("1000000"), new BigDecimal("200000"), new BigDecimal("800000"), 2L),
                        org.assertj.core.groups.Tuple.tuple("공동", new BigDecimal("300000"), BigDecimal.ZERO, new BigDecimal("300000"), 1L),
                        org.assertj.core.groups.Tuple.tuple("명의 미지정", BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("-50000"), 1L)
                );
    }

    private Asset asset(Household household, AssetType type, String name, String balance, String ownerName) {
        Asset asset = new Asset(household, type, name, new BigDecimal(balance), type.name());
        asset.update(type, name, new BigDecimal(balance), type.name(), ownerName, "");
        return asset;
    }
}
