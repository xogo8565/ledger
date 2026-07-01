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
            ReceiptOcrCandidates candidates,
            ReceiptOcrPolicy policy
    ) {
        public ReceiptOcrPreview(
                String originalFilename,
                String rawText,
                TextImportPreview preview,
                List<String> warnings,
                ReceiptOcrCandidates candidates
        ) {
            this(originalFilename, rawText, preview, warnings, candidates, ReceiptOcrPolicy.from(warnings, candidates));
        }
    }

    public record ReceiptOcrPolicy(
            int confidenceScore,
            boolean needsReview,
            List<String> recognizedFields,
            List<String> reviewReasons
    ) {
        public static ReceiptOcrPolicy from(List<String> warnings, ReceiptOcrCandidates candidates) {
            int fieldCount = 0;
            java.util.ArrayList<String> fields = new java.util.ArrayList<>();
            if (candidates != null && candidates.dateCandidates() != null && !candidates.dateCandidates().isEmpty()) {
                fields.add("date");
                fieldCount++;
            }
            if (candidates != null && candidates.titleCandidates() != null && !candidates.titleCandidates().isEmpty()) {
                fields.add("title");
                fieldCount++;
            }
            if (candidates != null && candidates.amountCandidates() != null && !candidates.amountCandidates().isEmpty()) {
                fields.add("amount");
                fieldCount++;
            }
            int warningCount = warnings == null ? 0 : warnings.size();
            int score = Math.max(0, Math.min(100, 35 + (fieldCount * 20) - (warningCount * 10)));
            return new ReceiptOcrPolicy(score, warningCount > 0 || fieldCount < 3, List.copyOf(fields), warnings == null ? List.of() : List.copyOf(warnings));
        }
    }

    public record ReceiptOcrCandidates(
            List<LocalDate> dateCandidates,
            List<String> titleCandidates,
            List<BigDecimal> amountCandidates,
            List<ReceiptOcrCandidateDetail> candidateDetails
    ) {
        public ReceiptOcrCandidates(
                List<LocalDate> dateCandidates,
                List<String> titleCandidates,
                List<BigDecimal> amountCandidates
        ) {
            this(dateCandidates, titleCandidates, amountCandidates, List.of());
        }
    }

    public record ReceiptOcrCandidateDetail(
            String field,
            String value,
            int score,
            String sourceLine,
            String reason
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
