package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.Category;
import com.comfortableledger.ledger.domain.CategoryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByHouseholdIdAndActiveTrueOrderBySortOrderAscIdAsc(Long householdId);

    List<Category> findByHouseholdIdAndTypeAndActiveTrueOrderBySortOrderAscIdAsc(Long householdId, CategoryType type);
}
