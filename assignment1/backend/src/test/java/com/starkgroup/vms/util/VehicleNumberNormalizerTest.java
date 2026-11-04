package com.starkgroup.vms.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleNumberNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "'dha-1234',          'DHA-1234'",
            "'  dha   1234  ',    'DHA 1234'",
            "'dha - metro -- 12', 'DHA-METRO-12'",
            "'Ka 01 ab 1234',     'KA 01 AB 1234'",
    })
    void toDisplay_cleansAndUppercases(String raw, String expected) {
        assertThat(VehicleNumberNormalizer.toDisplay(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'DHA-1234'",
            "'dha 1234'",
            "'DhA1234'",
            "' d-h-a 12 34 '",
    })
    void toKey_treatsFormattingVariantsAsSameNumber(String raw) {
        assertThat(VehicleNumberNormalizer.toKey(raw)).isEqualTo("DHA1234");
    }

    @Test
    void nullSafe() {
        assertThat(VehicleNumberNormalizer.toDisplay(null)).isNull();
        assertThat(VehicleNumberNormalizer.toKey(null)).isEmpty();
    }
}
