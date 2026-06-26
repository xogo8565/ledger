package com.comfortableledger.ledger.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StringValuesTest {
    @Test
    void returnsFirstTrimmedNonBlankValue() {
        assertThat(StringValues.firstNonBlank(null, "  ", "  value  ", "next")).isEqualTo("value");
        assertThat(StringValues.firstNonBlank(null, " ")).isNull();
    }

    @Test
    void normalizesWhitespace() {
        assertThat(StringValues.normalizeWhitespace("  a\t b\n c  ")).isEqualTo("a b c");
        assertThat(StringValues.normalizeWhitespace(null)).isEmpty();
    }

    @Test
    void checksContainmentAndSortsByLength() {
        assertThat(StringValues.containsAny("hello ledger", "none", "ledger")).isTrue();
        assertThat(StringValues.sortedByLengthDesc(List.of("a", "abcd", "ab"))).containsExactly("abcd", "ab", "a");
    }
}
