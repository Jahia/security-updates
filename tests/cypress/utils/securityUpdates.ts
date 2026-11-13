/**
 * Shared helpers of the security-updates specs: provisioning, GraphQL reads and writes, waiting for a check, the
 * banner request and accessibility checks.
 *
 * Every spec provisions its own known state in before() with settings-fixture-defaults.yml: the module reads only
 * the fixture store (https://storefixture, tests/storefixture) and "Check now" has no rate limit
 * (onDemandMinIntervalSeconds=0), so no spec waits a fixed time for it.
 */

export const ADMIN_PATH = '/jahia/administration/securityUpdates';
export const APP_PATH = '/jahia/dashboard';
export const FIXTURE_STORE = 'https://storefixture';
export const JAHIA_STORE = 'https://store.jahia.com';

export const BANNER = '#security-updates-banner';
export const BANNER_DISMISS = '[data-sel-role="security-updates-banner-dismiss"]';
export const BANNER_LINK = '[data-sel-role="security-updates-banner-link"]';
export const SESSION_DISMISS_KEY = 'security-updates-banner-dismissed';
/**
 * The localStorage key of root's dismissals: the SHA-256 of the user name where SubtleCrypto exists (a secure
 * context such as localhost), else its 32-bit FNV-1a (http://jahia:8080 in the test network is not a secure context).
 */
export const ROOT_LOCAL_DISMISS_KEYS = [
    `${SESSION_DISMISS_KEY}:4813494d137e1631bba301d5acab6e7bb7aa74ce1185d456565ef51d737677b2`,
    `${SESSION_DISMISS_KEY}:20fd0e45`
];

/** The dismissal keys of the fixture store, one per counted advisory (or owner report) of a component. */
export const KEY_CORE = 'jahia-core|JSA-2099-0001';
export const KEY_MODULE = 'security-updates|JSA-2099-0002';
export const KEY_OWNER = 'security-updates|GHSA-2c2c-3f3f-4g4g';

const GQL = {
    report: 'graphql/query/getSecurityUpdatesReport.graphql',
    banner: 'graphql/query/getSecurityUpdatesBanner.graphql',
    settings: 'graphql/query/getSecurityUpdatesSettings.graphql',
    refresh: 'graphql/mutation/refreshSecurityUpdates.graphql',
    update: 'graphql/mutation/updateSecurityUpdatesSettings.graphql',
    reset: 'graphql/mutation/resetSecurityUpdatesSettings.graphql'
};

export type Advisory = { id: string; title: string | null; url: string | null; cves: string[]; severity: string };
export type Finding = {
    component: string;
    groupId: string | null;
    installedVersion: string;
    recommendedVersion: string;
    compatibility: string;
    requiredJahiaVersion: string | null;
    severity: string;
    source: string;
    advisories: Advisory[];
};
export type StoreError = { storeUrl: string | null; kind: string; detail: string | null; message: string | null };
export type Report = {
    status: string;
    inProgress: boolean;
    stale: boolean;
    checkedAt: string | null;
    lastSuccess: string | null;
    nextCheckAt: string | null;
    jahiaVersion: string;
    errors: StoreError[];
    findings: Finding[];
};
export type Banner = {
    visible: boolean;
    count: number;
    minimumSeverity: string;
    components: string[];
    dismissalKeys: string[];
    backgroundColor: string;
    textColor: string;
    dismissPolicy: string;
    dismissHours: number;
    username: string;
};
export type Settings = {
    enabled: boolean;
    refreshIntervalHours: number;
    useForgeSettings: boolean;
    defaultStoreUrl: string;
    extraFeedUrls: string;
    bannerEnabled: boolean;
    bannerMinimumSeverity: string;
    bannerIncludeOwnerReports: boolean;
    bannerBackgroundColor: string;
    bannerTextColor: string;
    bannerDismissPolicy: string;
    bannerDismissHours: number;
};
export type StoredSettings = Settings & { contrastRatio: number; canEdit: boolean };
export type Suggestion = { field: string; value: string; ratio: string };
export type FieldError = { field: string | null; code: string; message: string; suggestions: Suggestion[] };
export type SettingsResult = { ok: boolean; errors: FieldError[]; settings: StoredSettings | null };
type GqlError = { message: string };
type GqlResult = { data?: Record<string, unknown> | null; errors?: GqlError[]; graphQLErrors?: GqlError[] };

