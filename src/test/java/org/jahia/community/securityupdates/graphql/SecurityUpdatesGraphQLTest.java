package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.securityupdates.model.AdvisoryRef;
import org.jahia.community.securityupdates.model.Compatibility;
import org.jahia.community.securityupdates.model.Finding;
import org.jahia.community.securityupdates.model.FindingSource;
import org.jahia.community.securityupdates.model.ReportStatus;
import org.jahia.community.securityupdates.model.SecurityReport;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.model.StoreError;
import org.jahia.community.securityupdates.model.StoreErrorKind;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Security updates GraphQL API")
class SecurityUpdatesGraphQLTest {

    private static final Instant CHECKED = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant SUCCESS = Instant.parse("2026-11-12T10:00:00Z");

    @AfterEach
    void resetLocator() {
        ServiceLocator.reset();
    }

    private static SecurityReport report() {
        AdvisoryRef safe = new AdvisoryRef("JSA-1", "Title", "https://academy.jahia.com/jsa-1", List.of("CVE-2026-1"), Severity.HIGH);
        AdvisoryRef unsafeLink = new AdvisoryRef("JSA-2", "Other", "http://example.org/jsa-2", List.of(), Severity.LOW);
        Finding finding = new Finding("graphql-dxm-provider", "org.jahia.modules", "3.4.0", "3.10.0",
                Compatibility.COMPATIBLE, "8.2.0.0", Severity.HIGH, FindingSource.ADVISORY, List.of(safe, unsafeLink));
        return new SecurityReport(ReportStatus.PARTIAL, CHECKED, SUCCESS, "8.2.2.1",
                List.of(new StoreError("https://store.jahia.com", StoreErrorKind.NOT_PUBLISHED, "404")), List.of(finding));
    }

