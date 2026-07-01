package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.InitialDataImport;
import java.time.OffsetDateTime;

public final class InitialDataDtos {
    private InitialDataDtos() {
    }

    public record InitialDataImportDto(
            Long id,
            String resourceKind,
            String resourceName,
            String checksum,
            OffsetDateTime importedAt,
            int rowCount
    ) {
        public static InitialDataImportDto from(InitialDataImport history) {
            return new InitialDataImportDto(
                    history.getId(),
                    history.getResourceKind(),
                    history.getResourceName(),
                    history.getChecksum(),
                    history.getImportedAt(),
                    history.getRowCount()
            );
        }
    }
}
