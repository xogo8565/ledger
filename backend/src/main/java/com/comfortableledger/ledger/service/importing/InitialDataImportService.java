package com.comfortableledger.ledger.service.importing;

import com.comfortableledger.ledger.dto.InitialDataDtos.InitialDataImportDto;
import com.comfortableledger.ledger.repository.InitialDataImportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialDataImportService {
    private final InitialDataImportRepository initialDataImportRepository;

    public InitialDataImportService(InitialDataImportRepository initialDataImportRepository) {
        this.initialDataImportRepository = initialDataImportRepository;
    }

    @Transactional(readOnly = true)
    public List<InitialDataImportDto> importHistory() {
        return initialDataImportRepository.findAllByOrderByImportedAtDescIdDesc().stream()
                .map(InitialDataImportDto::from)
                .toList();
    }
}
