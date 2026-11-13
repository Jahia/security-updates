import {createUser, deleteUser, grantRoles, revokeRoles} from '@jahia/cypress';
import {
    ADMIN_PATH,
    BANNER,
    BANNER_DISMISS,
    BANNER_LINK,
    Banner,
    KEY_CORE,
    KEY_MODULE,
    KEY_OWNER,
    SESSION_DISMISS_KEY,
    ensureFixtureReport,
    errorText,
    openAppPage,
    provision,
    raw,
    readBanner,
    until,
    waitForBannerQuery
} from '../utils/securityUpdates';

/**
 * The banner shown in the Jahia UI, decided server-side (Query.securityUpdates.banner) from the report and the
 * settings bannerEnabled, bannerMinimumSeverity and bannerIncludeOwnerReports. It counts distinct components, and
 * its dismissal identity is the set of dismissalKeys (<component>|<advisory id>).
 *
 * The fixture store gives three findings: jahia-core ADVISORY CRITICAL (JSA-2099-0001), security-updates ADVISORY
 * HIGH (JSA-2099-0002) and security-updates OWNER_REPORTED HIGH (GHSA-2c2c-3f3f-4g4g), so two components.
 */
describe('Security updates: banner', () => {
    const DENIED_USER = 'securityUpdatesBannerUser';
    const PASSWORD = 'SecUpdBanner9-Test';

    /** Provisions a banner fixture and waits until the service decides with it. */
    const withBanner = (fileName: string, expected: Partial<Banner>): Cypress.Chainable<Banner> => {
        provision(fileName);
        return until(readBanner, expected, fileName);
    };

    before(() => {
        cy.apolloClient();
        provision('banner-defaults.yml');
        ensureFixtureReport();
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        cy.apolloClient();
        grantRoles('/sites/systemsite', ['editor'], DENIED_USER, 'USER');
    });

    beforeEach(() => {
        cy.apolloClient();
        ensureFixtureReport();
    });

    after(() => {
        cy.apolloClient();
        revokeRoles('/sites/systemsite', ['editor'], DENIED_USER, 'USER');
        cy.login();
        deleteUser(DENIED_USER);
        cy.apolloClient();
        provision('restore-defaults.yml');
    });

    it('defaults: counts the two components, first in the page, links to the page', () => {
        withBanner('banner-defaults.yml', {visible: true, count: 2, dismissalKeys: [KEY_CORE, KEY_OWNER, KEY_MODULE]}).then(banner => {
            expect(banner.minimumSeverity).to.equal('HIGH');
            expect(banner.components, 'distinct components').to.deep.equal(['jahia-core', 'security-updates']);
        });

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible').and('have.attr', 'data-count', '2');
        cy.get(BANNER).should('contain', '2 components need a security update (high severity or above).');
        cy.get(BANNER).should('have.attr', 'role', 'region').and('have.attr', 'lang');
        cy.get('body').children().first().should('have.id', 'security-updates-banner');
        cy.get(BANNER_LINK)
            .should('contain', 'View security updates')
            .and('have.attr', 'href')
            .and('match', /\/jahia\/administration\/securityUpdates$/);
        cy.get(BANNER_DISMISS).should('contain', 'Hide this banner');
    });

    it('is not shown on the Security updates page, and comes back on the other pages', () => {
        withBanner('banner-defaults.yml', {visible: true, count: 2});
        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible');

        cy.get(BANNER_LINK).click();
        cy.location('pathname', {timeout: 30000}).should('match', /\/administration\/securityUpdates$/);
        cy.contains('h1', 'Security updates', {timeout: 60000}).should('be.visible');
        cy.get(BANNER).should('not.exist');

        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible');
        cy.visit(ADMIN_PATH);
        cy.contains('h1', 'Security updates', {timeout: 60000}).should('be.visible');
        cy.get(BANNER).should('not.exist');
    });

    it('bannerIncludeOwnerReports=false: same two components, without the owner report key', () => {
        withBanner('banner-advisories-only.yml', {visible: true, dismissalKeys: [KEY_CORE, KEY_MODULE]}).then(banner => {
            expect(banner.count).to.equal(2);
            expect(banner.components).to.deep.equal(['jahia-core', 'security-updates']);
        });

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('have.attr', 'data-count', '2');
    });

    it('bannerMinimumSeverity=CRITICAL: counts the critical component only', () => {
        withBanner('banner-critical-only.yml', {minimumSeverity: 'CRITICAL', count: 1}).then(banner => {
            expect(banner.visible).to.equal(true);
            expect(banner.components).to.deep.equal(['jahia-core']);
            expect(banner.dismissalKeys).to.deep.equal([KEY_CORE]);
        });

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000})
            .should('have.attr', 'data-count', '1')
            .and('contain', '1 component needs a security update (critical severity).');
    });

    it('bannerEnabled=false: the server says not visible, and no banner is shown', () => {
        withBanner('banner-disabled.yml', {visible: false});

        cy.login();
        // Positive signal first: the answer to the banner request, which says not visible.
        openAppPage().then(answer => {
            expect(answer.data?.jcr?.nodeByPath?.hasPermission).to.equal(true);
            expect(answer.data?.securityUpdates?.banner?.visible).to.equal(false);
        });
        cy.get(BANNER).should('not.exist');
    });

    it('SESSION: hidden after "Hide this banner" and after a reload in the same tab, back in a new session', () => {
        withBanner('banner-defaults.yml', {visible: true, dismissPolicy: 'SESSION', count: 2}).then(banner => {
            cy.login();
            openAppPage();
            cy.get(BANNER_DISMISS, {timeout: 30000}).click();
            cy.get(BANNER).should('not.exist');
            cy.document().then(doc => {
                expect(doc.activeElement?.closest(BANNER) ?? null, 'the focus is not lost in the removed banner').to.equal(null);
            });
            cy.window().then(win => {
                expect(JSON.parse(win.sessionStorage.getItem(SESSION_DISMISS_KEY) as string)).to.deep.equal(banner.dismissalKeys);
            });

            cy.reload();
            waitForBannerQuery().then(answer => expect(answer.data?.securityUpdates?.banner?.visible).to.equal(true));
            cy.get(BANNER).should('not.exist');

            cy.clearAllSessionStorage();
            cy.reload();
            waitForBannerQuery();
            cy.get(BANNER, {timeout: 30000}).should('be.visible');
        });
    });

    it('a user without adminTemplates never gets the banner', () => {
        withBanner('banner-defaults.yml', {visible: true});

        cy.apolloClient({username: DENIED_USER, password: PASSWORD});
        raw.banner().then(result => expect(errorText(result)).to.contain('Permission denied'));

        cy.login(DENIED_USER, PASSWORD);
        // Positive signal first: the Jahia UI loaded for this user and its banner request was answered.
        openAppPage().then(answer => {
            expect(answer.data?.jcr?.nodeByPath?.hasPermission ?? false, 'no adminTemplates').to.equal(false);
        });
        cy.get('.moonstone-primaryNav', {timeout: 60000}).should('be.visible');
        cy.get(BANNER).should('not.exist');
    });
});
