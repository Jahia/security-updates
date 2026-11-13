import {createUser, deleteUser, grantRoles, revokeRoles} from '@jahia/cypress';
import {
    ADMIN_PATH,
    APP_PATH,
    BANNER,
    DEFAULT_SETTINGS,
    FIXTURE_SETTINGS,
    checkUntil,
    ensureFixtureReport,
    errorText,
    isFixtureReport,
    openAppPage,
    provision,
    raw,
    readBanner,
    readReport,
    readSettings,
    readStoredSettings,
    refresh,
    resetSettings,
    runCheck,
    saveSettings,
    settingsOf,
    until
} from '../utils/securityUpdates';

/**
 * Who may do what:
 *  - adminTemplates (here the web-designer role, granted on the server): report, banner, settings (read-only:
 *    canEdit false), refresh, and the admin page;
 *  - admin (server administrators, here root): also updateSettings and resetSettings;
 *  - neither (here an editor of systemsite, who can open the Jahia UI): nothing, and no banner.
 */
describe('Security updates: permissions', () => {
    const DESIGNER = 'securityUpdatesDesigner';
    const DENIED_USER = 'securityUpdatesDeniedUser';
    const PASSWORD = 'SecUpd9Pwd-Test';
    const DENIED = 'Permission denied';
    const field = (name: string) => `#security-updates-setting-${name}`;

    const asRoot = () => cy.apolloClient();
    const asDesigner = () => cy.apolloClient({username: DESIGNER, password: PASSWORD});
    const asDenied = () => cy.apolloClient({username: DENIED_USER, password: PASSWORD});

    before(() => {
        asRoot();
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        checkUntil(isFixtureReport, 'the report of the fixture store');
        cy.login();
        createUser(DESIGNER, PASSWORD);
        createUser(DENIED_USER, PASSWORD);
        asRoot();
        grantRoles('/', ['web-designer'], DESIGNER, 'USER');
        grantRoles('/sites/systemsite', ['editor'], DENIED_USER, 'USER');
    });

    beforeEach(() => {
        asRoot();
    });

    after(() => {
        asRoot();
        revokeRoles('/', ['web-designer'], DESIGNER, 'USER');
        revokeRoles('/sites/systemsite', ['editor'], DENIED_USER, 'USER');
        cy.login();
        deleteUser(DESIGNER);
        deleteUser(DENIED_USER);
        asRoot();
        provision('restore-defaults.yml');
    });

    it('a web designer (adminTemplates, not admin) reads the report, the banner and the settings', () => {
        asDesigner();
        readReport().then(report => expect(isFixtureReport(report), 'the fixture report').to.equal(true));
        readBanner().then(banner => {
            expect(banner.visible).to.equal(true);
            expect(banner.username).to.equal(DESIGNER);
        });
        readStoredSettings().then(settings => {
            expect(settingsOf(settings)).to.deep.equal(FIXTURE_SETTINGS);
            expect(settings.canEdit, 'read-only for a web designer').to.equal(false);
        });
    });

    it('a web designer starts a check', () => {
        asDesigner();
        refresh().then(answer => expect(answer.inProgress, 'a check runs').to.equal(true));
        runCheck().then(report => expect(isFixtureReport(report)).to.equal(true));
    });

    it('a web designer can neither update nor reset the settings, which stay unchanged', () => {
        asDesigner();
        raw.update({refreshIntervalHours: 1, defaultStoreUrl: 'https://attacker.example.org'}).then(result => {
            expect(errorText(result)).to.contain(DENIED);
        });
        raw.reset().then(result => {
            expect(errorText(result)).to.contain(DENIED);
        });

        asRoot();
        readStoredSettings().then(settings => {
            expect(settings.canEdit, 'root may edit').to.equal(true);
            expect(settingsOf(settings), 'unchanged').to.deep.equal(FIXTURE_SETTINGS);
        });
    });

    it('a server administrator (root) can update and reset the settings', () => {
        saveSettings({refreshIntervalHours: 12, bannerMinimumSeverity: 'CRITICAL'}).then(result => {
            expect(result.errors).to.deep.equal([]);
            expect(result.ok).to.equal(true);
            expect(result.settings?.refreshIntervalHours, 'the saved values are returned').to.equal(12);
            expect(result.settings?.bannerMinimumSeverity).to.equal('CRITICAL');
        });
        readSettings().then(settings => expect(settings).to.deep.equal({
            ...FIXTURE_SETTINGS,
            refreshIntervalHours: 12,
            bannerMinimumSeverity: 'CRITICAL'
        }));

        resetSettings().then(result => {
            expect(result.ok).to.equal(true);
            expect(settingsOf(result.settings as NonNullable<typeof result.settings>)).to.deep.equal(DEFAULT_SETTINGS);
        });
        readSettings().then(settings => expect(settings, 'the module defaults').to.deep.equal(DEFAULT_SETTINGS));

        // Back to the fixture store for the next tests.
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        ensureFixtureReport();
    });

    it('a user without adminTemplates gets "Permission denied" on every field and mutation', () => {
        asDenied();
        raw.report().then(result => expect(errorText(result)).to.contain(DENIED));
        raw.banner().then(result => expect(errorText(result)).to.contain(DENIED));
        raw.settings().then(result => expect(errorText(result)).to.contain(DENIED));
        raw.refresh().then(result => expect(errorText(result)).to.contain(DENIED));
        raw.update({refreshIntervalHours: 1}).then(result => expect(errorText(result)).to.contain(DENIED));
        raw.reset().then(result => expect(errorText(result)).to.contain(DENIED));

        asRoot();
        readSettings().then(settings => expect(settings, 'unchanged').to.deep.equal(FIXTURE_SETTINGS));
    });

    it('a web designer opens the page, runs Check now and sees the settings read-only', () => {
        asRoot();
        ensureFixtureReport();
        cy.login(DESIGNER, PASSWORD);
        cy.visit(ADMIN_PATH);
        cy.contains('h1', 'Security updates').should('be.visible');
        cy.contains('h2', 'Components to update').should('be.visible');

        cy.contains('button', 'Check now').click();
        cy.get('[data-testid="security-updates-status"]', {timeout: 120000})
            .should('contain', 'Check completed. The page shows the new results.');

        // The values are there (positive signal), and cannot be changed.
        cy.get(field('defaultStoreUrl')).should('have.value', 'https://storefixture').and('be.disabled');
        cy.get(field('refreshIntervalHours')).should('have.value', '24').and('be.disabled');
        cy.contains('Only server administrators can change these settings. They are shown here for information.')
            .should('be.visible');
        cy.contains('button', 'Save settings').should('not.exist');
        cy.contains('button', 'Reset to defaults').should('not.exist');
    });

    it('a web designer gets the banner', () => {
        cy.login(DESIGNER, PASSWORD);
        openAppPage().then(answer => {
            expect(answer.data?.jcr?.nodeByPath?.hasPermission, 'adminTemplates').to.equal(true);
            expect(answer.data?.securityUpdates?.banner?.visible).to.equal(true);
        });
        cy.get(BANNER, {timeout: 30000}).should('be.visible').and('have.attr', 'data-count', '2');
    });

    it('a user without adminTemplates opens the Jahia UI, but gets neither the banner nor the page', () => {
        cy.login(DENIED_USER, PASSWORD);
        // Positive signals first: the Jahia UI loaded for this user and the banner callback asked the server.
        openAppPage(APP_PATH).then(answer => {
            expect(answer.data?.jcr?.nodeByPath?.hasPermission ?? false, 'no adminTemplates').to.equal(false);
        });
        cy.get('.moonstone-primaryNav', {timeout: 60000}).should('be.visible');
        cy.get(BANNER).should('not.exist');

        cy.visit(ADMIN_PATH, {failOnStatusCode: false});
        cy.get('.moonstone-primaryNav', {timeout: 60000}).should('be.visible');
        cy.contains('button', 'Check now').should('not.exist');
        cy.get(field('refreshIntervalHours')).should('not.exist');
    });
});
