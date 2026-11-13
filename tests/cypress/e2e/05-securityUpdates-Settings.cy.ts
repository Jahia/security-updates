import {
    ADMIN_PATH,
    BANNER,
    BANNER_DISMISS,
    DEFAULT_SETTINGS,
    FIXTURE_SETTINGS,
    FieldError,
    KEY_CORE,
    KEY_MODULE,
    KEY_OWNER,
    ROOT_LOCAL_DISMISS_KEYS,
    Settings,
    checkPageAccessibility,
    ensureFixtureReport,
    openAppPage,
    provision,
    readBanner,
    readSettings,
    saveSettings,
    until,
    waitForBannerQuery
} from '../utils/securityUpdates';

/**
 * The settings form of /jahia/administration/securityUpdates and the settings GraphQL API as a server administrator
 * (root), with the banner colours and dismissal policies they drive. Who may change them: spec 02.
 *
 * Fixture store: two components (jahia-core CRITICAL; security-updates HIGH advisory and HIGH owner report).
 */
describe('Security updates: settings', () => {
    const STATUS = '[data-testid="security-updates-settings-status"]';
    const field = (name: string) => `#security-updates-setting-${name}`;
    const fill = (name: string, value: string, options?: Partial<Cypress.TypeOptions>) => {
        cy.get(field(name)).clear();
        cy.get(field(name)).type(value, options);
    };

    const ELEVEN_FEEDS = Array.from({length: 11}, (_, i) => `https://feed${i}.example.org/security.json`).join(', ');

    const save = (overrides: Partial<Settings>) => saveSettings(overrides).then(result => {
        expect(result.errors, JSON.stringify(result.errors)).to.deep.equal([]);
        expect(result.ok).to.equal(true);
    });

    const codesOf = (errors: FieldError[]) => errors.map(e => `${e.field}:${e.code}`).sort();

    /** The localStorage record of root's dismissal, under the hashed user key (never the user name itself). */
    const localRecord = (win: Window) => {
        const keys = Object.keys(win.localStorage).filter(key => key.startsWith('security-updates-banner-dismissed'));
        expect(keys, 'one dismissal record').to.have.length(1);
        expect(ROOT_LOCAL_DISMISS_KEYS, 'key made of the hashed user name').to.include(keys[0]);
        expect(keys[0]).to.not.contain('root');
        return {key: keys[0], record: JSON.parse(win.localStorage.getItem(keys[0]) as string)};
    };

    const reloadApp = () => {
        cy.reload();
        return waitForBannerQuery();
    };

    before(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        ensureFixtureReport();
    });

    beforeEach(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');
        until(readBanner, {dismissPolicy: 'SESSION', backgroundColor: '#1B1B1B', minimumSeverity: 'HIGH'}, 'fixture banner');
        ensureFixtureReport();
    });

    after(() => {
        cy.apolloClient();
        provision('restore-defaults.yml');
    });

    it('the form shows the stored values, with native labels, to a server administrator', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        cy.contains('h2', 'Settings').should('be.visible');
        cy.get(field('defaultStoreUrl')).should('have.value', 'https://storefixture').and('not.be.disabled');
        cy.get(field('enabled')).should('be.checked');
        cy.get(field('refreshIntervalHours')).should('have.value', '24');
        cy.get(field('useForgeSettings')).should('not.be.checked');
        cy.get(field('extraFeedUrls')).should('have.value', '');
        cy.get(field('bannerEnabled')).should('be.checked');
        cy.get(field('bannerMinimumSeverity')).should('have.value', 'HIGH');
        cy.get(field('bannerIncludeOwnerReports')).should('be.checked');
        cy.get(field('bannerBackgroundColor')).should('have.value', '#1B1B1B');
        cy.get(field('bannerTextColor')).should('have.value', '#FFFFFF');
        cy.get(field('bannerDismissPolicy')).should('have.value', 'SESSION');
        cy.get(field('bannerDismissHours')).should('have.value', '24').and('be.disabled');
        cy.get('[data-testid="security-updates-contrast"]').should('contain', '17.22:1');

        cy.get('label[for="security-updates-setting-defaultStoreUrl"]').should('contain', 'Default store address');
        cy.get('label[for="security-updates-setting-extraFeedUrls"]').should('contain', 'Other security data addresses');
        cy.get('label[for="security-updates-setting-bannerEnabled"]').should('contain', 'Show the banner');
        cy.get('label[for="security-updates-setting-bannerBackgroundColor"]').should('contain', 'Banner background colour');
        cy.get(field('defaultStoreUrl')).should('not.have.attr', 'aria-label');
        cy.contains('button', 'Save settings').should('have.attr', 'type', 'submit');
        cy.contains('Only server administrators can change these settings').should('not.exist');
    });

    it('saving from the form changes the settings and the banner', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        fill('refreshIntervalHours', '12');
        cy.get(field('bannerMinimumSeverity')).select('CRITICAL');
        cy.contains('button', 'Save settings').click();
        cy.get(STATUS).should('contain', 'Settings saved. The next check runs in about one minute.');
        cy.get(field('refreshIntervalHours')).should('have.value', '12');

        until(readSettings, {refreshIntervalHours: 12, bannerMinimumSeverity: 'CRITICAL'}, 'saved settings').then(settings => {
            expect(settings.defaultStoreUrl, 'other values are kept').to.equal('https://storefixture');
        });
        until(readBanner, {count: 1}, 'banner with the new threshold').then(banner => {
            expect(banner.components).to.deep.equal(['jahia-core']);
        });
    });

    it('invalid input in the form shows field errors (https only, at most 10 addresses), changes nothing, and passes axe', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        fill('refreshIntervalHours', '0');
        fill('defaultStoreUrl', 'http://store.example.org');
        fill('extraFeedUrls', ELEVEN_FEEDS, {delay: 0});
        fill('bannerBackgroundColor', '#888888');
        fill('bannerTextColor', '#777777');
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Below the 7:1 minimum');
        cy.contains('button', 'Save settings').click();

        cy.get('[role="alert"]').should('contain', 'Some settings are not valid: nothing was saved.');
        ['refreshIntervalHours', 'defaultStoreUrl', 'extraFeedUrls', 'bannerBackgroundColor', 'bannerTextColor'].forEach(name => {
            cy.get(field(name)).should('have.attr', 'aria-invalid', 'true')
                .and('have.attr', 'aria-describedby')
                .and('contain', `security-updates-setting-${name}-error`);
            // The form is taller than the admin viewport: scroll as a user would before checking visibility.
            cy.get(`${field(name)}-error`).scrollIntoView();
            cy.get(`${field(name)}-error`).should('be.visible');
        });
        cy.get(`${field('defaultStoreUrl')}-error`).should('contain', 'Enter https addresses only: http addresses are refused.');
        cy.get(`${field('extraFeedUrls')}-error`).should('contain', 'Enter at most 10 addresses.');
        cy.get(`${field('refreshIntervalHours')}-error`).should('contain', 'Enter a whole number from 1 to 720.');
        cy.get(`${field('bannerTextColor')}-error`).should('contain', '1.26:1');
        cy.focused().should('have.id', 'security-updates-setting-refreshIntervalHours');

        checkPageAccessibility();

        readSettings().then(settings => expect(settings, 'nothing saved').to.deep.equal(FIXTURE_SETTINGS));
    });

    it('invalid input through GraphQL is refused with field errors and changes nothing', () => {
        saveSettings({
            bannerBackgroundColor: '#12345',
            refreshIntervalHours: 0,
            defaultStoreUrl: 'http://store.example.org',
            extraFeedUrls: ELEVEN_FEEDS
        }).then(result => {
            expect(result.ok).to.equal(false);
            expect(result.settings, 'nothing saved').to.equal(null);
            expect(codesOf(result.errors)).to.deep.equal([
                'bannerBackgroundColor:INVALID_COLOR',
                'defaultStoreUrl:NOT_HTTPS',
                'extraFeedUrls:TOO_MANY',
                'refreshIntervalHours:OUT_OF_RANGE'
            ]);
        });
        saveSettings({extraFeedUrls: 'https://feed.example.org/security.json http://feed.example.org/security.json'}).then(result => {
            expect(codesOf(result.errors)).to.deep.equal(['extraFeedUrls:NOT_HTTPS']);
        });
        saveSettings({extraFeedUrls: 'not a url'}).then(result => {
            expect(codesOf(result.errors)).to.deep.equal(['extraFeedUrls:INVALID_URL']);
        });
        saveSettings({bannerBackgroundColor: '#777777', bannerTextColor: '#888888'}).then(result => {
            expect(result.ok).to.equal(false);
            expect(result.errors.map(e => e.code)).to.deep.equal(['LOW_CONTRAST', 'LOW_CONTRAST']);
        });
        readSettings().then(settings => expect(settings, 'unchanged').to.deep.equal(FIXTURE_SETTINGS));
    });

    it('ten https feeds are accepted', () => {
        const ten = Array.from({length: 10}, (_, i) => `https://feed${i}.example.org/security.json`).join(', ');
        saveSettings({extraFeedUrls: ten}).then(result => {
            expect(result.errors).to.deep.equal([]);
            expect(result.ok).to.equal(true);
        });
        readSettings().then(settings => expect(settings.extraFeedUrls).to.contain('https://feed9.example.org/security.json'));
    });

    it('a low contrast suggests colours: through GraphQL, and in the form where one click applies them', () => {
        saveSettings({bannerBackgroundColor: '#1B1B1B', bannerTextColor: '#333333'}).then(result => {
            expect(result.ok).to.equal(false);
            const error = result.errors.find(e => e.field === 'bannerTextColor') as FieldError;
            expect(error.code).to.equal('LOW_CONTRAST');
            expect(error.suggestions.map(s => `${s.field}=${s.value}@${s.ratio}`)).to.deep.equal([
                'bannerTextColor=#A6A6A6@7.07',
                'bannerBackgroundColor=#C1C1C1@7.01'
            ]);
        });

        cy.login();
        cy.visit(ADMIN_PATH);
        fill('bannerTextColor', '#333333');
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Below the 7:1 minimum');
        cy.get('[data-testid="security-updates-suggestion-text"]').should('contain', '#A6A6A6').and('contain', '7.07:1');
        cy.get('[data-testid="security-updates-suggestion-background"]').should('contain', '#C1C1C1');
        cy.contains('button', 'Use this text colour #A6A6A6').click();
        cy.get(field('bannerTextColor')).should('have.value', '#A6A6A6');
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Meets the 7:1 minimum');
        cy.get(STATUS).should('contain', 'Text colour #A6A6A6 applied');
        cy.contains('button', 'Save settings').click();
        cy.get(STATUS).should('contain', 'Settings saved.');

        until(readBanner, {backgroundColor: '#1B1B1B', textColor: '#A6A6A6'}, 'banner colours');
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('have.css', 'background-color', 'rgb(27, 27, 27)');
        cy.get(BANNER).should('have.css', 'color', 'rgb(166, 166, 166)');
    });

    it('the banner uses the configured colours', () => {
        save({bannerBackgroundColor: '#0B3D91', bannerTextColor: '#FFFFFF'});
        until(readBanner, {backgroundColor: '#0B3D91', textColor: '#FFFFFF'}, 'banner colours');

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('have.css', 'background-color', 'rgb(11, 61, 145)');
        cy.get(BANNER).should('have.css', 'color', 'rgb(255, 255, 255)');
        cy.get(`${BANNER} a`).should('have.css', 'color', 'rgb(255, 255, 255)');
    });

    it('NEVER: the banner has no "Hide this banner" button', () => {
        save({bannerDismissPolicy: 'NEVER'});
        until(readBanner, {dismissPolicy: 'NEVER'}, 'NEVER policy');

        cy.login();
        openAppPage();
        cy.get(BANNER, {timeout: 30000}).should('be.visible');
        cy.get(BANNER_DISMISS).should('not.exist');
    });

    it('HOURS: hidden after a reload, back once the stored dismissal is older than the duration', () => {
        save({bannerDismissPolicy: 'HOURS', bannerDismissHours: 1});
        until(readBanner, {dismissPolicy: 'HOURS', dismissHours: 1}, 'HOURS policy').then(banner => {
            expect(banner.username).to.equal('root');
            cy.login();
            openAppPage();
            cy.get(BANNER_DISMISS, {timeout: 30000}).click();
            cy.get(BANNER).should('not.exist');
            cy.window().then(win => {
                const {record} = localRecord(win);
                expect(record.keys).to.deep.equal(banner.dismissalKeys);
                expect(record.at).to.be.a('number');
            });
        });

        reloadApp().then(answer => expect(answer.data?.securityUpdates?.banner?.visible).to.equal(true));
        cy.get(BANNER).should('not.exist');

        cy.window().then(win => {
            const {key, record} = localRecord(win);
            win.localStorage.setItem(key, JSON.stringify({...record, at: record.at - (2 * 3600 * 1000)}));
        });
        reloadApp();
        cy.get(BANNER, {timeout: 30000}).should('be.visible');
    });

    it('UNTIL_CHANGE: stays hidden while advisories only disappear, back when a new advisory is counted', () => {
        save({bannerDismissPolicy: 'UNTIL_CHANGE', bannerIncludeOwnerReports: false});
        until(readBanner, {dismissPolicy: 'UNTIL_CHANGE', dismissalKeys: [KEY_CORE, KEY_MODULE]}, 'two advisories');

        cy.login();
        openAppPage();
        cy.get(BANNER_DISMISS, {timeout: 30000}).click();
        cy.get(BANNER).should('not.exist');
        reloadApp();
        cy.get(BANNER).should('not.exist');
        cy.clearAllSessionStorage();
        reloadApp().then(answer => expect(answer.data?.securityUpdates?.banner?.visible, 'still visible server-side').to.equal(true));
        cy.get(BANNER).should('not.exist');

        // A key disappears (critical only): still hidden.
        save({bannerDismissPolicy: 'UNTIL_CHANGE', bannerIncludeOwnerReports: false, bannerMinimumSeverity: 'CRITICAL'});
        until(readBanner, {dismissalKeys: [KEY_CORE]}, 'one advisory');
        reloadApp().then(answer => expect(answer.data?.securityUpdates?.banner?.dismissalKeys).to.deep.equal([KEY_CORE]));
        cy.get(BANNER).should('not.exist');

        // A new advisory for a component already listed (the owner report of security-updates): back.
        save({bannerDismissPolicy: 'UNTIL_CHANGE', bannerIncludeOwnerReports: true});
        until(readBanner, {dismissalKeys: [KEY_CORE, KEY_OWNER, KEY_MODULE]}, 'three advisories');
        reloadApp();
        cy.get(BANNER, {timeout: 30000}).should('be.visible').and('have.attr', 'data-count', '2');
    });

    it('Reset to defaults asks for confirmation: Cancel keeps the settings, confirming restores the defaults', () => {
        save({refreshIntervalHours: 12});
        until(readSettings, {refreshIntervalHours: 12}, 'saved settings');

        cy.login();
        cy.visit(ADMIN_PATH);
        cy.get(field('refreshIntervalHours')).should('have.value', '12');
        cy.contains('button', 'Reset to defaults').click();
        cy.get('[role="dialog"]').should('be.visible')
            .and('contain', 'Reset every setting to its default value?')
            .within(() => cy.contains('button', 'Cancel').click());
        cy.get('[role="dialog"]').should('not.exist');
        readSettings().then(settings => expect(settings.refreshIntervalHours, 'Cancel changes nothing').to.equal(12));

        cy.contains('button', 'Reset to defaults').click();
        cy.get('[role="dialog"]').within(() => cy.contains('button', 'Reset to defaults').click());
        cy.get(STATUS).should('contain', 'Default settings restored. The next check runs in about one minute.');
        cy.get(field('defaultStoreUrl')).should('have.value', 'https://store.jahia.com');
        cy.get(field('refreshIntervalHours')).should('have.value', '24');
        readSettings().then(settings => expect(settings).to.deep.equal(DEFAULT_SETTINGS));
    });
});
