package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ImportTextService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9,]+)\\s*원");
    private static final Pattern YEAR_DATE_PATTERN = Pattern.compile("(20\\d{2})[./-]\\s*(\\d{1,2})[./-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(\\d{1,2})[./월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern SUPPORTING_AMOUNT_PATTERN = Pattern.compile("(잔액|누적|합계|한도|사용가능|총액)\\s*[: ]*([0-9,]+)\\s*원?");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}[:시]\\d{2}\\b");
    private static final Pattern CARD_SUFFIX_PATTERN = Pattern.compile("\\b\\d{2,4}[-*]\\*{2,4}\\b|\\(\\d{3,4}\\)");
    private static final List<String> ISSUER_WORDS = List.of(
            "국민", "KB", "신한", "삼성", "현대", "롯데", "우리", "하나", "NH", "농협", "BC", "비씨",
            "카카오", "토스", "케이뱅크", "국민은행", "신한은행", "우리은행", "하나은행", "농협은행"
    );
    private static final List<String> NOISE_WORDS = List.of(
            "카드", "체크", "체크카드", "신용", "승인", "사용", "이용", "출금", "입금", "취소", "승인취소",
            "환불", "일시불", "국내", "해외", "결제", "전자금융", "자동입금", "입출금", "알림"
    );

    public TextImportPreview preview(String rawText) {
        String normalized = normalize(rawText);
        String primaryText = stripSupportingAmounts(normalized);
        BigDecimal amount = extractAmount(primaryText);
        LocalDate date = extractDate(primaryText);
        TransactionType type = extractType(primaryText);
        String merchant = extractMerchant(primaryText);
        return new TextImportPreview(rawText, type, date, amount, merchant, importMemo(primaryText, type));
    }

    private BigDecimal extractAmount(String rawText) {
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        }
        return BigDecimal.ZERO;
    }

    private LocalDate extractDate(String rawText) {
        Matcher yearMatcher = YEAR_DATE_PATTERN.matcher(rawText);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            int month = Integer.parseInt(yearMatcher.group(2));
            int day = Integer.parseInt(yearMatcher.group(3));
            return LocalDate.of(year, month, day);
        }
        Matcher monthMatcher = MONTH_DATE_PATTERN.matcher(rawText);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            int day = Integer.parseInt(monthMatcher.group(2));
            return LocalDate.of(LocalDate.now().getYear(), month, day);
        }
        return LocalDate.now();
    }

    private TransactionType extractType(String rawText) {
        if (containsAny(rawText, "승인취소", "취소", "환불", "입금")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String extractMerchant(String rawText) {
        String candidate = rawText;
        candidate = candidate.replaceAll("\\[[^\\]]+]", " ");
        candidate = YEAR_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = MONTH_DATE_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = AMOUNT_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = TIME_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = CARD_SUFFIX_PATTERN.matcher(candidate).replaceAll(" ");
        candidate = candidate.replaceAll("승인번호\\s*\\S+", " ");
        candidate = candidate.replaceAll("[()\\[\\],]", " ");
        for (String word : sortedByLengthDesc(ISSUER_WORDS)) {
            candidate = candidate.replaceAll("(?i)" + Pattern.quote(word), " ");
        }
        for (String word : sortedByLengthDesc(NOISE_WORDS)) {
            candidate = candidate.replaceAll(Pattern.quote(word), " ");
        }
        candidate = candidate.replaceAll("\\s+", " ").trim();
        if (!candidate.isBlank()) {
            return candidate;
        }
        return "자동 입력";
    }

    private String stripSupportingAmounts(String rawText) {
        return SUPPORTING_AMOUNT_PATTERN.matcher(rawText).replaceAll(" ");
    }

    private String normalize(String rawText) {
        return rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
    }

    private String importMemo(String rawText, TransactionType type) {
        if (type == TransactionType.INCOME && containsAny(rawText, "취소", "환불")) {
            return "문자 자동 입력 후보 · 취소/환불";
        }
        if (type == TransactionType.INCOME) {
            return "문자 자동 입력 후보 · 입금";
        }
        if (containsAny(rawText, "체크", "출금")) {
            return "문자 자동 입력 후보 · 체크/출금";
        }
        return "문자 자동 입력 후보 · 카드 승인";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<String> sortedByLengthDesc(List<String> words) {
        return words.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}
