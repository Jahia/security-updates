import { DocumentNode } from 'graphql'
import { createUser, deleteUser } from '@jahia/cypress'

/**
 * The settings section of /jahia/administration/securityUpdates and the settings GraphQL API
 * (Query.securityUpdates.settings, Mutation.securityUpdates.updateSettings), with the banner colours and dismiss
 * policies they drive.
 *
 * Fixture store (tests/storefixture): three banner rows by default (jahia-core CRITICAL, security-updates HIGH
 * advisory, security-updates HIGH owner report). Every write here also keeps the module on the fixture store, so
 * that the check scheduled after each configuration change reads the same data.
 */
type Settings = {
    enabled: boolean
    refreshIntervalHours: number
    useForgeSettings: boolean
    defaultStoreUrl: string
    extraFeedUrls: string
    bannerEnabled: boolean
    bannerMinimumSeverity: string
    bannerIncludeOwnerReports: boolean
    bannerBackgroundColor: string
    bannerTextColor: string
    bannerDismissPolicy: string
    bannerDismissHours: number
    contrastRatio?: number
}
type Banner = {
    visible: boolean
    count: number
    components: string[]
    backgroundColor: string
    textColor: string
    dismissPolicy: string
    username: string
}
type Suggestion = { field: string; value: string; ratio: string }
type FieldError = { field: string | null; code: string; message: string; suggestions: Suggestion[] }
type UpdateResult = { ok: boolean; errors: FieldError[] }
type Report = { findings: Array<{ component: string; source: string }> }