    @Test
    @DisplayName("exposes the report of the service")
    void query() {
        SecurityUpdateService service = mock(SecurityUpdateService.class);
        when(service.getReport()).thenReturn(report());
        ServiceLocator.override(() -> service);

        GqlSecurityReport gql = new SecurityUpdatesQuery().getReport();

        assertThat(gql.getStatus()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(gql.getCheckedAt()).isEqualTo("2026-11-13T10:00:00Z");
        assertThat(gql.getLastSuccess()).isEqualTo("2026-11-12T10:00:00Z");
        assertThat(gql.getJahiaVersion()).isEqualTo("8.2.2.1");
        assertThat(gql.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getStoreUrl()).isEqualTo("https://store.jahia.com");
            assertThat(e.getKind()).isEqualTo(StoreErrorKind.NOT_PUBLISHED);
            assertThat(e.getMessage()).isEqualTo("404");
        });
        GqlFinding finding = gql.getFindings().get(0);
        assertThat(finding.getComponent()).isEqualTo("graphql-dxm-provider");
        assertThat(finding.getGroupId()).isEqualTo("org.jahia.modules");
        assertThat(finding.getInstalledVersion()).isEqualTo("3.4.0");
        assertThat(finding.getRecommendedVersion()).isEqualTo("3.10.0");
        assertThat(finding.getCompatibility()).isEqualTo(Compatibility.COMPATIBLE);
        assertThat(finding.getRequiredJahiaVersion()).isEqualTo("8.2.0.0");
        assertThat(finding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(finding.getSource()).isEqualTo(FindingSource.ADVISORY);
        assertThat(finding.getAdvisories()).extracting(GqlAdvisory::getId).containsExactly("JSA-1", "JSA-2");
        GqlAdvisory advisory = finding.getAdvisories().get(0);
        assertThat(advisory.getTitle()).isEqualTo("Title");
        assertThat(advisory.getCves()).containsExactly("CVE-2026-1");
        assertThat(advisory.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(advisory.getUrl()).isEqualTo("https://academy.jahia.com/jsa-1");
    }

    @Test
    @DisplayName("only https advisory links are exposed")
    void onlyHttpsLinks() {
        SecurityUpdateService service = mock(SecurityUpdateService.class);
        when(service.getReport()).thenReturn(report());
        ServiceLocator.override(() -> service);

        assertThat(new SecurityUpdatesQuery().getReport().getFindings().get(0).getAdvisories().get(1).getUrl()).isNull();
    }

    @Test
    @DisplayName("exposes the banner decision of the service")
    void banner() throws NoSuchMethodException {
        SecurityUpdateService service = mock(SecurityUpdateService.class);
        when(service.getBanner()).thenReturn(new org.jahia.community.securityupdates.model.BannerDecision(
                true, Severity.HIGH, List.of("jahia-core", "security-updates"),
                List.of("jahia-core|JSA-1", "security-updates|JSA-2", "security-updates|https://example.org/r"),
                "#1B1B1B", "#FFFFFF", org.jahia.community.securityupdates.model.DismissPolicy.SESSION, 24));
        ServiceLocator.override(() -> service);

        GqlBanner banner = new SecurityUpdatesQuery().getBanner();

        assertThat(banner.isVisible()).isTrue();
        assertThat(banner.getCount()).isEqualTo(2);
        assertThat(banner.getMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(banner.getComponents()).containsExactly("jahia-core", "security-updates");
        assertThat(banner.getDismissalKeys()).containsExactly("jahia-core|JSA-1", "security-updates|JSA-2",
                "security-updates|https://example.org/r");
        assertPermission(SecurityUpdatesQuery.class.getMethod("getBanner"));
        assertThat(GqlBanner.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesBanner");
    }

    @Test
    @DisplayName("without the service, no banner")
    void bannerWithoutService() {
        ServiceLocator.override(() -> null);

        GqlBanner banner = new SecurityUpdatesQuery().getBanner();

        assertThat(banner.isVisible()).isFalse();
        assertThat(banner.getCount()).isZero();
    }

    @Test
    @DisplayName("the refresh mutation runs an on-demand refresh")
    void mutation() {
        SecurityUpdateService service = mock(SecurityUpdateService.class);
        when(service.refresh()).thenReturn(report());
        ServiceLocator.override(() -> service);

        GqlSecurityReport gql = new SecurityUpdatesMutation().refresh();

        verify(service).refresh();
        assertThat(gql.getFindings()).hasSize(1);
    }

    @Test
    @DisplayName("without the service, the report says the check is unavailable")
    void serviceMissing() {
        ServiceLocator.override(() -> null);

        GqlSecurityReport query = new SecurityUpdatesQuery().getReport();
        GqlSecurityReport mutation = new SecurityUpdatesMutation().refresh();

        assertThat(query.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
        assertThat(query.getCheckedAt()).isNull();
        assertThat(query.getErrors()).isNotEmpty();
        assertThat(mutation.getStatus()).isEqualTo(ReportStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("one namespace field on Query and one on Mutation, both requiring adminTemplates")
    void namespaceContainers() throws NoSuchMethodException {
        assertThat(SecurityUpdatesQueryExtension.class.getAnnotation(GraphQLTypeExtension.class).value())
                .isEqualTo(DXGraphQLProvider.Query.class);
        assertThat(SecurityUpdatesMutationExtension.class.getAnnotation(GraphQLTypeExtension.class).value())
                .isEqualTo(DXGraphQLProvider.Mutation.class);
        assertSingleRootField(SecurityUpdatesQueryExtension.class);
        assertSingleRootField(SecurityUpdatesMutationExtension.class);

        assertThat(SecurityUpdatesQueryExtension.securityUpdates()).isInstanceOf(SecurityUpdatesQuery.class);
        assertThat(SecurityUpdatesMutationExtension.securityUpdates()).isInstanceOf(SecurityUpdatesMutation.class);
        assertThat(SecurityUpdatesQuery.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesQuery");
        assertThat(SecurityUpdatesMutation.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesMutation");
        assertPermission(SecurityUpdatesQuery.class.getMethod("getReport"));
        assertPermission(SecurityUpdatesMutation.class.getMethod("refresh"));
    }

    private static void assertSingleRootField(Class<?> extension) throws NoSuchMethodException {
        List<Method> fields = Arrays.stream(extension.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GraphQLField.class)).collect(java.util.stream.Collectors.toList());
        assertThat(fields).singleElement().satisfies(m -> {
            assertThat(Modifier.isStatic(m.getModifiers())).isTrue();
            assertThat(m.getAnnotation(GraphQLName.class).value()).isEqualTo("securityUpdates");
        });
        assertPermission(extension.getMethod("securityUpdates"));
    }

    private static void assertPermission(Method method) {
        assertThat(method.getAnnotation(GraphQLRequiresPermission.class)).isNotNull();
        assertThat(method.getAnnotation(GraphQLRequiresPermission.class).value()).isEqualTo("adminTemplates");
    }
}