export const SETTINGS_KEYS: Array<keyof Settings> = [
    'enabled',
    'refreshIntervalHours',
    'useForgeSettings',
    'defaultStoreUrl',
    'extraFeedUrls',
    'bannerEnabled',
    'bannerMinimumSeverity',
    'bannerIncludeOwnerReports',
    'bannerBackgroundColor',
    'bannerTextColor',
    'bannerDismissPolicy',
    'bannerDismissHours'
];

/** The settings of settings-fixture-defaults.yml. */
export const FIXTURE_SETTINGS: Settings = {
    enabled: true,
    refreshIntervalHours: 24,
    useForgeSettings: false,
    defaultStoreUrl: FIXTURE_STORE,
    extraFeedUrls: '',
    bannerEnabled: true,
    bannerMinimumSeverity: 'HIGH',
    bannerIncludeOwnerReports: true,
    bannerBackgroundColor: '#1B1B1B',
    bannerTextColor: '#FFFFFF',
    bannerDismissPolicy: 'SESSION',
    bannerDismissHours: 24
};

/** The module defaults (META-INF/configurations/org.jahia.community.securityupdates.cfg), set by resetSettings. */
export const DEFAULT_SETTINGS: Settings = {...FIXTURE_SETTINGS, useForgeSettings: true, defaultStoreUrl: JAHIA_STORE};

/** The twelve settings only (no contrastRatio, canEdit or __typename). */
export const settingsOf = (value: Partial<StoredSettings>): Settings => SETTINGS_KEYS.reduce(
    (settings, key) => ({...settings, [key]: value[key]}),
    {} as Settings
);

export const provision = (fileName: string): void => {
    cy.runProvisioningScript({script: {fileName: `security-updates/${fileName}`, type: 'application/yaml'}});
};

export const errorsOf = (result: unknown): GqlError[] => {
    const r = result as GqlResult;
    return r.graphQLErrors ?? r.errors ?? [];
};

export const errorText = (result: unknown): string => errorsOf(result).map(e => e.message).join(' ');

const namespaceOf = (result: unknown): Record<string, unknown> => {
    const data = (result as GqlResult).data as { securityUpdates?: Record<string, unknown> } | null | undefined;
    expect(errorsOf(result), 'no GraphQL error').to.deep.equal([]);
    expect(data?.securityUpdates, 'the securityUpdates namespace').to.be.an('object');
    return (data as { securityUpdates: Record<string, unknown> }).securityUpdates;
};

export const readReport = (): Cypress.Chainable<Report> =>
    cy.apollo({queryFile: GQL.report, fetchPolicy: 'no-cache'}).then(result => namespaceOf(result).report as Report);

export const readBanner = (): Cypress.Chainable<Banner> =>
    cy.apollo({queryFile: GQL.banner, fetchPolicy: 'no-cache'}).then(result => namespaceOf(result).banner as Banner);

export const readStoredSettings = (): Cypress.Chainable<StoredSettings> =>
    cy.apollo({queryFile: GQL.settings, fetchPolicy: 'no-cache'})
        .then(result => namespaceOf(result).settings as StoredSettings);

export const readSettings = (): Cypress.Chainable<Settings> => readStoredSettings().then(settingsOf);

export const refresh = (): Cypress.Chainable<Report> =>
    cy.apollo({mutationFile: GQL.refresh}).then(result => namespaceOf(result).refresh as Report);

