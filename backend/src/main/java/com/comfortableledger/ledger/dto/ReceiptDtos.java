package com.comfortableledger.ledger.dto;

import com.comfortableledger.ledger.domain.ReceiptAttachment;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReceiptDtos {
    private ReceiptDtos() {
    }

    public record ReceiptOcrPreview(
            String originalFilename,
            String rawText,
            TextImportPreview preview,
            List<String> warnings,
            ReceiptOcrCandidates candidates
    ) {
    }

    public record ReceiptOcrCandidates(
            List<LocalDate> dateCandidates,
            List<String> titleCandidates,
            List<BigDecimal> amountCandidates
    ) {
    }

    public record ReceiptDto(Long id, String originalFilename, String storedPath, String contentType, long size) {
        public static ReceiptDto from(ReceiptAttachment attachment) {
            return new ReceiptDto(
                    attachment.getId(),
                    attachment.getOriginalFilename(),
                    attachment.getStoredPath(),
                    attachment.getContentType(),
                    attachment.getSize()
            );
        }
    }
}
