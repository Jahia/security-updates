package org.jahia.community.securityupdates.graphql;

import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.securityupdates.model.DismissPolicy;
import org.jahia.community.securityupdates.model.Severity;
import org.jahia.community.securityupdates.service.SecurityUpdateService;
import org.jahia.community.securityupdates.service.SettingsKeys;
import org.jahia.community.securityupdates.service.SettingsStore;
import org.jahia.community.securityupdates.service.SettingsTestSupport;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Security updates settings GraphQL API")
class SecurityUpdatesSettingsGraphQLTest {

    private SecurityUpdateService service;
    private SettingsStore store;

    @BeforeEach
    void setUp() throws IOException {
        service = mock(SecurityUpdateService.class);
        store = mock(SettingsStore.class);
        when(service.getSettings()).thenReturn(SettingsTestSupport.defaults());
        when(store.load()).thenReturn(SettingsTestSupport.defaults().toValues());
        ServiceLocator.override(() -> service);
        ServiceLocator.overrideStore(() -> store);
        ServiceLocator.overrideUsername(() -> "jdoe");
        ServiceLocator.overrideCanEdit(() -> true);
    }

    @AfterEach
    void reset() {
        ServiceLocator.reset();
    }

    private static GqlSettingsInput input(String background, String text, int interval, String url) {
        return new GqlSettingsInput(true, interval, true, url, "", true, "HIGH", true, background, text, "HOURS", 12);
    }

