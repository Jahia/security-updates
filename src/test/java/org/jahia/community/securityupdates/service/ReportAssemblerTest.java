package org.jahia.community.securityupdates.service;

import org.jahia.community.securityupdates.feed.SecurityFeedParser;
import org.jahia.community.securityupdates.feed.StoreFetchResult;
import org.jahia.community.securityupdates.feed.StoreTarget;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityFeed;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorDetail;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jahia.community.securityupdates.service.ServiceFixture.BOTH_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.DEFAULT_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.EMPTY_FEED;
import static org.jahia.community.securityupdates.service.ServiceFixture.FEED_WITH_FINDING;
import static org.jahia.community.securityupdates.service.ServiceFixture.JAHIA;
import static org.jahia.community.securityupdates.service.ServiceFixture.PRIVATE_STORE;
import static org.jahia.community.securityupdates.service.ServiceFixture.SECOND_FEED;

/**
 * Pins the status of a report: {@code OK} only when every store gave a fresh feed with no error other than
 * {@code CATALOGUE_UNAVAILABLE} and every component was compared; {@code PARTIAL} when some feed is usable;
 * {@code UNAVAILABLE} when none is.
 */
@DisplayName("ReportAssembler status")
class ReportAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private ServiceFixture fixture;
    private ReportAssembler assembler;
    private ReportAssembler.Sources sources;
    private final Map<String, StoreFetchResult> answers = new HashMap<>();

    @BeforeEach
    void setUp() {
        fixture = new ServiceFixture();
        assembler = new ReportAssembler(finding -> { }, fixture.clock);
        sources = new ReportAssembler.Sources(fixture.fetcher, fixture.forges, fixture.installed, JAHIA);
    }

    private SecurityReport assemble(Settings settings) {
        return assembler.assemble(settings, sources, NOW, SecurityReport.notChecked(JAHIA), () -> false);
    }

    private SecurityReport assembleWithAnswers(Settings settings) {
        Function<StoreTarget, StoreFetchResult> fetch = target -> answers.get(target.getUrl());
        return assembler.assemble(settings, sources, fetch, NOW, SecurityReport.notChecked(JAHIA), () -> false);
    }

    private static SecurityFeed feed(String json) {
        try {
            return SecurityFeedParser.parse(json);
        } catch (org.jahia.community.securityupdates.feed.FeedFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest(name = "any feed {0}, complete {1} -> {2}")
    @CsvSource({"false, false, UNAVAILABLE", "false, true, UNAVAILABLE", "true, false, PARTIAL", "true, true, OK"})
    void statusTruthTable(boolean anyFeed, boolean complete, ReportStatus expected) {
        assertThat(ReportAssembler.status(anyFeed, complete)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a fresh feed with the module list answering 500 is OK, with CATALOGUE_UNAVAILABLE and compatibility UNKNOWN")
    void catalogueUnavailableKeepsOk() {
        fixture.storeServes(DEFAULT_STORE, FEED_WITH_FINDING);
        fixture.fetcher.answer(DEFAULT_STORE + org.jahia.community.securityupdates.feed.StoreClient.MODULE_LIST_PATH,
                new org.jahia.community.securityupdates.feed.HttpResult(500, "down"));

        SecurityReport report = assemble(Settings.defaults());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
        assertThat(report.getErrors()).extracting(StoreError::getKind).containsExactly(StoreErrorKind.CATALOGUE_UNAVAILABLE);
        assertThat(report.getFindings()).singleElement()
                .satisfies(f -> assertThat(f.getCompatibility()).isEqualTo(Compatibility.UNKNOWN));
        assertThat(report.getLastSuccess()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "a fresh feed with a {0} error is never OK")
    @EnumSource(value = StoreErrorKind.class, mode = EnumSource.Mode.EXCLUDE, names = "CATALOGUE_UNAVAILABLE")
    void everyOtherErrorKindIsNeverOk(StoreErrorKind kind) {
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(FEED_WITH_FINDING), null,
                List.of(new StoreError(DEFAULT_STORE, kind, "problem"))));

        SecurityReport report = assembleWithAnswers(Settings.defaults());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.getLastSuccess()).isNull();
        assertThat(report.getFindings()).hasSize(1);
    }

    @ParameterizedTest(name = "no feed and a {0} error is UNAVAILABLE")
    @EnumSource(StoreErrorKind.class)
    void noFeedIsUnavailable(StoreErrorKind kind) {
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, null, null,
                List.of(new StoreError(DEFAULT_STORE, kind, "problem"))));

        assertThat(assembleWithAnswers(Settings.defaults()).getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("two stores, B fails after a good check and A is fresh: PARTIAL, with B's kept data and a marked error")
    void failingStoreKeepsItsData() {
        fixture.forges.stores = List.of(StoreTarget.store(DEFAULT_STORE, null, null), StoreTarget.store(PRIVATE_STORE, null, null));
        Settings settings = Settings.defaults();
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(EMPTY_FEED), null, Collections.emptyList()));
        answers.put(PRIVATE_STORE, new StoreFetchResult(PRIVATE_STORE, feed(SECOND_FEED), null, Collections.emptyList()));
        SecurityReport first = assembleWithAnswers(settings);
        assertThat(first.getStatus()).isEqualTo(ReportStatus.OK);
        assertThat(first.getFindings()).extracting(Finding::getComponent).containsExactly("jcontent");

        answers.put(PRIVATE_STORE, new StoreFetchResult(PRIVATE_STORE, null, null,
                List.of(new StoreError(PRIVATE_STORE, StoreErrorKind.UNAVAILABLE, "HTTP 503"))));
        SecurityReport second = assembleWithAnswers(settings);

        assertThat(second.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(second.getFindings()).extracting(Finding::getComponent).containsExactly("jcontent");
        assertThat(second.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getStoreUrl()).isEqualTo(PRIVATE_STORE);
            assertThat(e.getMessage()).isEqualTo("HTTP 503" + ReportAssembler.KEPT_DATA_SUFFIX);
        });
    }

    @Test
    @DisplayName("two stores both fresh, one without findings, is OK with the findings of both")
    void twoFreshStores() {
        fixture.forges.stores = List.of(StoreTarget.store(DEFAULT_STORE, null, null), StoreTarget.store(PRIVATE_STORE, null, null));
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(BOTH_FEED), null, Collections.emptyList()));
        answers.put(PRIVATE_STORE, new StoreFetchResult(PRIVATE_STORE, feed(EMPTY_FEED), null, Collections.emptyList()));

        SecurityReport report = assembleWithAnswers(Settings.defaults());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.OK);
        assertThat(report.getFindings()).hasSize(2);
    }

    private static final String THIRD_STORE = "https://third.example.org";

    private void threeStores() {
        fixture.forges.stores = List.of(StoreTarget.store(DEFAULT_STORE, null, null), StoreTarget.store(PRIVATE_STORE, null, null),
                StoreTarget.store(THIRD_STORE, null, null));
    }

    @ParameterizedTest(name = "a {0} thrown while reading one store fails that store only")
    @ValueSource(strings = {"runtime", "stack", "linkage"})
    void oneStoreFailingUnexpectedlyKeepsTheOthers(String failure) {
        threeStores();
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(FEED_WITH_FINDING), null, Collections.emptyList()));
        answers.put(THIRD_STORE, new StoreFetchResult(THIRD_STORE, feed(SECOND_FEED), null, Collections.emptyList()));
        Function<StoreTarget, StoreFetchResult> fetch = target -> {
            if (PRIVATE_STORE.equals(target.getUrl())) {
                throwUnexpected(failure);
            }
            return answers.get(target.getUrl());
        };

        SecurityReport report = assembler.assemble(Settings.defaults(), sources, fetch, NOW, SecurityReport.notChecked(JAHIA), () -> false);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactlyInAnyOrder("graphql-dxm-provider", "jcontent");
        assertThat(report.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getStoreUrl()).isEqualTo(PRIVATE_STORE);
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.CHECK_FAILED);
            assertThat(e.getMessage()).isEqualTo(ReportAssembler.STORE_CHECK_FAILED);
        });
    }

    private static void throwUnexpected(String failure) {
        if ("runtime".equals(failure)) {
            throw new IllegalStateException("bug");
        }
        if ("stack".equals(failure)) {
            throw new StackOverflowError();
        }
        throw new NoClassDefFoundError("x");
    }

    @Test
    @DisplayName("an OutOfMemoryError is not swallowed")
    void outOfMemoryIsNotCaught() {
        Function<StoreTarget, StoreFetchResult> fetch = target -> {
            throw new OutOfMemoryError("test");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assembler.assemble(Settings.defaults(), sources, fetch, NOW,
                SecurityReport.notChecked(JAHIA), () -> false)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("stores not reached before the check deadline are not read: TIMEOUT error, kept data shown, PARTIAL")
    void checkDeadline() {
        threeStores();
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(EMPTY_FEED), null, Collections.emptyList()));
        answers.put(PRIVATE_STORE, new StoreFetchResult(PRIVATE_STORE, feed(SECOND_FEED), null, Collections.emptyList()));
        answers.put(THIRD_STORE, new StoreFetchResult(THIRD_STORE, feed(EMPTY_FEED), null, Collections.emptyList()));
        assertThat(assembleWithAnswers(Settings.defaults()).getStatus()).isEqualTo(ReportStatus.OK);
        List<String> read = new java.util.ArrayList<>();
        Function<StoreTarget, StoreFetchResult> slowFirst = target -> {
            read.add(target.getUrl());
            // the first store takes the whole budget
            fixture.clock.advance(ReportAssembler.CHECK_DEADLINE);
            return answers.get(target.getUrl());
        };

        SecurityReport report = assembler.assemble(Settings.defaults(), sources, slowFirst, NOW, SecurityReport.notChecked(JAHIA),
                () -> false);

        assertThat(read).containsExactly(DEFAULT_STORE);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        // the private store's last data is still used
        assertThat(report.getFindings()).extracting(Finding::getComponent).containsExactly("jcontent");
        assertThat(report.getErrors()).extracting(StoreError::getStoreUrl).containsExactly(PRIVATE_STORE, THIRD_STORE);
        assertThat(report.getErrors()).allSatisfy(e -> {
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.UNAVAILABLE);
            assertThat(e.getDetail()).isEqualTo(StoreErrorDetail.TIMEOUT);
            assertThat(e.getMessage()).isEqualTo(ReportAssembler.OUT_OF_TIME + ReportAssembler.KEPT_DATA_SUFFIX);
        });
    }

    @Test
    @DisplayName("a store started just before the deadline is read")
    void justBeforeTheDeadline() {
        answers.put(DEFAULT_STORE, new StoreFetchResult(DEFAULT_STORE, feed(EMPTY_FEED), null, Collections.emptyList()));
        fixture.clock.advance(ReportAssembler.CHECK_DEADLINE.minus(Duration.ofSeconds(1)));

        assertThat(assembleWithAnswers(Settings.defaults()).getStatus()).isEqualTo(ReportStatus.OK);
    }
}
