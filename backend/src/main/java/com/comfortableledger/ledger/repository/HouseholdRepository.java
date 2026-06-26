package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.Household;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findFirstByOrderByIdAsc();
}
