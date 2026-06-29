package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.DebtProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtProfileRepository extends JpaRepository<DebtProfile, Long> {
    List<DebtProfile> findByAutoDeductTrue();
}
