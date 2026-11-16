import {createUser, deleteUser, grantRoles, revokeRoles} from '@jahia/cypress';
import {
    ADMIN_PATH,
    AXE_TAGS,
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
    expectStoredDismissalKeys,
    openAppPage,
    provision,
    raw,
    readBanner,
    reloadAppPage,
    until,
    waitForBannerDecision
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
    /** A TEST-ONLY button put right before the banner: the element focused before the keyboard goes into it. */
    const FOCUS_ORIGIN_ID = 'security-updates-e2e-focus-origin';

    /** Provisions a banner fixture and waits until the service decides with it. */
    const withBanner = (fileName: string, expected: Partial<Banner>): Cypress.Chainable<Banner> => {
        provision(fileName);
        return until(readBanner, expected, fileName);
    };

    /**
     * Puts the TEST-ONLY focus origin right before the banner and focuses it: a known element outside the banner,
     * from which the Tab key goes into the banner.
     */
    const focusOriginBeforeBanner = (): void => {
        cy.get(BANNER_DISMISS, {timeout: 30000}).should('be.visible');
        cy.get(BANNER).then($banner => {
            const button = $banner[0].ownerDocument.createElement('button');
            button.type = 'button';
            button.id = FOCUS_ORIGIN_ID;
            button.textContent = 'Focus origin (test)';
            // Fixed at the top left, so that it is visible whatever the position of the banner.
            button.style.cssText = 'position: fixed; top: 0; left: 0; z-index: 10000;';
            $banner[0].before(button);
        });
        cy.get(`#${FOCUS_ORIGIN_ID}`).focus();
        cy.focused().should('have.id', FOCUS_ORIGIN_ID);
    };

    /** From the focus origin, goes into the banner with the Tab key (native key events) up to "Hide this banner". */
    const tabToDismiss = (): void => {
        cy.press(Cypress.Keyboard.Keys.TAB);
        cy.focused().should('match', BANNER_LINK);
        cy.press(Cypress.Keyboard.Keys.TAB);
        cy.focused().should('match', BANNER_DISMISS);
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

    it('defaults: counts the two components, outside the Jahia UI tree, links to the page', () => {
        withBanner('banner-defaults.yml', {visible: true, count: 2, dismissalKeys: [KEY_CORE, KEY_OWNER, KEY_MODULE]}).then(banner => {
            expect(banner.minimumSeverity).to.equal('HIGH');
            expect(banner.components, 'distinct components').to.deep.equal(['jahia-core', 'security-updates']);
        });

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible').and('have.attr', 'data-count', '2');
        cy.get(BANNER).should('contain', '2 components need a security update (high severity or above).');
        cy.get(BANNER).should('have.attr', 'role', 'region').and('have.attr', 'lang');
        // A child of <body>, outside the React tree of the Jahia UI (which would remove it when it renders again).
        cy.get(BANNER).parent().should('match', 'body');
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
        waitForBannerDecision();
        cy.get(BANNER).should('not.exist');

        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible');
        cy.visit(ADMIN_PATH);
        cy.contains('h1', 'Security updates', {timeout: 60000}).should('be.visible');
        waitForBannerDecision();
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
        waitForBannerDecision();
        cy.get(BANNER).should('not.exist');
    });

    it('SESSION: hidden after "Hide this banner" and after a reload in the same tab, back in a new session', () => {
        withBanner('banner-defaults.yml', {visible: true, dismissPolicy: 'SESSION', count: 2}).then(banner => {
            cy.login();
            openAppPage();
            cy.get(BANNER_DISMISS, {timeout: 30000}).click();
            cy.get(BANNER).should('not.exist');
            cy.window().then(win => {
                const stored = JSON.parse(win.sessionStorage.getItem(SESSION_DISMISS_KEY) as string);
                expectStoredDismissalKeys(stored, banner.dismissalKeys, 'session dismissal');
            });

            reloadAppPage().then(answer => expect(answer.data?.securityUpdates?.banner?.visible).to.equal(true));
            waitForBannerDecision();
            cy.get(BANNER).should('not.exist');

            cy.clearAllSessionStorage();
            reloadAppPage();
            cy.get(BANNER, {timeout: 30000}).should('be.visible');
        });
    });

    it('top bar: the keyboard goes into it from the element before it, and "Hide this banner" gives the focus back', () => {
        withBanner('banner-defaults.yml', {visible: true, dismissPolicy: 'SESSION', count: 2, position: 'TOP'});
        cy.clearAllSessionStorage();
        cy.login();
        openAppPage();

        focusOriginBeforeBanner();
        tabToDismiss();
        cy.focused().type('{enter}');
        cy.get(BANNER).should('not.exist');
        cy.focused().should('have.id', FOCUS_ORIGIN_ID);
    });

    // A box in a corner (the last element of the page), so that a banner shown again comes after the focus origin.
    it('"Hide this banner" with the keyboard gives the focus back to the element focused before the banner', () => {
        withBanner('banner-position-bottom-right.yml', {visible: true, dismissPolicy: 'SESSION', count: 2, position: 'BOTTOM_RIGHT'});
        cy.clearAllSessionStorage();
        cy.login();
        openAppPage();

        // The focus went into the banner from a known element: Hide (Enter) gives it back to that element.
        focusOriginBeforeBanner();
        tabToDismiss();
        // Real Tab presses reach the button; cy.press only sends the raw key event, which Electron does not turn into
        // a button activation, so the activation key is typed into the focused button (Cypress simulates the click).
        cy.focused().type('{enter}');
        cy.get(BANNER).should('not.exist');
        cy.focused().should('have.id', FOCUS_ORIGIN_ID);

        // Neither the element focused before the banner nor the one focused when it appeared is still in the page:
        // the focus stays on the document. The banner comes back (new session, then a "changed" event) while the
        // focus origin has the focus, the keyboard goes into it from there, then the focus origin leaves the page.
        cy.window().then(win => {
            win.sessionStorage.removeItem(SESSION_DISMISS_KEY);
            win.dispatchEvent(new win.Event('security-updates:changed'));
        });
        cy.get(BANNER_DISMISS, {timeout: 30000}).should('be.visible');
        cy.focused().should('have.id', FOCUS_ORIGIN_ID);
        tabToDismiss();
        cy.get(`#${FOCUS_ORIGIN_ID}`).then($origin => $origin.remove());
        cy.focused().type(' ');
        cy.get(BANNER).should('not.exist');
        cy.document().should(doc => {
            expect(doc.getElementById(FOCUS_ORIGIN_ID), 'the focus origin is gone').to.equal(null);
            expect(doc.activeElement, 'the focus is on the document').to.equal(doc.body);
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
        waitForBannerDecision();
        cy.get(BANNER).should('not.exist');
    });

    describe('position (bannerPosition)', () => {
        /** The banner and the Jahia layout (Moonstone's LayoutApp), measured in the window. */
        type Layout = { banner: DOMRect; app: DOMRect; width: number; height: number };
        const measure = (check: (layout: Layout) => void): void => {
            cy.get(BANNER).should($banner => {
                const doc = $banner[0].ownerDocument;
                const app = doc.querySelector('.moonstone-layoutApp');
                expect(app, 'the Jahia layout').to.not.equal(null);
                check({
                    banner: $banner[0].getBoundingClientRect(),
                    app: (app as Element).getBoundingClientRect(),
                    width: doc.documentElement.clientWidth,
                    height: doc.documentElement.clientHeight
                });
            });
        };

        const showAt = (fileName: string, position: string): void => {
            withBanner(fileName, {visible: true, count: 2, position});
            cy.login();
            openAppPage();
            cy.get(BANNER, {timeout: 30000}).should('be.visible').and('have.attr', 'data-placement', position);
        };

        after(() => {
            cy.apolloClient();
            provision('banner-defaults.yml');
        });

        it('TOP (default): a full-width bar, first in the page, that pushes the Jahia UI down and covers none of it', () => {
            showAt('banner-defaults.yml', 'TOP');
            cy.get(BANNER).should('have.attr', 'data-layout', 'bar').and('have.attr', 'data-position', 'top');
            cy.get('body').children().first().should('match', BANNER);
            measure(({banner, app, width, height}) => {
                expect(banner.top, 'at the top of the window').to.equal(0);
                expect(banner.left, 'from the left edge').to.equal(0);
                expect(banner.width, 'to the right edge').to.be.closeTo(width, 1);
                expect(app.top, 'the Jahia UI starts under the bar').to.be.closeTo(banner.bottom, 1);
                expect(app.bottom, 'and still ends at the bottom of the window').to.be.closeTo(height, 1);
            });
            cy.injectAxe({axeCorePath: 'node_modules/axe-core/axe.min.js'});
            cy.checkA11y(BANNER, {runOnly: {type: 'tag', values: AXE_TAGS}});

            // Hidden: the Jahia UI takes the whole window again.
            cy.get(BANNER_DISMISS).click();
            cy.get(BANNER).should('not.exist');
            cy.get('.moonstone-layoutApp').should($app => {
                const rect = $app[0].getBoundingClientRect();
                expect(rect.top).to.equal(0);
                expect(rect.bottom).to.be.closeTo($app[0].ownerDocument.documentElement.clientHeight, 1);
            });
        });

        it('BOTTOM: a full-width bar, last in the page, under the Jahia UI', () => {
            showAt('banner-position-bottom.yml', 'BOTTOM');
            cy.get(BANNER).should('have.attr', 'data-layout', 'bar').and('have.attr', 'data-position', 'bottom');
            cy.get(BANNER).parent().should('match', 'body');
            measure(({banner, app, width, height}) => {
                expect(banner.bottom, 'at the bottom of the window').to.be.closeTo(height, 1);
                expect(banner.width, 'across the window').to.be.closeTo(width, 1);
                expect(app.top, 'the Jahia UI starts at the top').to.equal(0);
                expect(app.bottom, 'and ends above the bar').to.be.closeTo(banner.top, 1);
            });
        });

        it('TOP_RIGHT: a small box in the top right corner, over the Jahia UI, which keeps the whole window', () => {
            showAt('banner-position-top-right.yml', 'TOP_RIGHT');
            cy.get(BANNER).should('have.attr', 'data-layout', 'box').and('have.attr', 'data-position', 'top');
            measure(({banner, app, width, height}) => {
                expect(banner.top, 'near the top').to.be.lessThan(32);
                expect(width - banner.right, 'near the right edge').to.be.lessThan(32);
                expect(banner.width, 'a box, not a bar').to.be.lessThan(width / 2);
                expect(app.top).to.equal(0);
                expect(app.bottom).to.be.closeTo(height, 1);
            });
        });

        it('BOTTOM_RIGHT: a small box in the bottom right corner', () => {
            showAt('banner-position-bottom-right.yml', 'BOTTOM_RIGHT');
            cy.get(BANNER).should('have.attr', 'data-layout', 'box').and('have.attr', 'data-position', 'bottom');
            measure(({banner, width, height}) => {
                expect(height - banner.bottom, 'near the bottom').to.be.lessThan(32);
                expect(width - banner.right, 'near the right edge').to.be.lessThan(32);
                expect(banner.width, 'a box, not a bar').to.be.lessThan(width / 2);
            });
        });
    });
});
