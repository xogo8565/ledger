package com.comfortableledger.ledger.service.receipt;

import com.comfortableledger.ledger.dto.ImportDtos.TextImportPreview;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrCandidates;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrCandidateDetail;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrPolicy;
import com.comfortableledger.ledger.dto.ReceiptDtos.ReceiptOcrPreview;
import com.comfortableledger.ledger.service.importing.ImportTextService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptOcrService {
    private static final Logger log = LoggerFactory.getLogger(ReceiptOcrService.class);
    private static final String OCR_TEMP_PREFIX = "receipt-ocr-";
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STALE_TEMP_FILE_AGE = Duration.ofHours(24);
    private static final Pattern YEAR_DATE_CANDIDATE_PATTERN = Pattern.compile("\\b(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})\\b");
    private static final Pattern MONTH_DATE_CANDIDATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[./월\\s]+(\\d{1,2})\\s*(?:일)?\\b");
    private static final Pattern AMOUNT_CANDIDATE_PATTERN = Pattern.compile("([0-9][0-9,]{2,})\\s*(?:원|won)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}[:시]\\d{2}\\b");
    private static final Pattern RECEIPT_TOTAL_PATTERN = Pattern.compile(
            "(?:합계|총액|총 결제 금액|결제금액|받을금액|매출금액|승인금액|total)\\s*[: ]*([0-9,]+)\\s*(?:원|won)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BUSINESS_REGISTRATION_PATTERN = Pattern.compile("\\b\\d{3}[- ]?\\d{2}[- ]?\\d{5}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern CARD_MASK_PATTERN = Pattern.compile("\\b\\d{4}[-* ]?\\*{2,4}[-* ]?\\*{2,4}[-* ]?\\d{2,4}\\b");
    private static final List<String> TOTAL_AMOUNT_KEYWORDS = List.of("합계", "총액", "결제금액", "받을금액", "매출금액", "승인금액", "total");
    private static final List<String> ITEM_HEADER_NAME_WORDS = List.of("품명", "품목", "상품", "상품명", "메뉴", "제품");
    private static final List<String> ITEM_HEADER_UNIT_WORDS = List.of("단가", "수량", "단 위", "qty", "quantity");
    private static final List<String> ITEM_HEADER_AMOUNT_WORDS = List.of("금액", "금 액", "합계", "total", "amount");
    private static final List<String> NOISE_TITLE_WORDS = List.of(
            "영수증", "매출전표", "사업자", "대표", "주소", "전화", "tel", "품명", "단가", "수량", "금액",
            "합계", "총액", "결제", "승인", "카드", "부가세", "과세", "면세", "거스름", "받을금액", "total",
            "공급가액", "봉사료", "사업자번호", "주문번호", "승인번호"
    );

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
        Instant requestStartedAt = Instant.now();
        validateFile(file);
        String originalFilename = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
        long fileSize = file.getSize();
        Path tempFile = Files.createTempFile(OCR_TEMP_PREFIX, extension(file.getOriginalFilename()));
        try {
            file.transferTo(tempFile);
            OcrResult ocr = runTesseract(tempFile);
            ReceiptOcrPreview response = previewFromText(originalFilename, ocr.rawText(), ocr.warnings());
            log.info(
                    "receipt_ocr status=success file=\"{}\" sizeBytes={} durationMs={} tesseractMs={} rawTextLength={} warnings={} dateCandidates={} titleCandidates={} amountCandidates={}",
                    originalFilename,
                    fileSize,
                    elapsedMillis(requestStartedAt),
                    ocr.durationMillis(),
                    ocr.rawText().length(),
                    response.warnings().size(),
                    response.candidates().dateCandidates().size(),
                    response.candidates().titleCandidates().size(),
                    response.candidates().amountCandidates().size()
            );
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "receipt_ocr status=interrupted file=\"{}\" sizeBytes={} durationMs={} errorType={} message=\"{}\"",
                    originalFilename,
                    fileSize,
                    elapsedMillis(requestStartedAt),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "receipt_ocr status={} file=\"{}\" sizeBytes={} durationMs={} errorType={} message=\"{}\"",
                    exception instanceof ReceiptOcrTimeoutException ? "timeout" : "failed",
                    originalFilename,
                    fileSize,
                    elapsedMillis(requestStartedAt),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            throw exception;
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
        Instant tesseractStartedAt = Instant.now();
        ProcessBuilder builder = new ProcessBuilder(
                tesseractCommand,
                imagePath.toString(),
                "stdout",
                "-l",
                tesseractLanguage,
                "--psm",
                "6",
                "--oem",
                "1",
                "preserve_interword_spaces=1"
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
            throw new ReceiptOcrTimeoutException(
                    "OCR 처리 시간이 " + OCR_TIMEOUT.toSeconds() + "초를 초과했습니다. 더 밝고 선명한 영수증 사진으로 다시 시도하거나 직접 입력해 주세요."
            );
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
            warnings.add("OCR 텍스트를 찾지 못했습니다. 영수증 전체가 밝고 선명하게 보이도록 다시 촬영해 주세요.");
        }
        return new OcrResult(rawText, warnings, elapsedMillis(tesseractStartedAt));
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
        return qualityWarnings(rawText, preview, candidateOptions(rawText, preview));
    }

    List<String> qualityWarnings(String rawText, TextImportPreview preview, ReceiptOcrCandidates candidates) {
        List<String> warnings = new ArrayList<>();
        String normalized = rawText == null ? "" : rawText.trim();
        if (normalized.isBlank()) {
            warnings.add("거래 후보를 자동으로 채우지 못했습니다. 직접 입력하거나 더 선명한 사진으로 다시 시도해 주세요.");
            return warnings;
        }
        if (normalized.length() < 20) {
            warnings.add("인식된 텍스트가 짧습니다. 영수증 전체가 보이도록 다시 촬영하면 정확도가 올라갑니다.");
        }
        if (preview.amount() == null || BigDecimal.ZERO.compareTo(preview.amount()) == 0) {
            warnings.add("금액 후보를 찾지 못했습니다. OCR 원문에서 합계 또는 결제금액을 수정한 뒤 다시 분석해 주세요.");
        }
        if (preview.merchant() == null || preview.merchant().isBlank() || preview.merchant().equals("자동 입력")) {
            warnings.add("가맹점/품명 후보가 불확실합니다. OCR 원문을 확인해 주세요.");
        }
        if (candidates.amountCandidates().size() >= 4) {
            warnings.add("금액 후보가 여러 개입니다. 결제금액 또는 합계에 해당하는 금액을 선택해 주세요.");
        }
        if (!hasTotalAmountKeyword(normalized) && candidates.amountCandidates().size() > 1) {
            warnings.add("합계/결제금액 라벨을 찾지 못했습니다. 품목 금액이 거래 금액으로 선택됐는지 확인해 주세요.");
        }
        return warnings;
    }

    ReceiptOcrCandidates candidateOptions(String rawText, TextImportPreview preview) {
        String sourceText = rawText == null ? "" : rawText;
        List<LocalDate> dateCandidates = dateCandidates(sourceText, preview);
        List<String> titleCandidates = titleCandidates(sourceText, preview);
        List<BigDecimal> amountCandidates = amountCandidates(sourceText, preview);
        return new ReceiptOcrCandidates(
                dateCandidates,
                titleCandidates,
                amountCandidates,
                candidateDetails(sourceText, preview, dateCandidates, titleCandidates, amountCandidates)
        );
    }

    private List<ReceiptOcrCandidateDetail> candidateDetails(String rawText, TextImportPreview preview,
                                                            List<LocalDate> dates, List<String> titles,
                                                            List<BigDecimal> amounts) {
        Map<String, ReceiptOcrCandidateDetail> details = new LinkedHashMap<>();
        for (LocalDate date : dates) {
            String value = date.toString();
            String sourceLine = firstLineContaining(rawText, value).orElse("");
            int score = date.equals(preview.transactionDate()) ? 90 : (sourceLine.isBlank() ? 65 : 85);
            putCandidateDetail(details, new ReceiptOcrCandidateDetail(
                    "date",
                    value,
                    score,
                    sourceLine,
                    date.equals(preview.transactionDate()) ? "거래 초안 날짜" : "OCR 원문 날짜 후보"
            ));
        }
        for (String title : titles) {
            String sourceLine = firstLineContaining(rawText, title).orElse("");
            boolean previewTitle = title.equals(preview.merchant());
            boolean itemName = receiptItemNames(rawText).contains(title);
            putCandidateDetail(details, new ReceiptOcrCandidateDetail(
                    "title",
                    title,
                    previewTitle ? 95 : (itemName ? 80 : 60),
                    sourceLine,
                    previewTitle ? "거래 초안 내용" : (itemName ? "품목표 품명 후보" : "OCR 원문 가맹점 후보")
            ));
        }
        for (BigDecimal amount : amounts) {
            String value = amount.stripTrailingZeros().toPlainString();
            String sourceLine = firstLineContaining(rawText, value).orElse("");
            boolean previewAmount = preview.amount() != null && preview.amount().compareTo(amount) == 0;
            boolean labeledTotal = labeledTotalAmounts(rawText).stream().anyMatch(candidate -> candidate.compareTo(amount) == 0);
            boolean itemAmount = receiptItemAmounts(rawText).stream().anyMatch(candidate -> candidate.compareTo(amount) == 0);
            putCandidateDetail(details, new ReceiptOcrCandidateDetail(
                    "amount",
                    value,
                    previewAmount ? 100 : (labeledTotal ? 95 : (itemAmount ? 75 : 55)),
                    sourceLine,
                    previewAmount ? "거래 초안 금액" : (labeledTotal ? "합계/결제금액 라벨 후보" : (itemAmount ? "품목표 금액 후보" : "OCR 원문 금액 후보"))
            ));
        }
        return details.values().stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(18)
                .toList();
    }

    private void putCandidateDetail(Map<String, ReceiptOcrCandidateDetail> details, ReceiptOcrCandidateDetail detail) {
        String key = detail.field() + ":" + detail.value();
        details.putIfAbsent(key, detail);
    }

    private Optional<String> firstLineContaining(String rawText, String value) {
        String needle = value == null ? "" : value.trim();
        if (needle.isBlank()) {
            return Optional.empty();
        }
        String compactNeedle = needle.replaceAll("[,\\s]", "");
        return normalizedLines(rawText).stream()
                .filter(line -> line.contains(needle) || line.replaceAll("[,\\s]", "").contains(compactNeedle))
                .findFirst();
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

    private Optional<LocalDate> safeDate(int year, int month, int day) {
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private List<BigDecimal> amountCandidates(String rawText, TextImportPreview preview) {
        Set<BigDecimal> candidates = new LinkedHashSet<>();
        labeledTotalAmounts(rawText).forEach(candidates::add);
        if (preview.amount() != null && preview.amount().compareTo(BigDecimal.ZERO) > 0) {
            candidates.add(preview.amount());
        }
        receiptItemAmounts(rawText).forEach(candidates::add);
        generalAmounts(rawText).forEach(candidates::add);
        return candidates.stream().limit(6).toList();
    }

    private List<BigDecimal> labeledTotalAmounts(String rawText) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher matcher = RECEIPT_TOTAL_PATTERN.matcher(cleanAmountNoise(rawText));
        while (matcher.find()) {
            BigDecimal amount = NumberValues.parseWonAmount(matcher.group(1));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                amounts.add(amount);
            }
        }
        return amounts;
    }

    private List<BigDecimal> receiptItemAmounts(String rawText) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (String line : receiptItemLines(rawText)) {
            BigDecimal amount = lastAmountInLine(line);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                amounts.add(amount);
            }
        }
        return amounts;
    }

    private List<BigDecimal> generalAmounts(String rawText) {
        List<BigDecimal> amounts = new ArrayList<>();
        String amountSearchText = cleanAmountNoise(rawText);
        amountSearchText = YEAR_DATE_CANDIDATE_PATTERN.matcher(amountSearchText).replaceAll(" ");
        amountSearchText = MONTH_DATE_CANDIDATE_PATTERN.matcher(amountSearchText).replaceAll(" ");
        amountSearchText = TIME_PATTERN.matcher(amountSearchText).replaceAll(" ");
        Matcher matcher = AMOUNT_CANDIDATE_PATTERN.matcher(amountSearchText);
        while (matcher.find()) {
            BigDecimal amount = NumberValues.parseWonAmount(matcher.group(1));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                amounts.add(amount);
            }
        }
        return amounts;
    }

    private String cleanAmountNoise(String rawText) {
        String text = rawText == null ? "" : rawText;
        text = BUSINESS_REGISTRATION_PATTERN.matcher(text).replaceAll(" ");
        text = PHONE_PATTERN.matcher(text).replaceAll(" ");
        text = CARD_MASK_PATTERN.matcher(text).replaceAll(" ");
        return text;
    }

    private BigDecimal lastAmountInLine(String line) {
        Matcher matcher = AMOUNT_CANDIDATE_PATTERN.matcher(line);
        BigDecimal selected = BigDecimal.ZERO;
        while (matcher.find()) {
            selected = NumberValues.parseWonAmount(matcher.group(1));
        }
        return selected;
    }

    private List<String> titleCandidates(String rawText, TextImportPreview preview) {
        Set<String> candidates = new LinkedHashSet<>();
        if (preview.merchant() != null && !preview.merchant().isBlank() && !preview.merchant().equals("자동 입력")) {
            candidates.add(preview.merchant().trim());
        }
        receiptItemNames(rawText).forEach(candidates::add);
        merchantLineCandidates(rawText).forEach(candidates::add);
        return candidates.stream().limit(6).toList();
    }

    private List<String> receiptItemNames(String rawText) {
        List<String> names = new ArrayList<>();
        for (String line : receiptItemLines(rawText)) {
            String itemName = line.split("[0-9]", 2)[0].replaceAll("[·*]", "").trim();
            if (itemName.length() >= 2) {
                names.add(itemName);
            }
        }
        return names;
    }

    private List<String> receiptItemLines(String rawText) {
        List<String> lines = normalizedLines(rawText);
        List<String> itemLines = new ArrayList<>();
        boolean itemTableStarted = false;
        for (String line : lines) {
            String compact = line.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (!itemTableStarted && isItemHeader(compact)) {
                itemTableStarted = true;
                continue;
            }
            if (itemTableStarted && isItemTableEnd(line)) {
                break;
            }
            if (itemTableStarted && AMOUNT_CANDIDATE_PATTERN.matcher(line).find()) {
                itemLines.add(line);
            }
        }
        return itemLines;
    }

    private boolean isItemHeader(String compactLine) {
        return containsAny(compactLine, ITEM_HEADER_NAME_WORDS)
                && containsAny(compactLine, ITEM_HEADER_UNIT_WORDS)
                && containsAny(compactLine, ITEM_HEADER_AMOUNT_WORDS);
    }

    private boolean isItemTableEnd(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "합계", "총액", "결제", "받을금액", "거스름", "부가세", "과세", "면세", "total");
    }

    private List<String> merchantLineCandidates(String rawText) {
        List<String> candidates = new ArrayList<>();
        for (String line : normalizedLines(rawText)) {
            if (AMOUNT_CANDIDATE_PATTERN.matcher(line).find()) continue;
            if (YEAR_DATE_CANDIDATE_PATTERN.matcher(line).find() || MONTH_DATE_CANDIDATE_PATTERN.matcher(line).find()) continue;
            if (BUSINESS_REGISTRATION_PATTERN.matcher(line).find() || PHONE_PATTERN.matcher(line).find()) continue;
            if (isNoiseTitleLine(line)) continue;
            if (line.length() >= 2 && line.length() <= 40) {
                candidates.add(line);
            }
        }
        return candidates;
    }

    private List<String> normalizedLines(String rawText) {
        return (rawText == null ? "" : rawText).lines()
                .map(String::trim)
                .map(line -> line.replaceAll("\\s+", " "))
                .filter(line -> !line.isBlank())
                .toList();
    }

    private boolean isNoiseTitleLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return NOISE_TITLE_WORDS.stream().anyMatch(normalized::contains);
    }

    private boolean hasTotalAmountKeyword(String rawText) {
        String normalized = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        return TOTAL_AMOUNT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(word -> text.contains(word.replaceAll("\\s+", "")));
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

    private static long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private static final class ReceiptOcrTimeoutException extends IllegalStateException {
        private ReceiptOcrTimeoutException(String message) {
            super(message);
        }
    }

    public ReceiptOcrPreview reparse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("OCR raw text is required");
        }
        Instant startedAt = Instant.now();
        ReceiptOcrPreview response = previewFromText("edited-ocr-text", rawText, List.of());
        log.info(
                "receipt_ocr_reparse status=success durationMs={} rawTextLength={} warnings={} dateCandidates={} titleCandidates={} amountCandidates={}",
                elapsedMillis(startedAt),
                rawText.length(),
                response.warnings().size(),
                response.candidates().dateCandidates().size(),
                response.candidates().titleCandidates().size(),
                response.candidates().amountCandidates().size()
        );
        return response;
    }

    private ReceiptOcrPreview previewFromText(String originalFilename, String rawText, List<String> baseWarnings) {
        TextImportPreview preview = importTextService.preview(rawText);
        ReceiptOcrCandidates candidates = candidateOptions(rawText, preview);
        List<String> warnings = new ArrayList<>(baseWarnings == null ? List.of() : baseWarnings);
        warnings.addAll(qualityWarnings(rawText, preview, candidates));
        return new ReceiptOcrPreview(
                originalFilename,
                rawText,
                preview,
                warnings,
                candidates,
                ReceiptOcrPolicy.from(warnings, candidates)
        );
    }

    private record OcrResult(String rawText, List<String> warnings, long durationMillis) {
    }
}
