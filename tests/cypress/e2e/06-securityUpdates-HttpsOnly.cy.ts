import {
    ADMIN_PATH,
    FIXTURE_SETTINGS,
    Report,
    checkUntil,
    isFixtureReport,
    provision,
    readSettings,
    saveSettings,
    until
} from '../utils/securityUpdates';

/**
 * Plain http stores and feeds are refused: the settings refuse http addresses (NOT_HTTPS), and an http store set in
 * the configuration (here through provisioning, which skips the validation of the settings form) is reported as
 * INVALID_URL / NOT_HTTPS and never fetched.
 *
 * Port 80 of the fixture store serves a trap: a feed with advisory JSA-2099-0666 for jahia-core, which would show up
 * in the report if the module read http://storefixture.
 */
describe('Security updates: https only', () => {
    const HTTP_STORE = 'http://storefixture';
    const TRAP_FEED = `${HTTP_STORE}/contents/modules-repository.securityUpdates.json`;
    const TRAP_ADVISORY = 'JSA-2099-0666';

    const isRefusedHttpStore = (report: Report) => report.errors.some(e => e.kind === 'INVALID_URL' &&
        e.detail === 'NOT_HTTPS' &&
        (e.storeUrl ?? '').startsWith(HTTP_STORE));

    before(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        checkUntil(isFixtureReport, 'the report of the https fixture store');
    });

    beforeEach(() => {
        cy.apolloClient();
    });

    after(() => {
        cy.apolloClient();
        provision('restore-defaults.yml');
    });

    it('the settings refuse http addresses and keep the saved ones', () => {
        saveSettings({defaultStoreUrl: HTTP_STORE}).then(result => {
            expect(result.ok).to.equal(false);
            expect(result.errors.map(e => `${e.field}:${e.code}`)).to.deep.equal(['defaultStoreUrl:NOT_HTTPS']);
        });
        saveSettings({extraFeedUrls: TRAP_FEED}).then(result => {
            expect(result.ok).to.equal(false);
            expect(result.errors.map(e => `${e.field}:${e.code}`)).to.deep.equal(['extraFeedUrls:NOT_HTTPS']);
        });
        readSettings().then(settings => expect(settings, 'unchanged').to.deep.equal(FIXTURE_SETTINGS));
    });

    it('an http store in the configuration is reported as refused and never fetched', () => {
        // Positive control: the trap is served over http on the test network, so a fetch would bring its advisory.
        cy.request(TRAP_FEED).then(response => {
            expect(response.status).to.equal(200);
            expect(JSON.stringify(response.body)).to.contain(TRAP_ADVISORY);
        });

        provision('http-store.yml');
        // Positive signal first: a check ran with the http store, and refused it.
        checkUntil(isRefusedHttpStore, 'a check refusing the http store').then(report => {
            expect(report.status, 'no store could be read').to.equal('UNAVAILABLE');
            expect(report.findings, 'nothing read from the http store').to.deep.equal([]);
            expect(JSON.stringify(report), 'the trap advisory').to.not.contain(TRAP_ADVISORY);
        });

        cy.login();
        cy.visit(ADMIN_PATH);
        cy.contains('h2', 'Problems found during the last check').should('be.visible');
        cy.contains('li', 'Store address refused')
            .should('contain', 'The address does not use https.')
            .and('contain', `Store: ${HTTP_STORE}`);
        cy.contains('No security data').should('be.visible');
        cy.contains(TRAP_ADVISORY).should('not.exist');
    });
});
