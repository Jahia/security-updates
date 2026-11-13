package org.jahia.community.securityupdates.feed;

import org.jahia.community.securityupdates.model.ModuleCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModuleCatalogParser")
class ModuleCatalogParserTest {

    @Test
    @DisplayName("reads the required Jahia version of every module version, prefix removed")
    void readsVersions() throws Exception {
        ModuleCatalog catalog = ModuleCatalogParser.parse(Fixtures.read("module-list.json"));

        assertThat(catalog.requiredJahiaVersion("graphql-dxm-provider", "org.jahia.modules", "3.10.0")).contains("8.2.0.0");
        assertThat(catalog.requiredJahiaVersion("graphql-dxm-provider", "org.jahia.modules", "2.24.0")).contains("8.1.0.0");
        assertThat(catalog.requiredJahiaVersion("graphql-dxm-provider", null, "4.0.0")).contains("8.2.3.0");
        assertThat(catalog.requiredJahiaVersion("graphql-dxm-provider", "org.other", "3.10.0")).isEmpty();
        assertThat(catalog.requiredJahiaVersion("graphql-dxm-provider", "org.jahia.modules", "9.9.9")).isEmpty();
        assertThat(catalog.requiredJahiaVersion("unknown", null, "1.0")).isEmpty();
    }

    @Test
    @DisplayName("skips incomplete modules and versions")
    void skipsIncompleteEntries() throws Exception {
        ModuleCatalog catalog = ModuleCatalogParser.parse(Fixtures.read("module-list.json"));

        assertThat(catalog.requiredJahiaVersion("broken-versions", "org.jahia.community", "1.0.0")).isEmpty();
        assertThat(catalog.requiredJahiaVersion("no-group", null, "1.0")).isEmpty();
    }

    @Test
    @DisplayName("a module without versions is skipped and counted; a module listed twice merges its versions")
    void moduleWithoutVersionsAndDuplicates() throws Exception {
        ModuleCatalog catalog = ModuleCatalogParser.parse("[{\"modules\":["
                + "{\"groupId\":\"g\",\"name\":\"none\"},"
                + "{\"groupId\":\"g\",\"name\":\"m\",\"versions\":[{\"version\":\"1.0\",\"requiredVersion\":\"version-8.0.0.0\"}]},"
                + "{\"groupId\":\"g\",\"name\":\"m\",\"versions\":[{\"version\":\"1.0\",\"requiredVersion\":\"version-8.2.0.0\"},"
                + "{\"version\":\"2.0\",\"requiredVersion\":\"8.2.1.0\"}]},"
                + "7]}]");

        assertThat(catalog.requiredJahiaVersion("m", "g", "1.0")).contains("8.0.0.0");
        assertThat(catalog.requiredJahiaVersion("m", "g", "2.0")).contains("8.2.1.0");
        assertThat(catalog.requiredJahiaVersion("none", "g", "1.0")).isEmpty();
        assertThat(catalog.getSkippedEntries()).isEqualTo(2);
        assertThat(catalog.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("reads at most MAX_ENTRIES modules and counts the others as skipped")
    void entriesAreCapped() throws Exception {
        StringBuilder json = new StringBuilder("[{\"modules\":[");
        int total = ModuleCatalogParser.MAX_ENTRIES + 3;
        for (int i = 0; i < total; i++) {
            json.append(i == 0 ? "" : ",").append("{\"groupId\":\"g\",\"name\":\"m").append(i)
                    .append("\",\"versions\":[{\"version\":\"1.0\",\"requiredVersion\":\"8.2.0.0\"}]}");
        }
        json.append("]}]");

        ModuleCatalog catalog = ModuleCatalogParser.parse(json.toString());

        assertThat(catalog.requiredJahiaVersion("m0", "g", "1.0")).contains("8.2.0.0");
        assertThat(catalog.requiredJahiaVersion("m" + (total - 1), "g", "1.0")).isEmpty();
        assertThat(catalog.getSkippedEntries()).isEqualTo(3);
        assertThat(catalog.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("an empty list is an empty catalogue")
    void emptyList() throws Exception {
        assertThat(ModuleCatalogParser.parse(Fixtures.read("module-list-empty.json")).isEmpty()).isTrue();
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @NullAndEmptySource
    @ValueSource(strings = {"<html/>", "[1]", "[{\"modules\": 3}]"})
    void invalidDocumentIsRefused(String body) {
        assertThatThrownBy(() -> ModuleCatalogParser.parse(body)).isInstanceOf(FeedFormatException.class);
    }

    @Test
    void objectInsteadOfArrayIsRefused() {
        assertThatThrownBy(() -> ModuleCatalogParser.parse(Fixtures.read("module-list-object.json")))
                .isInstanceOf(FeedFormatException.class);
    }

    @Test
    @DisplayName("merging keeps the first store's entry for a version known by two stores")
    void mergeKeepsFirst() throws Exception {
        ModuleCatalog first = ModuleCatalogParser.parse("[{\"modules\":[{\"groupId\":\"g\",\"name\":\"m\","
                + "\"versions\":[{\"version\":\"1.0\",\"requiredVersion\":\"version-8.0.0.0\"}]}]}]");
        ModuleCatalog second = ModuleCatalogParser.parse("[{\"modules\":[{\"groupId\":\"g\",\"name\":\"m\","
                + "\"versions\":[{\"version\":\"1.0\",\"requiredVersion\":\"version-8.2.0.0\"},"
                + "{\"version\":\"2.0\",\"requiredVersion\":\"version-8.2.0.0\"}]}]}]");

        ModuleCatalog merged = ModuleCatalog.merge(java.util.List.of(first, second));

        assertThat(merged.requiredJahiaVersion("m", "g", "1.0")).contains("8.0.0.0");
        assertThat(merged.requiredJahiaVersion("m", "g", "2.0")).contains("8.2.0.0");
    }
}
