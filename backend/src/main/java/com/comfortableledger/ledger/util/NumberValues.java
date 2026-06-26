package com.comfortableledger.ledger.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumberValues {
    private NumberValues() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static BigDecimal parseDecimal(String value) {
        String normalized = normalizeNumericText(value);
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalized);
    }

    public static BigDecimal parseWonAmount(String value) {
        return parseDecimal(value);
    }

    public static BigDecimal percent(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100")).divide(denominator, scale, RoundingMode.HALF_UP);
    }

    private static String normalizeNumericText(String value) {
        return StringValues.emptyIfNull(value)
                .replace(",", "")
                .replace("원", "")
                .replace("₩", "")
                .trim();
    }
}