/** The updateSettings mutation with the fixture settings, changed by the overrides. */
export const saveSettings = (overrides: Partial<Settings>): Cypress.Chainable<SettingsResult> =>
    cy.apollo({mutationFile: GQL.update, variables: {input: {...FIXTURE_SETTINGS, ...overrides}}})
        .then(result => namespaceOf(result).updateSettings as SettingsResult);

export const resetSettings = (): Cypress.Chainable<SettingsResult> =>
    cy.apollo({mutationFile: GQL.reset}).then(result => namespaceOf(result).resetSettings as SettingsResult);

/** The documents for calls expected to fail (errorPolicy all): the raw result, with its errors. */
export const raw = {
    report: (): Cypress.Chainable<unknown> => cy.apollo({queryFile: GQL.report, errorPolicy: 'all'}),
    banner: (): Cypress.Chainable<unknown> => cy.apollo({queryFile: GQL.banner, errorPolicy: 'all'}),
    settings: (): Cypress.Chainable<unknown> => cy.apollo({queryFile: GQL.settings, errorPolicy: 'all'}),
    refresh: (): Cypress.Chainable<unknown> => cy.apollo({mutationFile: GQL.refresh, errorPolicy: 'all'}),
    update: (overrides: Partial<Settings>): Cypress.Chainable<unknown> => cy.apollo({
        mutationFile: GQL.update,
        variables: {input: {...FIXTURE_SETTINGS, ...overrides}},
        errorPolicy: 'all'
    }),
    reset: (): Cypress.Chainable<unknown> => cy.apollo({mutationFile: GQL.reset, errorPolicy: 'all'})
};

const matches = <T>(value: T, expected: Partial<T>): boolean => Object.entries(expected)
    .every(([key, v]) => JSON.stringify(value[key as keyof T]) === JSON.stringify(v));

/**
 * Polls read() until the expected values are there: a configuration change reaches ConfigurationAdmin and the
 * service asynchronously. Fails with the description when they never are.
 */
export const until = <T>(read: () => Cypress.Chainable<T>, expected: Partial<T>, description: string): Cypress.Chainable<T> =>
    cy.waitUntil(() => read().then(value => (matches(value, expected) ? value : false)), {
        timeout: 60000,
        interval: 1000,
        errorMsg: `${description}: ${JSON.stringify(expected)} never reached`
    }) as Cypress.Chainable<T>;

const summary = (report: Report) => JSON.stringify({
    status: report.status,
    errors: report.errors.map(e => `${e.kind}/${e.detail}@${e.storeUrl}`),
    findings: report.findings.map(f => `${f.component}/${f.source}`)
});

/**
 * Runs one check to its end and yields its report: starts it with the refresh mutation (retried while the on-demand
 * limit of a configuration not applied yet refuses it; joining a check that already runs is fine), then polls the
 * report until inProgress is false and checkedAt has changed. No fixed wait.
 */
export const runCheck = (): Cypress.Chainable<Report> => readReport().then(before => {
    cy.waitUntil(() => refresh().then(started => started.inProgress), {
        timeout: 90000,
        interval: 1000,
        errorMsg: 'no check could be started (still rate limited, or checks turned off)'
    });
    return cy.waitUntil(() => readReport().then(report => (!report.inProgress && report.checkedAt !== before.checkedAt ? report : false)), {
        timeout: 180000,
        interval: 1000,
        errorMsg: 'the check did not end in 3 minutes'
    }) as Cypress.Chainable<Report>;
});

/**
 * Runs checks until the report is the expected one. A check started before the last configuration change (the
 * scheduled one, or one joined by runCheck) may still read the previous stores: the next one reads the new ones.
 */
export const checkUntil = (isExpected: (report: Report) => boolean, description: string, attempts = 4): Cypress.Chainable<Report> =>
    runCheck().then(report => {
        if (isExpected(report) || attempts <= 1) {
            expect(isExpected(report), `${description}; last report: ${summary(report)}`).to.equal(true);
            return cy.wrap(report);
        }

        return checkUntil(isExpected, description, attempts - 1);
    });

