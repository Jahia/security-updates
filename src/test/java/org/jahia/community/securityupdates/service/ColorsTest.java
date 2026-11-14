package org.jahia.community.securityupdates.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Colors")
class ColorsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"#0b3d91, #0B3D91", "#FFFFFF, #FFFFFF", "' #1b1b1b ', #1B1B1B"})
    void normalizes(String value, String expected) {
        assertThat(Colors.normalize(value)).contains(expected);
    }

    @ParameterizedTest(name = "[{0}] is not a colour")
    @NullAndEmptySource
    @ValueSource(strings = {"#FFF", "FFFFFF", "#GGGGGG", "#1234567", "red", "rgb(0,0,0)", "#12345"})
    void refusesOtherFormats(String value) {
        assertThat(Colors.normalize(value)).isEmpty();
    }

    @ParameterizedTest(name = "{0} on {1} = {2}")
    @CsvSource({
            "#000000, #FFFFFF, 21.0",
            "#FFFFFF, #000000, 21.0",
            "#FFFFFF, #FFFFFF, 1.0",
            "#777777, #888888, 1.26",
            "#FFFFFF, #0B3D91, 10.05",
            "#FFFFFF, #1B1B1B, 17.22",
            "#767676, #FFFFFF, 4.54"
    })
    void contrastRatioFollowsWcag(String a, String b, double expected) {
        assertThat(Colors.contrastRatio(a, b)).isCloseTo(expected, within(0.01));
    }

    @Test
    void sevenToOneThreshold() {
        assertThat(Colors.MIN_CONTRAST).isEqualTo(7.0);
        assertThat(Colors.hasEnoughContrast("#FFFFFF", "#0B3D91")).isTrue();
        assertThat(Colors.hasEnoughContrast("#777777", "#888888")).isFalse();
        // #595959 on white is 7.0:1, just enough
        assertThat(Colors.hasEnoughContrast("#595959", "#FFFFFF")).isTrue();
        assertThat(Colors.hasEnoughContrast("#5A5A5A", "#FFFFFF")).isFalse();
    }
}
