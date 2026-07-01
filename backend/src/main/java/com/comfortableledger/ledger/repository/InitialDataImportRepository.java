package com.comfortableledger.ledger.repository;

import com.comfortableledger.ledger.domain.InitialDataImport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InitialDataImportRepository extends JpaRepository<InitialDataImport, Long> {
    Optional<InitialDataImport> findByResourceKindAndResourceName(String resourceKind, String resourceName);

    List<InitialDataImport> findAllByOrderByImportedAtDescIdDesc();
}
