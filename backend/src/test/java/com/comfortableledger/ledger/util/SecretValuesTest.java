package com.comfortableledger.ledger.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretValuesTest {
    @Test
    void masksSecretValue() {
        assertThat(SecretValues.mask("abcdef123456")).isEqualTo("ab********56");
        assertThat(SecretValues.mask("1234")).isEqualTo("********");
        assertThat(SecretValues.mask(" ")).isEmpty();
    }

    @Test
    void masksAllCharacters() {
        assertThat(SecretValues.maskAll("secret")).isEqualTo("********");
        assertThat(SecretValues.maskAll(null)).isEmpty();
    }
}
