package org.jahia.community.securityupdates.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContrastSuggestions")
class ContrastSuggestionsTest {

    /** The table shared with the Jest tests (src/javascript/Settings/__tests__): both implementations must agree. */
    static Stream<Arguments> sharedTable() throws IOException {
        try (InputStream in = ContrastSuggestionsTest.class.getResourceAsStream("/contrast-suggestions.json")) {
            JSONArray cases = new JSONArray(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<Arguments> arguments = new ArrayList<>();
            for (int i = 0; i < cases.length(); i++) {
                JSONObject c = cases.getJSONObject(i);
                arguments.add(Arguments.of(c.getString("background"), c.getString("text"), c.getString("ratio"),
                        c.optJSONObject("textSuggestion"), c.optJSONObject("backgroundSuggestion")));
            }
            return arguments.stream();
        }
    }

    @ParameterizedTest(name = "{1} on {0} ({2}:1)")
    @MethodSource("sharedTable")
    @DisplayName("matches the shared table")
    void sharedTable(String background, String text, String ratio, JSONObject textSuggestion, JSONObject backgroundSuggestion) {
        assertThat(ContrastSuggestions.formatRatio(Colors.contrastRatio(background, text))).isEqualTo(ratio);
        assertSuggestion(ContrastSuggestions.suggest(background, text), textSuggestion);
        assertSuggestion(ContrastSuggestions.suggest(text, background), backgroundSuggestion);
    }

    private static void assertSuggestion(Optional<ContrastSuggestions.Suggestion> actual, JSONObject expected) {
        if (expected == null) {
            assertThat(actual).isEmpty();
            return;
        }
        assertThat(actual).isPresent();
        assertThat(actual.get().getValue()).isEqualTo(expected.getString("value"));
        assertThat(actual.get().getRatio()).isEqualTo(expected.getString("ratio"));
    }

    @Test
    @DisplayName("a suggestion always reaches 7:1 after rounding, and keeps the other colour")
    void suggestionsPass() throws IOException {
        sharedTable().forEach(arguments -> {
            String background = (String) arguments.get()[0];
            String text = (String) arguments.get()[1];
            ContrastSuggestions.suggest(background, text)
                    .ifPresent(s -> assertThat(Colors.contrastRatio(background, s.getValue())).isGreaterThanOrEqualTo(7.0));
            ContrastSuggestions.suggest(text, background)
                    .ifPresent(s -> assertThat(Colors.contrastRatio(text, s.getValue())).isGreaterThanOrEqualTo(7.0));
        });
    }

    @Test
    @DisplayName("an already passing pair gets no suggestion")
    void passingPair() {
        assertThat(ContrastSuggestions.suggest("#1B1B1B", "#FFFFFF")).isEmpty();
    }

    @Test
    @DisplayName("keeps the hue: a dark blue text on dark blue becomes a light blue")
    void keepsHue() {
        String light = ContrastSuggestions.suggest("#0B3D91", "#1E5BC6").orElseThrow().getValue();
        int r = Integer.parseInt(light.substring(1, 3), 16);
        int b = Integer.parseInt(light.substring(5, 7), 16);
        assertThat(b).isGreaterThan(r);
    }

    @Test
    @DisplayName("the ratio is shown rounded down to two decimals")
    void formatRatio() {
        assertThat(ContrastSuggestions.formatRatio(6.999)).isEqualTo("6.99");
        assertThat(ContrastSuggestions.formatRatio(7.0)).isEqualTo("7.00");
        assertThat(ContrastSuggestions.formatRatio(17.2244)).isEqualTo("17.22");
    }
}
