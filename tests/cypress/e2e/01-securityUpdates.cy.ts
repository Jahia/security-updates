import {
    ADMIN_PATH,
    BANNER,
    answerOf,
    checkPageAccessibility,
    checkUntil,
    interceptPageOperations,
    isFixtureReport,
    named,
    openAppPage,
    provision,
    readReport,
    refresh
} from '../utils/securityUpdates';

/**
 * The report, the non-blocking "Check now" and the admin page, against the fixture store (https://storefixture, see
 * tests/storefixture): three findings, no store error.
 */
describe('Security updates: report, Check now and the admin page', () => {
    const STATUSES = ['NOT_CHECKED', 'DISABLED', 'OK', 'PARTIAL', 'UNAVAILABLE'];
    const ISO_DATE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

    before(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        checkUntil(isFixtureReport, 'the report of the fixture store');
    });

    beforeEach(() => {
        cy.apolloClient();
    });

    after(() => {
        cy.apolloClient();
        provision('restore-defaults.yml');
    });

    it('exposes a complete, current report under the securityUpdates namespace', () => {
        readReport().then(report => {
            expect(report.status).to.equal('OK');
            expect(report.inProgress, 'no check running').to.equal(false);
            expect(report.stale, 'not stale').to.equal(false);
            expect(report.jahiaVersion).to.match(/^8\.\d+\.\d+\.\d+$/);
            expect(report.checkedAt, 'ISO-8601 check time').to.match(ISO_DATE);
            expect(report.lastSuccess, 'last complete check').to.match(ISO_DATE);
            expect(report.nextCheckAt, 'next scheduled check').to.match(ISO_DATE);
            expect(report.errors).to.deep.equal([]);
            expect(report.findings).to.have.length(3);
        });
    });

    it('the refresh mutation starts a check in the background and returns at once', () => {
        readReport().then(before => {
            refresh().then(started => {
                expect(STATUSES).to.include(started.status);
                expect(started.inProgress, 'a check runs').to.equal(true);
                expect(started.checkedAt, 'the report of the previous check, returned at once').to.equal(before.checkedAt);
            });
            const ended = () => readReport().then(report => (report.inProgress ? false : report));
            cy.waitUntil(ended, {
                timeout: 120000,
                interval: 1000,
                errorMsg: 'the check never ended'
            }).then((report: typeof before) => {
                expect(Date.parse(report.checkedAt as string), 'a newer check').to.be.greaterThan(Date.parse(before.checkedAt as string));
                expect(isFixtureReport(report), 'same results').to.equal(true);
            });
        });
    });

    it('renders the page: status chip, dates, findings in a named scrolling region, no store problem', () => {
        cy.login();
        // Positive signal for the banner absence asserted below: on the other pages of the Jahia UI, this report
        // shows the banner (the banner script sends no request at all on the Security updates page).
        openAppPage().then(answer => expect(answer.data?.securityUpdates?.banner?.visible, 'the banner is due').to.equal(true));
        cy.get(BANNER, {timeout: 30000}).should('be.visible');

        cy.visit(ADMIN_PATH);
        cy.contains('h1', 'Security updates').should('be.visible');
        cy.title().should('contain', 'Security updates');
        cy.contains('button', 'Check now').should('be.visible').and('not.have.attr', 'aria-busy');
        cy.contains('Updates needed').should('be.visible');
        cy.get('[data-testid="security-updates-status"]').should('have.attr', 'role', 'status');
        cy.contains('dt', 'Last check').should('exist');
        cy.contains('dt', 'Next check').next('dd').invoke('text').should('not.equal', 'Not planned');
        cy.contains('a', 'Open Manage modules').should('have.attr', 'href').and('contain', '/jahia/administration/manageModules');

        cy.contains('h2', 'Components to update').should('have.attr', 'id', 'security-updates-findings-title');
        cy.get('[role="region"][aria-labelledby="security-updates-findings-title"]')
            .should('have.attr', 'tabindex', '0')
            .within(() => {
                cy.get('table caption').should('contain', 'Components with security updates');
                cy.get('th[scope="col"]').should('have.length', 8);
                cy.get('tbody tr').should('have.length', 3);
            });

        // Only now that the findings are shown (the same answer as the store problems): the store was read without a
        // problem, and the banner, shown on the previous page, is not on this one.
        cy.get('#security-updates-errors-title').should('not.exist');
        cy.contains('h2', 'Problems found during the last check').should('not.exist');
        cy.get(BANNER).should('not.exist');
    });

    it('Check now shows that a check runs, polls the report, then announces the new results', () => {
        cy.login();
        interceptPageOperations();
        cy.visit(ADMIN_PATH);
        cy.contains('h2', 'Components to update').should('be.visible');

        cy.contains('button', 'Check now').click();
        cy.contains('button', 'Checking…').should('have.attr', 'aria-busy', 'true');
        cy.get('[data-testid="security-updates-checking"]').should('contain', 'Checking the stores');
        cy.wait('@SecurityUpdatesRefresh').then(interception => {
            type RefreshAnswer = { data?: { securityUpdates?: { refresh?: { inProgress?: boolean } } } };
            const answer = answerOf<RefreshAnswer>(interception, named('SecurityUpdatesRefresh'), 'SecurityUpdatesRefresh');
            expect(answer.data?.securityUpdates?.refresh?.inProgress, 'the mutation only started the check').to.equal(true);
        });

        cy.get('[data-testid="security-updates-status"]', {timeout: 120000})
            .should('contain', 'Check completed. 2 components need a security update.');
        cy.contains('button', 'Check now').should('not.have.attr', 'aria-busy');
        cy.get('[data-testid="security-updates-checking"]').should('not.exist');
    });

    it('the page passes axe (WCAG 2.2 A and AA rules, and the AAA rules axe has)', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        cy.get('[role="region"][aria-labelledby="security-updates-findings-title"] tbody tr').should('have.length', 3);
        cy.get('#security-updates-setting-defaultStoreUrl').should('have.value', 'https://storefixture');
        checkPageAccessibility();
    });

    it('Check now within the on-demand interval says that no check was started', () => {
        provision('rate-limited.yml');
        // Positive signal first: the limit is in effect once a refresh, with no check running, starts nothing.
        cy.waitUntil(() => refresh().then(answer => !answer.inProgress), {
            timeout: 120000,
            interval: 1000,
            errorMsg: 'the one-hour on-demand limit never applied'
        });

        cy.login();
        cy.visit(ADMIN_PATH);
        cy.contains('h2', 'Components to update').should('be.visible');
        cy.contains('button', 'Check now').click();
        cy.get('[data-testid="security-updates-status"]', {timeout: 30000})
            .should('contain', 'A check ran a moment ago, so no new check was started.');
        cy.contains('button', 'Check now').should('not.have.attr', 'aria-busy');
    });
});
