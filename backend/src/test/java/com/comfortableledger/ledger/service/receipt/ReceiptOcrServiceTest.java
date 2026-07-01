package com.comfortableledger.ledger.service.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrPolicy;
import com.comfortableledger.ledger.service.importing.ImportTextService;
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

class ReceiptOcrServiceTest {
    private final ImportTextService importTextService = mock(ImportTextService.class);
    private final ReceiptOcrService service = new ReceiptOcrService(importTextService, "tesseract", "kor+eng");

    @Test
    void ignoresInformationalTesseractMessages() {
        assertThat(service.isInformationalTesseractMessage("Estimating resolution as 300")).isTrue();
        assertThat(service.isInformationalTesseractMessage("Detected 64 diacritics")).isTrue();
        assertThat(service.isInformationalTesseractMessage("Tesseract failed to read file")).isFalse();
    }

    @Test
    void warnsWhenOcrTextIsBlank() {
        TextImportPreview preview = preview(BigDecimal.ZERO, "자동 입력");

        List<String> warnings = service.qualityWarnings("", preview);

        assertThat(warnings).anyMatch(message -> message.contains("거래 후보를 자동으로 채우지 못했습니다"));
    }

    @Test
    void warnsWhenAmountOrMerchantIsUncertain() {
        TextImportPreview preview = preview(BigDecimal.ZERO, "자동 입력");

        List<String> warnings = service.qualityWarnings("짧은 텍스트", preview);

        assertThat(warnings).anyMatch(message -> message.contains("인식된 텍스트가 짧습니다"));
        assertThat(warnings).anyMatch(message -> message.contains("금액 후보를 찾지 못했습니다"));
        assertThat(warnings).anyMatch(message -> message.contains("가맹점/품명 후보가 불확실합니다"));
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
    void buildsReceiptOcrCandidateOptionsWithTotalAmountPriority() {
        TextImportPreview preview = preview(new BigDecimal("9500"), "Americano");

        var candidates = service.candidateOptions("""
                STARBUCKS GANGNAM
                사업자번호 123-45-67890
                전화 02-1234-5678
                2026-06-26 12:34
                품명 단가 수량 금액
                Americano 4,500 1 4,500
                Latte 5,000 1 5,000
                합계 9,500원
                카드번호 1234-****-****-5678
                """, preview);

        assertThat(candidates.dateCandidates()).contains(LocalDate.of(2026, 6, 26));
        assertThat(candidates.titleCandidates()).contains("Americano", "Latte");
        assertThat(candidates.titleCandidates()).doesNotContain("사업자번호 123-45-67890");
        assertThat(candidates.amountCandidates()).startsWith(new BigDecimal("9500"));
        assertThat(candidates.amountCandidates()).contains(new BigDecimal("4500"), new BigDecimal("5000"));
        assertThat(candidates.amountCandidates()).doesNotContain(new BigDecimal("1234567890"), new BigDecimal("212345678"));
        assertThat(candidates.candidateDetails())
                .anySatisfy(detail -> {
                    assertThat(detail.field()).isEqualTo("amount");
                    assertThat(detail.value()).isEqualTo("9500");
                    assertThat(detail.reason()).contains("거래 초안 금액");
                    assertThat(detail.score()).isGreaterThanOrEqualTo(90);
                })
                .anySatisfy(detail -> {
                    assertThat(detail.field()).isEqualTo("title");
                    assertThat(detail.value()).isEqualTo("Americano");
                    assertThat(detail.sourceLine()).contains("Americano");
                });
    }

    @Test
    void buildsPolicyFromWarningsAndRecognizedCandidates() {
        var candidates = service.candidateOptions("""
                카페테스트
                2026-06-26
                합계 9,500원
                """, preview(new BigDecimal("9500"), "카페테스트"));

        ReceiptOcrPolicy policy = ReceiptOcrPolicy.from(List.of(), candidates);

        assertThat(policy.recognizedFields()).containsExactly("date", "title", "amount");
        assertThat(policy.needsReview()).isFalse();
        assertThat(policy.confidenceScore()).isGreaterThanOrEqualTo(90);
    }

    @Test
    void reparsesEditedOcrTextWithCandidatesAndPolicy() {
        String rawText = """
                카페테스트
                2026-06-26
                합계 9,500원
                """;
        when(importTextService.preview(rawText)).thenReturn(preview(new BigDecimal("9500"), "카페테스트"));

        var result = service.reparse(rawText);

        assertThat(result.preview().merchant()).isEqualTo("카페테스트");
        assertThat(result.candidates().dateCandidates()).contains(LocalDate.of(2026, 6, 26));
        assertThat(result.candidates().amountCandidates()).contains(new BigDecimal("9500"));
        assertThat(result.candidates().candidateDetails()).isNotEmpty();
        assertThat(result.policy().recognizedFields()).contains("date", "title", "amount");
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
