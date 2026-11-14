import type {Interception} from 'cypress/types/net-stubbing';

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

/**
 * The hashes under which the banner stores those keys (never the keys themselves): SHA-256 in hexadecimal of the
 * UTF-8 key where SubtleCrypto exists, else its 32-bit FNV-1a, as for the user name. Precomputed: the specs run at
 * http://jahia:8080, which is not a secure context, so they have no SubtleCrypto either.
 */
const STORED_KEY_HASHES: Record<string, { sha256: string; fnv1a: string }> = {
    [KEY_CORE]: {sha256: 'feddab4d011d7e07eb60cec5253fdc803fb2383a1991f5fed32704fa177d35a1', fnv1a: '5d7fe35d'},
    [KEY_MODULE]: {sha256: '9c778a9cf1deb093774a8528db6c17235ea74d145fa46e9ccfe0230ccab63e5e', fnv1a: '1cffbca6'},
    [KEY_OWNER]: {sha256: '9b1b9d8169a1cbfe409586d1bc25f2d197cb258854bcc375993358def4acc6a6', fnv1a: '41e38b68'}
};

/**
 * Asserts that a stored list of dismissed keys is the list of the hashes of the keys, in the same order, all made
 * with the same hash function, and that no key is stored in clear.
 */
export const expectStoredDismissalKeys = (stored: unknown, keys: string[], label: string): void => {
    keys.forEach(key => expect(STORED_KEY_HASHES, `${label}: precomputed hashes of ${key}`).to.have.property(key));
    const hashed = [
        JSON.stringify(keys.map(key => STORED_KEY_HASHES[key].sha256)),
        JSON.stringify(keys.map(key => STORED_KEY_HASHES[key].fnv1a))
    ];
    expect(hashed, `${label}: the hashes of ${keys.join(', ')}`).to.include(JSON.stringify(stored));
    keys.forEach(key => expect(JSON.stringify(stored), `${label}: ${key} not stored in clear`).to.not.contain(key));
};

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

type GraphqlOperation = { operationName?: string; query?: string };

/**
 * The answer to one GraphQL operation of an intercepted request. Jahia's Apollo client batches operations: the
 * request body and the response body are then arrays, in the same order; otherwise both are single objects.
 */
export const answerOf = <T = Record<string, unknown>>(interception: Interception, isOperation: (operation: GraphqlOperation) => boolean, label: string): T => {
    expect(interception.response?.statusCode, `${label} answered`).to.equal(200);
    const requestBody = interception.request.body as GraphqlOperation | GraphqlOperation[];
    const responseBody = interception.response?.body as T | T[];
    const operations = Array.isArray(requestBody) ? requestBody : [requestBody];
    const answers = Array.isArray(responseBody) ? responseBody : [responseBody];
    const index = operations.findIndex(operation => Boolean(operation) && isOperation(operation));
    expect(index, `${label} in the request`).to.be.greaterThan(-1);
    expect(answers[index], `answer to ${label}`).to.be.an('object');
    return answers[index];
};

/** The operation of an intercepted request named operationName (the Apollo documents of the admin page). */
export const named = (operationName: string) => (operation: GraphqlOperation): boolean => operation.operationName === operationName;

const isBannerQuery = (operation: GraphqlOperation): boolean => typeof operation.query === 'string' && operation.query.includes('SecurityUpdatesBanner {');

/*
 * One alias per navigation. cy.wait('@alias') yields the oldest request of that alias not waited for yet, and the
 * banner sends more requests than the one awaited (visibilitychange, the security-updates:changed event): with one
 * alias for the whole test, a wait after a reload could yield an answer given before it. Each interceptBannerQuery
 * call therefore gets its own alias, and only its handler names requests once it is the active one (older handlers
 * of the same test stay registered but name nothing).
 */
let bannerGeneration = 0;
let activeBannerGeneration = 0;
let pendingBannerAlias: string | null = null;

/**
 * Aliases the banner requests of the next navigation (one GraphQL request, sent with fetch). Call it right before
 * each cy.visit or cy.reload whose banner request waitForBannerQuery awaits.
 */
export const interceptBannerQuery = (): void => {
    bannerGeneration += 1;
    const generation = bannerGeneration;
    const alias = `bannerQuery${generation}`;
    pendingBannerAlias = alias;
    cy.then(() => {
        activeBannerGeneration = generation;
    });
    cy.intercept('POST', '**/modules/graphql', req => {
        const operations: GraphqlOperation[] = Array.isArray(req.body) ? req.body : [req.body];
        if (generation === activeBannerGeneration && operations.some(operation => Boolean(operation) && isBannerQuery(operation))) {
            req.alias = alias;
        }
    });
};

