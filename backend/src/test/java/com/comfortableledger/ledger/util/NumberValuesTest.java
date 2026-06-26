package com.comfortableledger.ledger.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NumberValuesTest {
    @Test
    void parsesWonAmount() {
        assertThat(NumberValues.parseWonAmount(" 1,234,567원 ")).isEqualByComparingTo(new BigDecimal("1234567"));
        assertThat(NumberValues.parseWonAmount("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(NumberValues.parseWonAmount(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void handlesPositiveAndPercent() {
        assertThat(NumberValues.isPositive(new BigDecimal("1"))).isTrue();
        assertThat(NumberValues.isPositive(BigDecimal.ZERO)).isFalse();
        assertThat(NumberValues.percent(new BigDecimal("25"), new BigDecimal("200"), 1))
                .isEqualByComparingTo(new BigDecimal("12.5"));
    }
}
