package com.comfortableledger.ledger.util;

public final class SecretValues {
    private static final String MASK = "********";

    private SecretValues() {
    }

    public static String mask(String value) {
        String normalized = StringValues.trimToEmpty(value);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= 4) {
            return MASK;
        }
        return normalized.substring(0, 2) + MASK + normalized.substring(normalized.length() - 2);
    }

    public static String maskAll(String value) {
        return StringValues.trimToEmpty(value).isBlank() ? "" : MASK;
    }
}
