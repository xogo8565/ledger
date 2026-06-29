package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(Long householdId);

    Optional<Asset> findByHouseholdIdAndNameIgnoreCase(Long householdId, String name);
}
