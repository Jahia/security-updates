import {
    ADMIN_PATH,
    FIXTURE_SETTINGS,
    FIXTURE_STORE,
    Report,
    StoreError,
    checkUntil,
    isFixtureReport,
    provision,
    readSettings,
    saveSettings,
    until
} from '../utils/securityUpdates';

/**
 * Stores that fail, and an advisory that does not concern the installed version. Each store is a path of the fixture
 * store (tests/storefixture/nginx.conf, feeds in tests/storefixture/stores/), set as the default store:
 *  - not-published: the feed answers 404 (NOT_PUBLISHED, as store.jahia.com until it publishes its feed);
 *  - schema-v2: a feed of schemaVersion 2 (NOT_UNDERSTOOD / SCHEMA_UNSUPPORTED);
 *  - flaky: the fixture store, or HTTP 500 while the switch of the fixture store (port 8081) says "fail";
 *  - introduced-version: an advisory of security-updates introduced in 50.0.0, and its twin without introducedVersion.
 * GraphQL first, then the page.
 */
describe('Security updates: store errors', () => {
    const NOT_PUBLISHED_STORE = `${FIXTURE_STORE}/not-published`;
    const SCHEMA_V2_STORE = `${FIXTURE_STORE}/schema-v2`;
    const FLAKY_STORE = `${FIXTURE_STORE}/flaky`;
    const INTRODUCED_VERSION_STORE = `${FIXTURE_STORE}/introduced-version`;
    /** TEST-ONLY switch of the flaky store, reachable from the Cypress container only (tests/storefixture/switches.js). */
    const FLAKY_SWITCH = 'http://storefixture:8081/switch/flaky';
    const ERRORS_TITLE = '#security-updates-errors-title';
    const FINDING_ROWS = '[role="region"][aria-labelledby="security-updates-findings-title"] tbody tr';

    const switchFlaky = (state: 'ok' | 'fail') => {
        cy.request({method: 'POST', url: `${FLAKY_SWITCH}?state=${state}`}).its('body').should('equal', `${state}\n`);
    };

    /** Makes url the only store, through the updateSettings mutation, and waits until the service has it. */
    const useStore = (url: string) => {
        saveSettings({defaultStoreUrl: url}).then(result => {
            expect(result.errors, `${url} accepted`).to.deep.equal([]);
            expect(result.ok).to.equal(true);
        });
        until(readSettings, {defaultStoreUrl: url}, 'the store URL');
    };

    const errorsOfStore = (report: Report, url: string): StoreError[] => report.errors.filter(e => e.storeUrl === url);
    const causes = (errors: StoreError[]): string[] => errors.map(e => `${e.kind}/${e.detail}`);
    const advisoryIds = (report: Report): string[] => report.findings.flatMap(f => f.advisories.map(a => a.id));
    const findingKeys = (report: Report): string[] => report.findings.map(f => `${f.component}/${f.source}`).sort();

    const openPage = () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        cy.contains('h1', 'Security updates').should('be.visible');
    };

    before(() => {
        cy.apolloClient();
        switchFlaky('ok');
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
    });

    beforeEach(() => {
        cy.apolloClient();
    });

    after(() => {
        cy.apolloClient();
        switchFlaky('ok');
        provision('restore-defaults.yml');
    });

    it('a store whose feed answers 404 does not publish one yet: UNAVAILABLE, no finding, "Not available yet"', () => {
        useStore(NOT_PUBLISHED_STORE);
        checkUntil(report => report.status === 'UNAVAILABLE' && errorsOfStore(report, NOT_PUBLISHED_STORE).length > 0,
            'the report of a store that publishes no feed').then(report => {
            expect(report.errors.map(e => `${e.kind}/${e.detail}@${e.storeUrl}`), 'one problem, and only that one')
                .to.deep.equal([`NOT_PUBLISHED/null@${NOT_PUBLISHED_STORE}`]);
            expect(report.findings).to.deep.equal([]);
        });

        openPage();
        cy.contains('Not available yet').should('be.visible');
        // A store that has simply not published its feed yet is listed as neutral store details, not as a problem.
        cy.get('#security-updates-store-details-title').should('be.visible').and('have.text', 'Store details');
        cy.get(ERRORS_TITLE).should('not.exist');
        cy.get(FINDING_ROWS).should('not.exist');
    });

    it('a feed of an unknown schemaVersion is not understood: never OK, and nothing is read from it', () => {
        useStore(SCHEMA_V2_STORE);
        checkUntil(report => errorsOfStore(report, SCHEMA_V2_STORE).some(e => e.kind === 'NOT_UNDERSTOOD'),
            'the report of a feed of schemaVersion 2').then(report => {
            expect(causes(errorsOfStore(report, SCHEMA_V2_STORE))).to.deep.equal(['NOT_UNDERSTOOD/SCHEMA_UNSUPPORTED']);
            expect(report.status, 'no usable feed').to.equal('UNAVAILABLE');
            expect(report.findings, 'its advisory JSA-2099-0201 is not used').to.deep.equal([]);
        });

        openPage();
        // Positive signal first: the problems of the check are listed; this is not a store that has no feed yet.
        cy.get(ERRORS_TITLE).should('be.visible');
        cy.contains('Not available yet').should('not.exist');
    });

    it('a store that worked and then fails: PARTIAL, with the findings of its last good check', () => {
        switchFlaky('fail');
        useStore(FLAKY_STORE);
        // Positive signal first: the checks read the flaky store now (failing, with nothing to keep yet).
        checkUntil(report => report.status === 'UNAVAILABLE' &&
            causes(errorsOfStore(report, FLAKY_STORE)).includes('UNAVAILABLE/HTTP_STATUS'), 'the flaky store, failing before any good check');

        switchFlaky('ok');
        checkUntil(isFixtureReport, 'the flaky store, working').then(good => {
            switchFlaky('fail');
            checkUntil(report => report.status === 'PARTIAL', 'the flaky store, failing after a good check').then(report => {
                expect(causes(errorsOfStore(report, FLAKY_STORE)), 'its feed failed').to.include('UNAVAILABLE/HTTP_STATUS');
                expect(findingKeys(report), 'the findings of the last good check are kept').to.deep.equal(findingKeys(good));
                expect(Date.parse(report.lastSuccess as string), 'last success: a good check')
                    .to.be.at.least(Date.parse(good.checkedAt as string))
                    .and.below(Date.parse(report.checkedAt as string));
            });
        });

        openPage();
        cy.contains('Partly checked').should('be.visible');
        cy.get(FINDING_ROWS).should('have.length', 3);
        cy.get(ERRORS_TITLE).should('be.visible');
    });

    it('an advisory introduced after the installed version gives no finding; its twin without introducedVersion does', () => {
        useStore(INTRODUCED_VERSION_STORE);
        checkUntil(report => advisoryIds(report).includes('JSA-2099-0102'), 'the report of the introduced-version store').then(report => {
            expect(report.status).to.equal('OK');
            expect(report.errors).to.deep.equal([]);
            expect(report.findings, 'one finding: security-updates').to.have.length(1);
            const finding = report.findings[0];
            expect(finding.component).to.equal('security-updates');
            expect(parseInt(finding.installedVersion, 10), 'installed below introducedVersion 50.0.0').to.be.below(50);
            expect(finding.advisories.map(a => a.id), 'JSA-2099-0101 (introduced in 50.0.0) does not concern it')
                .to.deep.equal(['JSA-2099-0102']);
        });
    });
});
