package com.comfortableledger.ledger.service.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.comfortableledger.ledger.service.importing.ImportTextService;

class ReceiptOcrServiceTest {
    private final ReceiptOcrService service = new ReceiptOcrService(mock(ImportTextService.class), "tesseract", "kor+eng");

    @Test
    void ignoresInformationalTesseractMessages() {
        assertThat(service.isInformationalTesseractMessage("Estimating resolution as 300")).isTrue();
        assertThat(service.isInformationalTesseractMessage("Tesseract failed to read file")).isFalse();
    }

    @Test
    void warnsWhenOcrTextIsBlank() {
        TextImportPreview preview = preview(BigDecimal.ZERO, "?먮룞 ?낅젰");

        List<String> warnings = service.qualityWarnings("", preview);

        assertThat(warnings).anyMatch(message -> message.contains("嫄곕옒 ?꾨낫"));
    }

    @Test
    void warnsWhenAmountOrMerchantIsUncertain() {
        TextImportPreview preview = preview(BigDecimal.ZERO, "?먮룞 ?낅젰");

        List<String> warnings = service.qualityWarnings("짧은 텍스트", preview);

        assertThat(warnings).anyMatch(message -> message.contains("吏㏃뒿?덈떎"));
        assertThat(warnings).anyMatch(message -> message.contains("湲덉븸 ?꾨낫"));
        assertThat(warnings).anyMatch(message -> message.contains("媛留뱀젏/?덈챸"));
    }

    @Test
    void cleanupStaleTempFilesDeletesOnlyOldReceiptOcrFiles(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.now();
        Path oldOcrTemp = Files.createFile(tempDir.resolve("receipt-ocr-old.png"));
        Path recentOcrTemp = Files.createFile(tempDir.resolve("receipt-ocr-recent.png"));
        Path unrelatedOldTemp = Files.createFile(tempDir.resolve("other-old.png"));
        Files.setLastModifiedTime(oldOcrTemp, FileTime.from(now.minus(Duration.ofDays(2))));
        Files.setLastModifiedTime(recentOcrTemp, FileTime.from(now));
        Files.setLastModifiedTime(unrelatedOldTemp, FileTime.from(now.minus(Duration.ofDays(2))));

        int deletedCount = service.cleanupStaleTempFiles(tempDir, now.minus(Duration.ofHours(24)));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(oldOcrTemp).doesNotExist();
        assertThat(recentOcrTemp).exists();
        assertThat(unrelatedOldTemp).exists();
    }

    @Test
    void buildsReceiptOcrCandidateOptions() {
        TextImportPreview preview = preview(new BigDecimal("9500"), "Americano");

        var candidates = service.candidateOptions("""
                STARBUCKS GANGNAM
                2026-06-26 12:34
                ?덈챸 ?④? ?섎웾 湲덉븸
                Americano 4,500 1 4,500
                Latte 5,000 1 5,000
                TOTAL 9,500WON
                """, preview);

        assertThat(candidates.dateCandidates()).contains(LocalDate.of(2026, 6, 26));
        assertThat(candidates.titleCandidates()).contains("Americano", "Latte");
        assertThat(candidates.amountCandidates()).contains(new BigDecimal("9500"), new BigDecimal("4500"), new BigDecimal("5000"));
    }

    private TextImportPreview preview(BigDecimal amount, String merchant) {
        return new TextImportPreview(
                "",
                TransactionType.EXPENSE,
                LocalDate.of(2026, 6, 26),
                amount,
                merchant,
                "",
                null,
                null,
                null
        );
    }
}