type BannerAnswer = {
    data?: {
        jcr?: { nodeByPath?: { hasPermission?: boolean } | null } | null;
        securityUpdates?: { banner?: Banner | null } | null;
    } | null;
};

/**
 * Waits for the first banner request of the navigation prepared by the last interceptBannerQuery call and yields its
 * answer (a positive signal before any absence is asserted). Each call needs its own interceptBannerQuery call.
 */
export const waitForBannerQuery = (): Cypress.Chainable<BannerAnswer> => {
    const alias = pendingBannerAlias;
    pendingBannerAlias = null;
    if (!alias) {
        throw new Error('waitForBannerQuery: call interceptBannerQuery() right before the visit or reload it waits for');
    }

    return cy.wait(`@${alias}`, {timeout: 60000}).then(interception => answerOf<BannerAnswer>(interception, isBannerQuery, 'banner request'));
};

/** Opens a page of the Jahia UI and yields the answer to its banner request. */
export const openAppPage = (path = APP_PATH): Cypress.Chainable<BannerAnswer> => {
    interceptBannerQuery();
    cy.visit(path);
    return waitForBannerQuery();
};

/** Reloads the current page of the Jahia UI and yields the answer to its own banner request. */
export const reloadAppPage = (): Cypress.Chainable<BannerAnswer> => {
    interceptBannerQuery();
    cy.reload();
    return waitForBannerQuery();
};

/**
 * The test hook of the banner script on <html>: "pending" while a banner request runs, "ready" once the banner is
 * decided (shown, hidden by the settings or a dismissal, not allowed, Security updates page open, or an error).
 */
export const BANNER_DECISION_ATTRIBUTE = 'data-security-updates-banner';

/**
 * Waits until the banner script of the current page has decided. Call it after the navigation it is about (after
 * waitForBannerQuery, or once the Security updates page is shown) and before any assertion that the banner is
 * absent: the banner is rendered asynchronously after its answer (response body, hash of the user name), so an
 * absence assertion made before the decision could pass on a banner that is still to come.
 */
export const waitForBannerDecision = (): void => {
    cy.get('html', {timeout: 30000, log: false}).should('have.attr', BANNER_DECISION_ATTRIBUTE, 'ready');
};

/** Aliases the GraphQL operations of the admin page by operation name (@SecurityUpdatesRefresh, ...). */
export const interceptPageOperations = (): void => {
    cy.intercept('POST', '**/modules/graphql', req => {
        const operations: GraphqlOperation[] = Array.isArray(req.body) ? req.body : [req.body];
        const name = operations.map(op => op?.operationName).find(n => typeof n === 'string' && n.startsWith('SecurityUpdates'));
        if (name) {
            req.alias = name;
        }
    });
};

/** WCAG 2.0/2.1/2.2 A and AA rules, and the AAA rules axe has (wcag2aaa: contrast 7:1, ...). */
export const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag2aaa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/** The banner preview of the settings form (a decorative sample, aria-hidden, showing the edited colours). */
export const BANNER_PREVIEW = '[data-testid="security-updates-banner-preview"]';

const describeViolations = (violations: Array<{ id: string; impact?: string | null; help: string; nodes: Array<{ target: unknown }> }>) =>
    violations.map(v => `${v.id} (${v.impact}): ${v.help} on ${v.nodes.slice(0, 5).map(n => JSON.stringify(n.target)).join(', ')}`).join('\n');

/**
 * Runs axe on the Security updates page only (from its heading to its settings), not on the rest of the Jahia
 * UI, and fails with the list of violations. `exclude` leaves selectors out of this run (see its callers for why).
 */
export const checkPageAccessibility = (exclude: string[] = []): void => {
    cy.injectAxe({axeCorePath: 'node_modules/axe-core/axe.min.js'});
    cy.contains('h1', 'Security updates').then($title => {
        cy.get('#security-updates-settings-title').then($settings => {
            let root: HTMLElement | null = $title[0].parentElement;
            while (root && !root.contains($settings[0])) {
                root = root.parentElement;
            }

            expect(root, 'element holding the whole page').to.not.equal(null);
            exclude.forEach(selector => cy.get(selector).should('exist'));
            const context = exclude.length > 0 ? {include: [root as HTMLElement], exclude} : root as HTMLElement;
            cy.checkA11y(context, {runOnly: {type: 'tag', values: AXE_TAGS}}, violations => {
                throw new Error(`Accessibility violations:\n${describeViolations(violations)}`);
            });
        });
    });
};