describe('Security updates settings', () => {
    const ADMIN_PATH = '/jahia/administration/securityUpdates'
    const APP_PATH = '/jahia/dashboard'
    const BANNER = '#security-updates-banner'
    const DISMISS = '[data-sel-role="security-updates-banner-dismiss"]'
    const DENIED_USER = 'securityUpdatesSettingsUser'
    const PASSWORD = 'SecUpdSettings9-Test'
    const LOCAL_KEY = 'security-updates-banner-dismissed:root'
    const field = (name: string) => `#security-updates-setting-${name}`

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesSettings.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const updateSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/updateSecurityUpdatesSettings.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getBanner: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesBanner.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getReport: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesReport.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const refresh: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/refreshSecurityUpdates.graphql')

    const FIXTURE_DEFAULTS: Settings = {
        enabled: true,
        refreshIntervalHours: 24,
        useForgeSettings: false,
        defaultStoreUrl: 'http://storefixture',
        extraFeedUrls: '',
        bannerEnabled: true,
        bannerMinimumSeverity: 'HIGH',
        bannerIncludeOwnerReports: true,
        bannerBackgroundColor: '#1B1B1B',
        bannerTextColor: '#FFFFFF',
        bannerDismissPolicy: 'SESSION',
        bannerDismissHours: 24,
    }

    const provision = (fileName: string) =>
        cy.runProvisioningScript({ script: { fileName: `security-updates/${fileName}`, type: 'application/yaml' } })

    const readSettings = () =>
        cy
            .apollo({ query: getSettings, fetchPolicy: 'no-cache' })
            .then((result: never) => {
                // Apollo adds __typename to every object; the comparisons are about the settings only.
                const { __typename, ...settings } = (
                    result as { data: { securityUpdates: { settings: Settings & { __typename?: string } } } }
                ).data.securityUpdates.settings
                return settings as Settings
            })

    const readBanner = () =>
        cy
            .apollo({ query: getBanner, fetchPolicy: 'no-cache' })
            .then(
                (result: never) =>
                    (result as { data: { securityUpdates: { banner: Banner } } }).data.securityUpdates.banner,
            )

    /** The full input of updateSettings: the fixture defaults with some values changed. */
    const inputWith = (overrides: Partial<Settings>) => {
        const input: Settings = { ...FIXTURE_DEFAULTS, ...overrides }
        delete input.contrastRatio
        return input
    }

    const save = (overrides: Partial<Settings>) =>
        cy
            .apollo({ mutation: updateSettings, variables: { input: inputWith(overrides) } })
            .then(
                (result: never) =>
                    (result as { data: { securityUpdates: { updateSettings: UpdateResult } } }).data.securityUpdates
                        .updateSettings,
            )

    /** A configuration change reaches the service asynchronously: poll until the expected values are in effect. */
    const untilMatches = <T>(
        read: () => Cypress.Chainable<T>,
        expected: Partial<T>,
        attempt = 0,
    ): Cypress.Chainable<T> =>
        read().then((value) => {
            const matches = Object.entries(expected).every(
                ([key, v]) => JSON.stringify(value[key as keyof T]) === JSON.stringify(v),
            )
            if (matches || attempt >= 15) {
                return cy.wrap(value)
            }
            // eslint-disable-next-line cypress/no-unnecessary-waiting
            cy.wait(1000)
            return untilMatches(read, expected, attempt + 1)
        })

    /** On-demand checks are rate limited to one per 30 s: retry until the three fixture rows are there. */
    const refreshUntilFixtureRead = (attempt = 0): Cypress.Chainable<Report> => {
        cy.apollo({ mutation: refresh })
        return cy
            .apollo({ query: getReport, fetchPolicy: 'no-cache' })
            .then(
                (result: never) =>
                    (result as { data: { securityUpdates: { report: Report } } }).data.securityUpdates.report,
            )
            .then((report) => {
                const fromFixture =
                    report.findings.some((f) => f.component === 'jahia-core') &&
                    report.findings.some((f) => f.source === 'OWNER_REPORTED')
                if (fromFixture || attempt >= 5) {
                    return cy.wrap(report)
                }
                // eslint-disable-next-line cypress/no-unnecessary-waiting
                cy.wait(31000)
                return refreshUntilFixtureRead(attempt + 1)
            })
    }

    /** Opens an app shell page and waits for the banner callback to have asked the server. */
    const openApp = () => {
        cy.intercept('POST', '**/modules/graphql', (req) => {
            if (typeof req.body?.query === 'string' && req.body.query.includes('SecurityUpdatesBanner {')) {
                req.alias = 'bannerQuery'
            }
        })
        cy.visit(APP_PATH)
        cy.wait('@bannerQuery', { timeout: 60000 })
    }

    const reloadApp = () => {
        cy.reload()
        cy.wait('@bannerQuery', { timeout: 60000 })
    }

    before(() => {
        cy.apolloClient()
        provision('settings-fixture-defaults.yml')
        untilMatches(readSettings, { useForgeSettings: false, defaultStoreUrl: 'http://storefixture' })
        refreshUntilFixtureRead().then((report) => {
            expect(report.findings, 'the three fixture rows').to.have.length(3)
        })
        cy.login()
        createUser(DENIED_USER, PASSWORD)
    })

    beforeEach(() => {
        cy.apolloClient()
        provision('settings-fixture-defaults.yml')
        untilMatches(readSettings, FIXTURE_DEFAULTS)
    })

    after(() => {
        cy.apolloClient()
        cy.login()
        deleteUser(DENIED_USER)
        provision('restore-defaults.yml')
    })

    it('the form shows the current values', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.contains('h2', 'Settings').should('be.visible')
        cy.get(field('enabled')).should('be.checked')
        cy.get(field('refreshIntervalHours')).should('have.value', '24')
        cy.get(field('useForgeSettings')).should('not.be.checked')
        cy.get(field('defaultStoreUrl')).should('have.value', 'http://storefixture')
        cy.get(field('extraFeedUrls')).should('have.value', '')
        cy.get(field('bannerEnabled')).should('be.checked')
        cy.get(field('bannerMinimumSeverity')).should('have.value', 'HIGH')
        cy.get(field('bannerIncludeOwnerReports')).should('be.checked')
        cy.get(field('bannerBackgroundColor')).should('have.value', '#1B1B1B')
        cy.get(field('bannerTextColor')).should('have.value', '#FFFFFF')
        cy.get(field('bannerDismissPolicy')).should('have.value', 'SESSION')
        cy.get(field('bannerDismissHours')).should('have.value', '24').and('be.disabled')
        cy.get('[data-testid="security-updates-contrast"]').should('contain', '17.22:1')
    })

    it('saving from the form changes the settings and the banner', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.get(field('refreshIntervalHours')).clear().type('12')
        cy.get(field('bannerMinimumSeverity')).select('CRITICAL')
        cy.contains('button', 'Save settings').click()
        cy.get('[data-testid="security-updates-settings-status"]').should('contain', 'Settings saved.')

        untilMatches(readSettings, { refreshIntervalHours: 12, bannerMinimumSeverity: 'CRITICAL' }).then((settings) => {
            expect(settings.refreshIntervalHours).to.equal(12)
            expect(settings.bannerMinimumSeverity).to.equal('CRITICAL')
            expect(settings.defaultStoreUrl, 'other values are kept').to.equal('http://storefixture')
        })
        untilMatches(readBanner, { count: 1 }).then((banner) => {
            expect(banner.components).to.deep.equal(['jahia-core'])
        })
    })

    it('invalid input in the form shows field errors and changes nothing', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.get(field('refreshIntervalHours')).clear().type('0')
        cy.get(field('defaultStoreUrl')).clear().type('ftp://store.example.org')
        cy.get(field('bannerBackgroundColor')).clear().type('#888888')
        cy.get(field('bannerTextColor')).clear().type('#777777')
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Below the 7:1 minimum')
        cy.contains('button', 'Save settings').click()

        cy.get('[role="alert"]').should('contain', 'nothing was saved')
        ;['refreshIntervalHours', 'defaultStoreUrl', 'bannerBackgroundColor', 'bannerTextColor'].forEach((name) => {
            cy.get(field(name)).should('have.attr', 'aria-invalid', 'true')
            // The form is taller than the admin viewport: scroll as a user would before checking visibility.
            cy.get(`${field(name)}-error`).scrollIntoView().should('be.visible')
        })
        cy.get(`${field('bannerTextColor')}-error`).should('contain', '1.26:1')
        cy.focused().should('have.id', 'security-updates-setting-refreshIntervalHours')

        readSettings().then((settings) => {
            expect({ ...settings, contrastRatio: undefined }).to.deep.equal({
                ...FIXTURE_DEFAULTS,
                contrastRatio: undefined,
            })
        })
    })

    it('invalid input through GraphQL is refused with field errors and changes nothing', () => {
        const invalid = { bannerBackgroundColor: '#12345', refreshIntervalHours: 0, extraFeedUrls: 'not a url' }
        save(invalid).then((result) => {
            expect(result.ok).to.equal(false)
            expect(result.errors.map((e) => `${e.field}:${e.code}`).sort()).to.deep.equal([
                'bannerBackgroundColor:INVALID_COLOR',
                'extraFeedUrls:INVALID_URL',
                'refreshIntervalHours:OUT_OF_RANGE',
            ])
        })
        save({ bannerBackgroundColor: '#777777', bannerTextColor: '#888888' }).then((result) => {
            expect(result.ok).to.equal(false)
            expect(result.errors.map((e) => e.code)).to.deep.equal(['LOW_CONTRAST', 'LOW_CONTRAST'])
        })
        readSettings().then((settings) => {
            expect(settings.bannerBackgroundColor).to.equal('#1B1B1B')
            expect(settings.refreshIntervalHours).to.equal(24)
        })
    })

    it('a low contrast suggests colours: through GraphQL, and in the form where one click applies them', () => {
        save({ bannerBackgroundColor: '#1B1B1B', bannerTextColor: '#333333' }).then((result) => {
            expect(result.ok).to.equal(false)
            const error = result.errors.find((e) => e.field === 'bannerTextColor') as FieldError
            expect(error.code).to.equal('LOW_CONTRAST')
            expect(error.suggestions.map((s) => `${s.field}=${s.value}@${s.ratio}`)).to.deep.equal([
                'bannerTextColor=#A6A6A6@7.07',
                'bannerBackgroundColor=#C1C1C1@7.01',
            ])
            expect(error.message).to.contain('Suggested: text #A6A6A6 (7.07:1) or background #C1C1C1 (7.01:1).')
        })

        cy.login()
        cy.visit(ADMIN_PATH)
        cy.get(field('bannerTextColor')).clear().type('#333333')
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Below the 7:1 minimum')
        cy.get('[data-testid="security-updates-suggestion-text"]').should('contain', '#A6A6A6').and('contain', '7.07:1')
        cy.get('[data-testid="security-updates-suggestion-background"]').should('contain', '#C1C1C1')
        cy.contains('button', 'Use this text colour #A6A6A6').click()
        cy.get(field('bannerTextColor')).should('have.value', '#A6A6A6')
        cy.get('[data-testid="security-updates-contrast"]').should('contain', 'Meets the 7:1 minimum')
        cy.get('[data-testid="security-updates-settings-status"]').should('contain', 'Text colour #A6A6A6 applied')
        cy.contains('button', 'Save settings').click()
        cy.get('[data-testid="security-updates-settings-status"]').should('contain', 'Settings saved.')

        untilMatches(readBanner, { backgroundColor: '#1B1B1B', textColor: '#A6A6A6' })
        openApp()
        cy.get(BANNER, { timeout: 30000 }).should('have.css', 'background-color', 'rgb(27, 27, 27)')
        cy.get(BANNER).should('have.css', 'color', 'rgb(166, 166, 166)')
    })

    it('the banner uses the configured colours', () => {
        save({ bannerBackgroundColor: '#0B3D91', bannerTextColor: '#FFFFFF' }).then((result) => {
            expect(result.ok, JSON.stringify(result.errors)).to.equal(true)
        })
        untilMatches(readBanner, { backgroundColor: '#0B3D91', textColor: '#FFFFFF' })

        cy.login()
        openApp()
        cy.get(BANNER, { timeout: 30000 }).should('have.css', 'background-color', 'rgb(11, 61, 145)')
        cy.get(BANNER).should('have.css', 'color', 'rgb(255, 255, 255)')
        cy.get(`${BANNER} a`).should('have.css', 'color', 'rgb(255, 255, 255)')
    })

    it('NEVER: no Dismiss button', () => {
        save({ bannerDismissPolicy: 'NEVER' }).then((result) => expect(result.ok).to.equal(true))
        untilMatches(readBanner, { dismissPolicy: 'NEVER' })

        cy.login()
        openApp()
        cy.get(BANNER, { timeout: 30000 }).should('be.visible')
        cy.get(DISMISS).should('not.exist')
    })

    it('HOURS: hidden after a reload, back once the stored dismissal time is older than the duration', () => {
        save({ bannerDismissPolicy: 'HOURS', bannerDismissHours: 1 }).then((result) => expect(result.ok).to.equal(true))
        untilMatches(readBanner, { dismissPolicy: 'HOURS' }).then((banner) => {
            expect(banner.username).to.equal('root')
        })

        cy.login()
        openApp()
        cy.get(DISMISS, { timeout: 30000 }).click()
        cy.get(BANNER).should('not.exist')
        cy.window().then((win) => {
            const record = JSON.parse(win.localStorage.getItem(LOCAL_KEY) as string)
            expect(record.key).to.equal('jahia-core|security-updates|security-updates')
            expect(record.at).to.be.a('number')
        })

        reloadApp()
        cy.get(BANNER).should('not.exist')

        cy.window().then((win) => {
            const record = JSON.parse(win.localStorage.getItem(LOCAL_KEY) as string)
            record.at -= 2 * 3600 * 1000
            win.localStorage.setItem(LOCAL_KEY, JSON.stringify(record))
        })
        reloadApp()
        cy.get(BANNER, { timeout: 30000 }).should('be.visible')
    })

    it('UNTIL_CHANGE: hidden after a reload and in a new session, back when the components change', () => {
        save({ bannerDismissPolicy: 'UNTIL_CHANGE' }).then((result) => expect(result.ok).to.equal(true))
        untilMatches(readBanner, { dismissPolicy: 'UNTIL_CHANGE', count: 3 })

        cy.login()
        openApp()
        cy.get(DISMISS, { timeout: 30000 }).click()
        cy.get(BANNER).should('not.exist')

        reloadApp()
        cy.get(BANNER).should('not.exist')

        cy.clearAllSessionStorage()
        reloadApp()
        cy.get(BANNER).should('not.exist')

        save({ bannerDismissPolicy: 'UNTIL_CHANGE', bannerIncludeOwnerReports: false }).then((result) =>
            expect(result.ok).to.equal(true),
        )
        untilMatches(readBanner, { count: 2 })
        reloadApp()
        cy.get(BANNER, { timeout: 30000 }).should('be.visible').and('have.attr', 'data-count', '2')
    })

    it('a user without adminTemplates can neither read nor update the settings, nor see the page', () => {
        const errorsOf = (result: never) => {
            const r = result as { graphQLErrors?: Array<{ message: string }>; errors?: Array<{ message: string }> }
            return r.graphQLErrors ?? r.errors ?? []
        }
        cy.apolloClient({ username: DENIED_USER, password: PASSWORD })
        cy.apollo({ query: getSettings, errorPolicy: 'all' }).then((result: never) => {
            expect(
                errorsOf(result)
                    .map((e) => e.message)
                    .join(' '),
            ).to.contain('Permission denied')
        })
        cy.apollo({
            mutation: updateSettings,
            variables: { input: inputWith({ refreshIntervalHours: 1 }) },
            errorPolicy: 'all',
        }).then((result: never) => {
            expect(
                errorsOf(result)
                    .map((e) => e.message)
                    .join(' '),
            ).to.contain('Permission denied')
        })

        cy.apolloClient()
        readSettings().then((settings) => expect(settings.refreshIntervalHours, 'unchanged').to.equal(24))

        cy.login(DENIED_USER, PASSWORD)
        cy.visit(ADMIN_PATH, { failOnStatusCode: false })
        cy.contains('button', 'Save settings').should('not.exist')
        cy.get(field('refreshIntervalHours')).should('not.exist')
    })
})
