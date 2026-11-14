package org.jahia.community.securityupdates.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Versions")
class VersionsTest {

    @ParameterizedTest(name = "{0} is well formed")
    @ValueSource(strings = {"1", "1.0", "3.10.0", "8.2.3.7", "1.0.0-SNAPSHOT", "4.13.1-jahia8-1", "0.0.1.alpha", "2.2.0.wemfix"})
    void acceptsRealStoreVersions(String version) {
        assertThat(Versions.isWellFormed(version)).isTrue();
    }

    @ParameterizedTest(name = "[{0}] is malformed")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "v1.0", "latest", "1.0 beta", "1.0;rm", "-1", "99999999999.0", "1..0"})
    void rejectsMalformedVersions(String version) {
        assertThat(Versions.isWellFormed(version)).isFalse();
    }

    /** The pattern before it was simplified (Sonar S5998): the new one must accept exactly the same strings. */
    private static final java.util.regex.Pattern LEGACY =
            java.util.regex.Pattern.compile("\\d+(\\.\\d+)*([.\\-_+][0-9A-Za-z][0-9A-Za-z.\\-_+]*)?");

    @ParameterizedTest(name = "[{0}] is judged as before")
    @ValueSource(strings = {"1", "1.0", "1.2.3.4a", "1.0.0+build.5", "1.0..1", "1..0", "1.-", "1.0-", "1_a", "1+", "1.a.b",
            "12.34.56.78.90", "1.0.0-rc.1+meta", "1.0.", ".1", "1.0.0-SNAPSHOT", "1a", "1.0a", "1-0", "0.0.0.0.0.0.0.0.0"})
    void acceptsTheSameVersionsAsTheLegacyPattern(String version) {
        boolean legacy = LEGACY.matcher(version).matches();

        assertThat(Versions.isWellFormed(version)).isEqualTo(legacy);
    }

    @Test
    void qualifierMayFollowAPlusSign() {
        assertThat(Versions.isWellFormed("1.0.0+build")).isTrue();
    }

    @Test
    void rejectsOverlongVersion() {
        assertThat(Versions.isWellFormed("1." + "0".repeat(100))).isFalse();
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({
            "3.10.0, 3.9.0, 1",
            "3.9.0, 3.10.0, -1",
            "2.24.0, 2.24.0, 0",
            "2.24, 2.24.0, 0",
            "1.0.0-SNAPSHOT, 1.0.0, -1",
            "8.2.3.7, 8.2.3.10, -1",
            "4.13.1-jahia8-2, 4.13.1-jahia8-1, 1",
            "1.0.0-beta-1, 1.0.0, -1"
    })
    void comparesSemantically(String left, String right, int expectedSign) {
        assertThat(Integer.signum(Versions.compare(left, right))).isEqualTo(expectedSign);
    }

    @ParameterizedTest(name = "module line of {0} is {1}")
    @CsvSource({"3.10.0, 3", "2.24.0, 2", "12, 12", "4.13.1-jahia8-1, 4", "1.0.0-SNAPSHOT, 1"})
    void moduleLineIsTheMajor(String version, String line) {
        assertThat(Versions.line(version, false)).isEqualTo(line);
    }

    @ParameterizedTest(name = "core line of {0} is {1}")
    @CsvSource({"8.2.3.7, 8.2.3", "8.2.3.0, 8.2.3", "8.1.9.3, 8.1.9", "8.2, 8.2.0", "8, 8.0.0", "8.2.3.7-SNAPSHOT, 8.2.3"})
    void coreLineIsMajorMinorServicePack(String version, String line) {
        assertThat(Versions.line(version, true)).isEqualTo(line);
    }

    @Test
    void majorOfVersion() {
        assertThat(Versions.major("8.2.2.1")).isEqualTo(8);
        assertThat(Versions.major("10.0")).isEqualTo(10);
    }

    @Test
    void lineOfMalformedVersionIsRefused() {
        assertThatThrownBy(() -> Versions.line("abc", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Versions.major(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
