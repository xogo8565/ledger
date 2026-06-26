package com.comfortableledger.ledger.service.receipt;

import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrPreview;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrCandidates;
import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.util.NumberValues;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.comfortableledger.ledger.service.importing.ImportTextService;

@Service
public class ReceiptOcrService {
    private static final String OCR_TEMP_PREFIX = "receipt-ocr-";
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STALE_TEMP_FILE_AGE = Duration.ofHours(24);
    private static final Pattern YEAR_DATE_CANDIDATE_PATTERN = Pattern.compile("\\b(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})\\b");
    private static final Pattern MONTH_DATE_CANDIDATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[./월]\\s*(\\d{1,2})\\s*(?:일)?\\b");
    private static final Pattern AMOUNT_CANDIDATE_PATTERN = Pattern.compile("([0-9][0-9,]{2,})\\s*(?:원|won)?", Pattern.CASE_INSENSITIVE);

    private final ImportTextService importTextService;
    private final String tesseractCommand;
    private final String tesseractLanguage;

    public ReceiptOcrService(ImportTextService importTextService,
                             @Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand,
                             @Value("${app.ocr.tesseract-language:kor+eng}") String tesseractLanguage) {
        this.importTextService = importTextService;
        this.tesseractCommand = tesseractCommand;
        this.tesseractLanguage = tesseractLanguage;
    }

    @PostConstruct
    void cleanupStaleTempFilesOnStartup() {
        try {
            cleanupStaleTempFiles(
                    Path.of(System.getProperty("java.io.tmpdir")),
                    Instant.now().minus(STALE_TEMP_FILE_AGE)
            );
        } catch (IOException ignored) {
            // OCR temp cleanup must not prevent application startup.
        }
    }

    public ReceiptOcrPreview preview(MultipartFile file) throws IOException, InterruptedException {
        validateFile(file);
        Path tempFile = Files.createTempFile(OCR_TEMP_PREFIX, extension(file.getOriginalFilename()));
        try {
            file.transferTo(tempFile);
            OcrResult ocr = runTesseract(tempFile);
            TextImportPreview preview = importTextService.preview(ocr.rawText());
            List<String> warnings = new ArrayList<>(ocr.warnings());
            warnings.addAll(qualityWarnings(ocr.rawText(), preview));
            return new ReceiptOcrPreview(
                    file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename(),
                    ocr.rawText(),
                    preview,
                    warnings,
                    candidateOptions(ocr.rawText(), preview)
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    int cleanupStaleTempFiles(Path tempDir, Instant deleteBefore) throws IOException {
        if (tempDir == null || deleteBefore == null || !Files.isDirectory(tempDir)) {
            return 0;
        }
        int deletedCount = 0;
        try (Stream<Path> paths = Files.list(tempDir)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!path.getFileName().toString().startsWith(OCR_TEMP_PREFIX)) {
                    continue;
                }
                Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();
                if (modifiedAt.isBefore(deleteBefore)) {
                    Files.deleteIfExists(path);
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }

    private OcrResult runTesseract(Path imagePath) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                tesseractCommand,
                imagePath.toString(),
                "stdout",
                "-l",
                tesseractLanguage
        );
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Tesseract OCR engine is not available. Install tesseract and language data or set app.ocr.tesseract-command.",
                    exception
            );
        }

        boolean finished = process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract OCR timed out");
        }

