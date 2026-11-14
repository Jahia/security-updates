package org.jahia.community.securityupdates.feed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UntrustedText")
class UntrustedTextTest {

    @Test
    @DisplayName("null, empty and blank text read as absent")
    void absent() {
        assertThat(UntrustedText.clean(null, 10)).isNull();
        assertThat(UntrustedText.clean("", 10)).isNull();
        assertThat(UntrustedText.clean(" \t\n ", 10)).isNull();
        assertThat(UntrustedText.clean("\u0000\u0007", 10)).isNull();
    }

    static Stream<Arguments> controlAndFormatCharacters() {
        return Stream.of(
                Arguments.of("a\tb\nc\rd", "a b c d"),
                Arguments.of("  padded  ", "padded"),
                Arguments.of("bell\u0007 removed", "bell removed"),
                Arguments.of("rtl‮override", "rtloverride"),
                Arguments.of("zero​width", "zerowidth"),
                Arguments.of("bom﻿", "bom"),
                Arguments.of("del\u007F", "del"),
                Arguments.of("c1\u0085control", "c1control"));
    }

    @ParameterizedTest(name = "[{index}]")
    @MethodSource("controlAndFormatCharacters")
    @DisplayName("removes control and format characters, turns tabs and line breaks into spaces")
    void removesControlAndFormatCharacters(String raw, String expected) {
        assertThat(UntrustedText.clean(raw, 100)).isEqualTo(expected);
    }

    @Test
    @DisplayName("keeps HTML as text and characters outside the BMP")
    void keepsTextAsIs() {
        assertThat(UntrustedText.clean("<b>x</b> 🔒", 100)).isEqualTo("<b>x</b> 🔒");
    }

    @Test
    @DisplayName("truncates to the maximum length")
    void truncates() {
        assertThat(UntrustedText.clean("x".repeat(50), 10)).hasSize(10);
        assertThat(UntrustedText.clean("a" + "\u0000".repeat(20) + "bc", 2)).isEqualTo("ab");
    }
}
