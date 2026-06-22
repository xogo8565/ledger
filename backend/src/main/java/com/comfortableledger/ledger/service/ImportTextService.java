package com.comfortableledger.ledger.service;

import com.comfortableledger.ledger.domain.TransactionType;
import com.comfortableledger.ledger.web.ApiDtos.TextImportPreview;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ImportTextService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9,]+)\\s*원");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})[./월]\\s*(\\d{1,2})");

    public TextImportPreview preview(String rawText) {
        BigDecimal amount = extractAmount(rawText);
        LocalDate date = extractDate(rawText);
        String merchant = extractMerchant(rawText);
        TransactionType type = rawText.contains("입금") || rawText.contains("승인취소")
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;
        return new TextImportPreview(rawText, type, date, amount, merchant, "문자 자동 입력 후보");
    }

    private BigDecimal extractAmount(String rawText) {
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText);
        BigDecimal result = BigDecimal.ZERO;
        while (matcher.find()) {
            result = new BigDecimal(matcher.group(1).replace(",", ""));
        }
        return result;
    }

    private LocalDate extractDate(String rawText) {
        Matcher matcher = DATE_PATTERN.matcher(rawText);
        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            int day = Integer.parseInt(matcher.group(2));
            return LocalDate.of(LocalDate.now().getYear(), month, day);
        }
        return LocalDate.now();
    }

    private String extractMerchant(String rawText) {
        String normalized = rawText.replaceAll("\\s+", " ").trim();
        String[] tokens = normalized.split(" ");
        if (tokens.length == 0) {
            return "자동 입력";
        }
        return tokens[Math.max(0, tokens.length - 1)].replaceAll("[0-9,원]", "");
    }
}