        String rawText = new String(process.getInputStream().readAllBytes()).trim();
        String errorText = new String(process.getErrorStream().readAllBytes()).trim();
        List<String> warnings = new ArrayList<>();
        if (!errorText.isBlank() && !isInformationalTesseractMessage(errorText)) {
            warnings.add(errorText);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Tesseract OCR failed: " + errorText);
        }
        if (rawText.isBlank()) {
            warnings.add("OCR ?띿뒪?몃? 李얠? 紐삵뻽?듬땲?? ?ъ쭊????諛앷퀬 ?좊챸?섍쾶 珥ъ쁺??二쇱꽭??");
        }
        return new OcrResult(rawText, warnings);
    }

    boolean isInformationalTesseractMessage(String errorText) {
        String normalized = errorText.toLowerCase(Locale.ROOT);
        return normalized.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .allMatch(line -> line.startsWith("estimating resolution")
                        || line.startsWith("detected")
                        || line.startsWith("empty page"));
    }

    List<String> qualityWarnings(String rawText, TextImportPreview preview) {
        List<String> warnings = new ArrayList<>();
        String normalized = rawText == null ? "" : rawText.trim();
        if (normalized.isBlank()) {
            warnings.add("嫄곕옒 ?꾨낫瑜??먮룞?쇰줈 梨꾩슦吏 紐삵뻽?듬땲?? 吏곸젒 ?낅젰?쇰줈 ?꾪솚?섍굅???ㅻⅨ ?ъ쭊?쇰줈 ?ㅼ떆 ?쒕룄??二쇱꽭??");
            return warnings;
        }
        if (normalized.length() < 20) {
            warnings.add("?몄떇???띿뒪?멸? 吏㏃뒿?덈떎. ?곸닔利??꾩껜媛 蹂댁씠?꾨줉 ?ㅼ떆 珥ъ쁺?섎㈃ ?뺥솗?꾧? ?щ씪媛묐땲??");
        }
        if (preview.amount() == null || BigDecimal.ZERO.compareTo(preview.amount()) == 0) {
            warnings.add("湲덉븸 ?꾨낫瑜?李얠? 紐삵뻽?듬땲?? OCR ?먮Ц?먯꽌 ?⑷퀎 ?먮뒗 湲덉븸???섏젙?????ㅼ떆 遺꾩꽍??二쇱꽭??");
        }
        if (preview.merchant() == null || preview.merchant().isBlank() || preview.merchant().equals("?먮룞 ?낅젰")) {
            warnings.add("媛留뱀젏/?덈챸 ?꾨낫媛 遺덊솗?ㅽ빀?덈떎. OCR ?먮Ц???뺤씤??二쇱꽭??");
        }
        return warnings;
    }

    ReceiptOcrCandidates candidateOptions(String rawText, TextImportPreview preview) {
        String sourceText = rawText == null ? "" : rawText;
        return new ReceiptOcrCandidates(
                dateCandidates(sourceText, preview),
                titleCandidates(sourceText, preview),
                amountCandidates(sourceText, preview)
        );
    }

    private List<LocalDate> dateCandidates(String rawText, TextImportPreview preview) {
        Set<LocalDate> candidates = new LinkedHashSet<>();
        if (preview.transactionDate() != null) {
            candidates.add(preview.transactionDate());
        }
        Matcher yearMatcher = YEAR_DATE_CANDIDATE_PATTERN.matcher(rawText);
        while (yearMatcher.find()) {
            safeDate(
                    Integer.parseInt(yearMatcher.group(1)),
                    Integer.parseInt(yearMatcher.group(2)),
                    Integer.parseInt(yearMatcher.group(3))
            ).ifPresent(candidates::add);
        }
        Matcher monthMatcher = MONTH_DATE_CANDIDATE_PATTERN.matcher(rawText);
        while (monthMatcher.find()) {
            safeDate(
                    LocalDate.now().getYear(),
                    Integer.parseInt(monthMatcher.group(1)),
                    Integer.parseInt(monthMatcher.group(2))
            ).ifPresent(candidates::add);
        }
        return candidates.stream().limit(6).toList();
    }

    private java.util.Optional<LocalDate> safeDate(int year, int month, int day) {
        try {
            return java.util.Optional.of(LocalDate.of(year, month, day));
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private List<BigDecimal> amountCandidates(String rawText, TextImportPreview preview) {
        Set<BigDecimal> candidates = new LinkedHashSet<>();
        if (preview.amount() != null && preview.amount().compareTo(BigDecimal.ZERO) > 0) {
            candidates.add(preview.amount());
        }
        String amountSearchText = YEAR_DATE_CANDIDATE_PATTERN.matcher(rawText).replaceAll(" ");
        amountSearchText = MONTH_DATE_CANDIDATE_PATTERN.matcher(amountSearchText).replaceAll(" ");
        Matcher matcher = AMOUNT_CANDIDATE_PATTERN.matcher(amountSearchText);
        while (matcher.find()) {
            BigDecimal amount = NumberValues.parseWonAmount(matcher.group(1));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                candidates.add(amount);
            }
        }
        return candidates.stream().limit(6).toList();
    }

    private List<String> titleCandidates(String rawText, TextImportPreview preview) {
        Set<String> candidates = new LinkedHashSet<>();
        if (preview.merchant() != null && !preview.merchant().isBlank() && !preview.merchant().equals("?먮룞 ?낅젰")) {
            candidates.add(preview.merchant().trim());
        }
        List<String> lines = rawText.lines()
                .map(String::trim)
                .map(line -> line.replaceAll("\\s+", " "))
                .filter(line -> !line.isBlank())
                .toList();
        boolean itemTableStarted = false;
        for (String line : lines) {
            String compact = line.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (!itemTableStarted && compact.contains("?덈챸") && compact.contains("?④?")
                    && compact.contains("?섎웾") && compact.contains("湲덉븸")) {
                itemTableStarted = true;
                continue;
            }
            if (itemTableStarted && isItemTableEnd(line)) {
                break;
            }
            if (itemTableStarted) {
                String itemName = line.split("[0-9]", 2)[0].replaceAll("[쨌*]", "").trim();
                if (!itemName.isBlank()) {
                    candidates.add(itemName);
                }
            }
        }
        for (String line : lines) {
            if (AMOUNT_CANDIDATE_PATTERN.matcher(line).find()) continue;
            if (YEAR_DATE_CANDIDATE_PATTERN.matcher(line).find() || MONTH_DATE_CANDIDATE_PATTERN.matcher(line).find()) continue;
            if (isNoiseTitleLine(line)) continue;
            if (line.length() >= 2 && line.length() <= 40) {
                candidates.add(line);
            }
        }
        return candidates.stream().limit(6).toList();
    }

    private boolean isItemTableEnd(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "합계", "총액", "결제", "받을금액", "거스름", "부가세", "과세", "면세", "total");
    }

    private boolean isNoiseTitleLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return containsAny(normalized,
                "영수증", "매출전표", "사업자", "대표", "주소", "전화", "tel",
                "품명", "단가", "수량", "금액", "합계", "총액", "결제", "승인",
                "카드", "부가세", "과세", "면세", "total"
        );
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Receipt image is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Receipt OCR file must be an image");
        }
    }

    private String extension(String filename) {
        if (filename == null) return ".img";
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) return ".img";
        return filename.substring(index).replaceAll("[^a-zA-Z0-9.]", "");
    }

    private record OcrResult(String rawText, List<String> warnings) {
    }
}
