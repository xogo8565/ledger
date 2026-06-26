package com.comfortableledger.ledger.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class StringValues {
    private StringValues() {
    }

    public static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static String normalizeSearchKey(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToEmpty(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return null;
    }

    public static boolean containsAny(String text, String... words) {
        String source = emptyIfNull(text);
        if (words == null) {
            return false;
        }
        for (String word : words) {
            if (word != null && source.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> sortedByLengthDesc(Collection<String> words) {
        if (words == null) {
            return List.of();
        }
        return words.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}
