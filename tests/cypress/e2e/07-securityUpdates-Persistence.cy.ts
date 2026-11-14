import {
    FIXTURE_SETTINGS,
    Settings,
    checkUntil,
    errorsOf,
    isFixtureReport,
    provision,
    raw,
    readBanner,
    readSettings,
    saveSettings,
    until
} from '../utils/securityUpdates';

/**
 * Settings saved through GraphQL are written to the OSGi configuration (karaf/etc), so they survive a stop and a
 * start of the module: still returned by the settings query, and still in effect (banner, checks).
 */
describe('Security updates: settings persistence', () => {
    const MODULE = 'security-updates';
    const SAVED: Partial<Settings> = {
        refreshIntervalHours: 12,
        bannerMinimumSeverity: 'CRITICAL',
        bannerBackgroundColor: '#0B3D91',
        bannerDismissPolicy: 'HOURS',
        bannerDismissHours: 6
    };

    const isAvailable = (result: unknown) => errorsOf(result).length === 0 &&
        Boolean((result as { data?: { securityUpdates?: unknown } }).data?.securityUpdates);

    const bundle = (operation: 'stopBundle' | 'startBundle') => {
        cy.runProvisioningScript({script: [{[operation]: MODULE}]});
    };

    before(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        checkUntil(isFixtureReport, 'the report of the fixture store');
    });

    after(() => {
        cy.apolloClient();
        // Whatever happened above, leave the module started.
        bundle('startBundle');
        cy.waitUntil(() => raw.report().then(isAvailable), {timeout: 120000, interval: 2000});
        provision('restore-defaults.yml');
    });

    it('keeps the saved settings, in effect, after the module is stopped and started', () => {
        saveSettings(SAVED).then(result => {
            expect(result.errors).to.deep.equal([]);
            expect(result.ok).to.equal(true);
        });
        until(readBanner, {minimumSeverity: 'CRITICAL', backgroundColor: '#0B3D91'}, 'saved settings in effect');

        bundle('stopBundle');
        // Positive signal that the module really stopped: its GraphQL fields are gone.
        cy.waitUntil(() => raw.report().then(result => !isAvailable(result)), {
            timeout: 120000,
            interval: 2000,
            errorMsg: 'the module never stopped'
        });

        bundle('startBundle');
        cy.waitUntil(() => raw.report().then(isAvailable), {
            timeout: 120000,
            interval: 2000,
            errorMsg: 'the module never started again'
        });

        readSettings().then(settings => expect(settings, 'the saved settings').to.deep.equal({...FIXTURE_SETTINGS, ...SAVED}));
        until(readBanner, {minimumSeverity: 'CRITICAL', backgroundColor: '#0B3D91', dismissPolicy: 'HOURS', dismissHours: 6}, 'banner settings after the restart');

        // The report is per node and in memory: a new check, with the saved settings (fixture store, CRITICAL only).
        checkUntil(isFixtureReport, 'the report of the fixture store after the restart');
        until(readBanner, {visible: true, count: 1, components: ['jahia-core']}, 'banner counting critical findings only');
    });
});