    @Test
    @DisplayName("settings exposes every configuration key and the contrast ratio")
    void readSettings() {
        GqlSettings settings = new SecurityUpdatesQuery().getSettings();

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRefreshIntervalHours()).isEqualTo(24);
        assertThat(settings.isUseForgeSettings()).isTrue();
        assertThat(settings.getDefaultStoreUrl()).isEqualTo("https://store.jahia.com");
        assertThat(settings.getExtraFeedUrls()).isEmpty();
        assertThat(settings.isBannerEnabled()).isTrue();
        assertThat(settings.getBannerMinimumSeverity()).isEqualTo(Severity.HIGH);
        assertThat(settings.isBannerIncludeOwnerReports()).isTrue();
        assertThat(settings.getBannerBackgroundColor()).isEqualTo("#1B1B1B");
        assertThat(settings.getBannerTextColor()).isEqualTo("#FFFFFF");
        assertThat(settings.getBannerDismissPolicy()).isEqualTo(DismissPolicy.SESSION);
        assertThat(settings.getBannerDismissHours()).isEqualTo(24);
        assertThat(settings.getContrastRatio()).isBetween(17.2, 17.3);
        assertThat(settings.isCanEdit()).isTrue();
    }

    @Test
    @DisplayName("settings are the stored values, not the ones the service has not received yet")
    void settingsComeFromTheStore() throws IOException {
        Map<String, String> stored = new java.util.HashMap<>(SettingsTestSupport.defaults().toValues());
        stored.put("refreshIntervalHours", "6");
        when(store.load()).thenReturn(stored);

        assertThat(new SecurityUpdatesQuery().getSettings().getRefreshIntervalHours()).isEqualTo(6);
        verify(service, never()).getSettings();
    }

    @Test
    @DisplayName("canEdit is false for a user without the admin permission")
    void cannotEdit() {
        ServiceLocator.overrideCanEdit(() -> false);

        assertThat(new SecurityUpdatesQuery().getSettings().isCanEdit()).isFalse();
    }

    @Test
    @DisplayName("without the store, or when it cannot be read, settings is a GraphQL error without details")
    void settingsUnavailable() throws IOException {
        ServiceLocator.overrideStore(() -> null);
        assertThatThrownBy(() -> new SecurityUpdatesQuery().getSettings()).isInstanceOf(DataFetchingException.class);

        ServiceLocator.overrideStore(() -> store);
        when(store.load()).thenThrow(new IOException("/var/jahia/karaf/etc unreadable"));
        assertThatThrownBy(() -> new SecurityUpdatesQuery().getSettings()).isInstanceOf(DataFetchingException.class)
                .hasMessageNotContaining("/var/jahia");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("a valid update is written in full and returned normalised")
    void validUpdate() throws IOException {
        GqlSettingsResult result = new SecurityUpdatesMutation().updateSettings(input("#0b3d91", "#ffffff", 6, "https://store.jahia.com/"));

        assertThat(result.isOk()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSettings().getBannerBackgroundColor()).isEqualTo("#0B3D91");
        assertThat(result.getSettings().getRefreshIntervalHours()).isEqualTo(6);
        assertThat(result.getSettings().getDefaultStoreUrl()).isEqualTo("https://store.jahia.com");
        ArgumentCaptor<Map<String, String>> written = ArgumentCaptor.forClass(Map.class);
        verify(store).save(written.capture(), eq("jdoe"));
        assertThat(written.getValue()).hasSize(12).containsEntry("bannerDismissPolicy", "HOURS").containsEntry("bannerDismissHours", "12");
        assertThat(result.getSettings().isCanEdit()).isTrue();
    }

    @Test
    @DisplayName("an invalid update returns field errors and writes nothing")
    void invalidUpdate() throws IOException {
        GqlSettingsResult result = new SecurityUpdatesMutation().updateSettings(input("#888888", "#777777", 0, "not a url"));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getSettings()).isNull();
        assertThat(result.getErrors()).extracting(GqlFieldError::getField).containsExactlyInAnyOrder(
                "bannerBackgroundColor", "bannerTextColor", "refreshIntervalHours", "defaultStoreUrl");
        assertThat(result.getErrors()).allSatisfy(e -> {
            assertThat(e.getCode()).isNotBlank();
            assertThat(e.getMessage()).isNotBlank();
        });
        verify(store, never()).save(anyMap(), any());
    }

    @Test
    @DisplayName("a low contrast error carries the suggested colours")
    void lowContrastSuggestions() {
        GqlSettingsResult result = new SecurityUpdatesMutation().updateSettings(input("#1B1B1B", "#333333", 24, ""));

        GqlFieldError error = result.getErrors().get(0);
        assertThat(error.getCode()).isEqualTo("LOW_CONTRAST");
        assertThat(error.getSuggestions()).extracting(GqlSuggestion::getField, GqlSuggestion::getValue, GqlSuggestion::getRatio)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("bannerTextColor", "#A6A6A6", "7.07"),
                        org.assertj.core.groups.Tuple.tuple("bannerBackgroundColor", "#C1C1C1", "7.01"));
        assertThat(GqlSuggestion.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesSuggestedValue");
    }

    @Test
    @DisplayName("a write failure is reported without details")
    void writeFailure() throws IOException {
        doThrow(new IOException("/var/jahia/karaf/etc is read-only")).when(store).save(any(), any());

        GqlSettingsResult result = new SecurityUpdatesMutation().updateSettings(input("#1B1B1B", "#FFFFFF", 24, ""));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrors()).singleElement().satisfies(e -> {
            assertThat(e.getField()).isNull();
            assertThat(e.getCode()).isEqualTo("WRITE_FAILED");
            assertThat(e.getMessage()).doesNotContain("/var/jahia");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("reset writes the default values")
    void resetToDefaults() throws IOException {
        GqlSettingsResult result = new SecurityUpdatesMutation().resetSettings();

        assertThat(result.isOk()).isTrue();
        ArgumentCaptor<Map<String, String>> written = ArgumentCaptor.forClass(Map.class);
        verify(store).save(written.capture(), any());
        assertThat(written.getValue()).isEqualTo(SettingsTestSupport.defaults().toValues());
        assertThat(result.getSettings().getBannerDismissPolicy()).isEqualTo(DismissPolicy.SESSION);
    }

    @Test
    @DisplayName("without the store, nothing is written and the error says so")
    void storeMissing() {
        ServiceLocator.overrideStore(() -> null);

        GqlSettingsResult result = new SecurityUpdatesMutation().updateSettings(input("#1B1B1B", "#FFFFFF", 24, ""));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrors()).extracting(GqlFieldError::getCode).containsExactly("WRITE_FAILED");
    }

    @Test
    @DisplayName("reading the settings requires adminTemplates, changing them requires admin")
    void permissions() throws NoSuchMethodException {
        assertThat(SecurityUpdatesQuery.class.getMethod("getSettings").getAnnotation(GraphQLRequiresPermission.class).value())
                .isEqualTo("adminTemplates");
        for (Method method : new Method[]{SecurityUpdatesMutation.class.getMethod("updateSettings", GqlSettingsInput.class),
                SecurityUpdatesMutation.class.getMethod("resetSettings")}) {
            assertThat(method.getAnnotation(GraphQLRequiresPermission.class)).as(method.getName()).isNotNull();
            assertThat(method.getAnnotation(GraphQLRequiresPermission.class).value()).isEqualTo("admin");
        }
        assertThat(GqlSettingsInput.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesSettingsValues");
        assertThat(GqlSettings.class.getAnnotation(GraphQLName.class).value()).isEqualTo("SecurityUpdatesSettings");
    }

    @Test
    @DisplayName("the banner exposes the validated colours and dismiss policy")
    void bannerPresentation() {
        when(service.getBanner()).thenReturn(new org.jahia.community.securityupdates.model.BannerDecision(true, Severity.HIGH,
                java.util.List.of("jahia-core"), java.util.List.of("jahia-core|JSA-1"), "#0B3D91", "#FFFFFF",
                DismissPolicy.UNTIL_CHANGE, 24));

        GqlBanner banner = new SecurityUpdatesQuery().getBanner();

        assertThat(banner.getBackgroundColor()).isEqualTo("#0B3D91");
        assertThat(banner.getTextColor()).isEqualTo("#FFFFFF");
        assertThat(banner.getDismissPolicy()).isEqualTo(DismissPolicy.UNTIL_CHANGE);
        assertThat(banner.getDismissHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("the banner gives the current user name, for the per-user dismissal key")
    void bannerUsername() {
        when(service.getBanner()).thenReturn(org.jahia.community.securityupdates.model.BannerDecision.hidden());
        ServiceLocator.overrideUsername(() -> "jdoe");

        assertThat(new SecurityUpdatesQuery().getBanner().getUsername()).isEqualTo("jdoe");

        ServiceLocator.overrideUsername(() -> null);
        assertThat(new SecurityUpdatesQuery().getBanner().getUsername()).isEmpty();
    }

    @Test
    @DisplayName("the input type has one field per settings key, with the same names, and converts every one")
    void inputFieldNamesMatchTheSettingsKeys() {
        java.util.List<String> constructorNames = new java.util.ArrayList<>();
        for (java.lang.reflect.Parameter parameter : GqlSettingsInput.class.getConstructors()[0].getParameters()) {
            constructorNames.add(parameter.getAnnotation(GraphQLName.class).value());
        }
        java.util.List<String> getterNames = new java.util.ArrayList<>();
        for (Method method : GqlSettingsInput.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GraphQLName.class)) {
                getterNames.add(method.getAnnotation(GraphQLName.class).value());
            }
        }
        java.util.List<String> outputNames = new java.util.ArrayList<>();
        for (Method method : GqlSettings.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GraphQLName.class)) {
                outputNames.add(method.getAnnotation(GraphQLName.class).value());
            }
        }

        assertThat(constructorNames).containsExactlyInAnyOrderElementsOf(SettingsKeys.ALL);
        assertThat(getterNames).containsExactlyInAnyOrderElementsOf(SettingsKeys.ALL);
        assertThat(outputNames).containsAll(SettingsKeys.ALL).contains("canEdit", "contrastRatio");
        assertThat(input("#1B1B1B", "#FFFFFF", 24, "").toValues()).containsOnlyKeys(SettingsKeys.ALL);
        GqlSettingsInput full = input("#1B1B1B", "#FFFFFF", 24, "");
        assertThat(full.getEnabled()).isTrue();
        assertThat(full.getRefreshIntervalHours()).isEqualTo(24);
        assertThat(full.getUseForgeSettings()).isTrue();
        assertThat(full.getDefaultStoreUrl()).isEmpty();
        assertThat(full.getExtraFeedUrls()).isEmpty();
        assertThat(full.getBannerEnabled()).isTrue();
        assertThat(full.getBannerMinimumSeverity()).isEqualTo("HIGH");
        assertThat(full.getBannerIncludeOwnerReports()).isTrue();
        assertThat(full.getBannerBackgroundColor()).isEqualTo("#1B1B1B");
        assertThat(full.getBannerTextColor()).isEqualTo("#FFFFFF");
        assertThat(full.getBannerDismissPolicy()).isEqualTo("HOURS");
        assertThat(full.getBannerDismissHours()).isEqualTo(12);
    }

    @Test
    @DisplayName("a null input value is left out, so that the validator reports it MISSING")
    void nullValuesAreLeftOut() {
        GqlSettingsInput partial = new GqlSettingsInput(null, null, true, "", "", true, "HIGH", true, "#1B1B1B", "#FFFFFF",
                "SESSION", 24);

        assertThat(partial.toValues()).doesNotContainKeys("enabled", "refreshIntervalHours");
    }
}