/** The report of the fixture store: read without any error, with its three findings. */
export const isFixtureReport = (report: Report): boolean => report.status === 'OK' &&
    report.errors.length === 0 &&
    report.findings.some(f => f.component === 'jahia-core' && f.source === 'ADVISORY') &&
    report.findings.some(f => f.component === 'security-updates' && f.source === 'ADVISORY') &&
    report.findings.some(f => f.source === 'OWNER_REPORTED');

/** Makes sure the current report is the fixture store's, running checks only when it is not. */
export const ensureFixtureReport = (): Cypress.Chainable<Report> => readReport().then(report => {
    if (isFixtureReport(report) && !report.inProgress) {
        return cy.wrap(report);
    }

    return checkUntil(isFixtureReport, 'the report of the fixture store');
});

/** Aliases the banner request of the Jahia UI (one GraphQL request, sent with fetch) as @bannerQuery. */
export const interceptBannerQuery = (): void => {
    cy.intercept('POST', '**/modules/graphql', req => {
        if (typeof req.body?.query === 'string' && req.body.query.includes('SecurityUpdatesBanner {')) {
            req.alias = 'bannerQuery';
        }
    });
};

type BannerAnswer = {
    data?: {
        jcr?: { nodeByPath?: { hasPermission?: boolean } | null } | null;
        securityUpdates?: { banner?: Banner | null } | null;
    } | null;
};

/** Waits for the next banner request and yields its answer (a positive signal before any absence is asserted). */
export const waitForBannerQuery = (): Cypress.Chainable<BannerAnswer> =>
    cy.wait('@bannerQuery', {timeout: 60000}).then(interception => {
        expect(interception.response?.statusCode, 'banner request answered').to.equal(200);
        return interception.response?.body as BannerAnswer;
    });

/** Opens a page of the Jahia UI and yields the answer to its banner request. */
export const openAppPage = (path = APP_PATH): Cypress.Chainable<BannerAnswer> => {
    interceptBannerQuery();
    cy.visit(path);
    return waitForBannerQuery();
};

/** Aliases the GraphQL operations of the admin page by operation name (@SecurityUpdatesRefresh, ...). */
export const interceptPageOperations = (): void => {
    cy.intercept('POST', '**/modules/graphql', req => {
        const operations = Array.isArray(req.body) ? req.body : [req.body];
        const name = operations.map(op => op?.operationName).find(n => typeof n === 'string' && n.startsWith('SecurityUpdates'));
        if (name) {
            req.alias = name;
        }
    });
};

/** WCAG 2.0/2.1/2.2 A and AA rules, and the AAA rules axe has (wcag2aaa: contrast 7:1, ...). */
export const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag2aaa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const describeViolations = (violations: Array<{ id: string; impact?: string | null; help: string; nodes: Array<{ target: unknown }> }>) =>
    violations.map(v => `${v.id} (${v.impact}): ${v.help} on ${v.nodes.slice(0, 5).map(n => JSON.stringify(n.target)).join(', ')}`).join('\n');

/**
 * Runs axe on the Security updates page only (from its heading to its settings), not on the rest of the Jahia
 * UI, and fails with the list of violations.
 */
export const checkPageAccessibility = (): void => {
    cy.injectAxe({axeCorePath: 'node_modules/axe-core/axe.min.js'});
    cy.contains('h1', 'Security updates').then($title => {
        cy.get('#security-updates-settings-title').then($settings => {
            let root: HTMLElement | null = $title[0].parentElement;
            while (root && !root.contains($settings[0])) {
                root = root.parentElement;
            }

            expect(root, 'element holding the whole page').to.not.equal(null);
            cy.checkA11y(root as HTMLElement, {runOnly: {type: 'tag', values: AXE_TAGS}}, violations => {
                throw new Error(`Accessibility violations:\n${describeViolations(violations)}`);
            });
        });
    });
};
