package com.comfortableledger.ledger.repo;

import com.comfortableledger.ledger.domain.Asset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByHouseholdIdAndHiddenFalseOrderBySortOrderAscIdAsc(Long householdId);
}
