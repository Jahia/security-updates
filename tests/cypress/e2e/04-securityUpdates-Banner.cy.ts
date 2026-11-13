import { DocumentNode } from 'graphql'
import { createUser, deleteUser } from '@jahia/cypress'

/**
 * The notice shown in the Jahia UI, decided server-side (Query.securityUpdates.banner) from the report and the
 * settings bannerEnabled, bannerMinimumSeverity and bannerIncludeOwnerReports.
 *
 * With the fixture store (tests/storefixture) the report has three rows:
 *   jahia-core ADVISORY CRITICAL, security-updates ADVISORY HIGH, security-updates OWNER_REPORTED HIGH.
 *
 * The banner is rendered by a jahiaApp-init callback, which runs on every page of the Jahia app shell
 * (/jahia/...). Every provisioning fixture also points the module at the fixture store, so that the check
 * scheduled after each configuration change reads the same data.
 */
type Banner = { visible: boolean; count: number; minimumSeverity: string; components: string[] }
type Report = { findings: Array<{ component: string; source: string }> }

describe('Security updates banner', () => {
    const APP_PATH = '/jahia/dashboard'
    const BANNER = '#security-updates-banner'
    const DISMISS = '[data-sel-role="security-updates-banner-dismiss"]'
    const LINK = '[data-sel-role="security-updates-banner-link"]'
    const DENIED_USER = 'securityUpdatesBannerUser'
    const PASSWORD = 'SecUpdBanner9-Test'

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getBanner: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesBanner.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getReport: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesReport.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const refresh: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/refreshSecurityUpdates.graphql')

    const provision = (fileName: string) =>
        cy.runProvisioningScript({ script: { fileName: `security-updates/${fileName}`, type: 'application/yaml' } })

    const readBanner = () =>
        cy
            .apollo({ query: getBanner, fetchPolicy: 'no-cache' })
            .then((result: never) => (result as { data: { securityUpdates: { banner: Banner } } }).data.securityUpdates.banner)

    const readReport = () =>
        cy
            .apollo({ query: getReport, fetchPolicy: 'no-cache' })
            .then((result: never) => (result as { data: { securityUpdates: { report: Report } } }).data.securityUpdates.report)

    /** On-demand checks are rate limited to one per 30 s: retry until the three fixture rows are there. */
    const refreshUntilFixtureRead = (attempt = 0): Cypress.Chainable<Report> => {
        cy.apollo({ mutation: refresh })
        return readReport().then(report => {
            const fromFixture =
                report.findings.some(f => f.component === 'jahia-core') &&
                report.findings.some(f => f.source === 'OWNER_REPORTED')
            if (fromFixture || attempt >= 5) {
                return cy.wrap(report)
            }
            // eslint-disable-next-line cypress/no-unnecessary-waiting
            cy.wait(31000)
            return refreshUntilFixtureRead(attempt + 1)
        })
    }

    /** A configuration change reaches the service asynchronously: poll the banner field until it matches. */
    const bannerUntil = (expected: Partial<Banner>, attempt = 0): Cypress.Chainable<Banner> =>
        readBanner().then(banner => {
            const matches = Object.entries(expected).every(
                ([key, value]) => JSON.stringify(banner[key as keyof Banner]) === JSON.stringify(value)
            )
            if (matches || attempt >= 15) {
                return cy.wrap(banner)
            }
            // eslint-disable-next-line cypress/no-unnecessary-waiting
            cy.wait(1000)
            return bannerUntil(expected, attempt + 1)
        })

    /** Opens an app shell page and waits for the banner callback to have asked the server. */
    const openAppAndWaitForBannerQuery = () => {
        cy.intercept('POST', '**/modules/graphql', req => {
            if (typeof req.body?.query === 'string' && req.body.query.includes('SecurityUpdatesBanner {')) {
                req.alias = 'bannerQuery'
            }
        })
        cy.visit(APP_PATH)
        cy.wait('@bannerQuery', { timeout: 60000 })
    }

    before(() => {
        cy.apolloClient()
        provision('banner-defaults.yml')
        refreshUntilFixtureRead().then(report => {
            expect(report.findings, 'the three fixture rows').to.have.length(3)
        })
        cy.login()
        createUser(DENIED_USER, PASSWORD)
    })

    after(() => {
        cy.apolloClient()
        cy.login()
        deleteUser(DENIED_USER)
        provision('restore-defaults.yml')
    })

    beforeEach(() => {
        cy.apolloClient()
    })

    it('defaults: visible, counts the three rows, links to the page', () => {
        provision('banner-defaults.yml')
        bannerUntil({ visible: true, count: 3 }).then(banner => {
            expect(banner.visible).to.equal(true)
            expect(banner.count).to.equal(3)
            expect(banner.minimumSeverity).to.equal('HIGH')
            expect(banner.components).to.deep.equal(['jahia-core', 'security-updates', 'security-updates'])
        })

        cy.login()
        openAppAndWaitForBannerQuery()
        cy.get(BANNER, { timeout: 30000 }).should('be.visible').and('have.attr', 'data-count', '3')
        cy.get(BANNER).should('contain', 'high severity or above').and('contain', '3')
        cy.get(BANNER).should('not.contain', 'Jahia advisories')
        cy.get(LINK).should('have.attr', 'href').and('match', /\/jahia\/administration\/securityUpdates$/)
    })

    it('bannerIncludeOwnerReports=false: counts the two Jahia advisories', () => {
        provision('banner-advisories-only.yml')
        bannerUntil({ count: 2 }).then(banner => {
            expect(banner.visible).to.equal(true)
            expect(banner.count).to.equal(2)
            expect(banner.components).to.deep.equal(['jahia-core', 'security-updates'])
        })

        cy.login()
        openAppAndWaitForBannerQuery()
        cy.get(BANNER, { timeout: 30000 }).should('have.attr', 'data-count', '2')
    })

    it('bannerMinimumSeverity=CRITICAL: counts the critical row only', () => {
        provision('banner-critical-only.yml')
        bannerUntil({ minimumSeverity: 'CRITICAL', count: 1 }).then(banner => {
            expect(banner.visible).to.equal(true)
            expect(banner.count).to.equal(1)
            expect(banner.components).to.deep.equal(['jahia-core'])
        })

        cy.login()
        openAppAndWaitForBannerQuery()
        cy.get(BANNER, { timeout: 30000 }).should('have.attr', 'data-count', '1').and('contain', 'critical severity')
    })

    it('bannerEnabled=false: no banner', () => {
        provision('banner-disabled.yml')
        bannerUntil({ visible: false }).then(banner => {
            expect(banner.visible).to.equal(false)
        })

        cy.login()
        openAppAndWaitForBannerQuery()
        cy.get(BANNER).should('not.exist')
    })

    it('dismiss: hidden after Dismiss and after a reload in the same session, back in a new session', () => {
        provision('banner-defaults.yml')
        bannerUntil({ visible: true, count: 3 })

        cy.login()
        openAppAndWaitForBannerQuery()
        cy.get(DISMISS, { timeout: 30000 }).click()
        cy.get(BANNER).should('not.exist')

        cy.reload()
        cy.wait('@bannerQuery', { timeout: 60000 })
        cy.get(BANNER).should('not.exist')

        cy.clearAllSessionStorage()
        cy.reload()
        cy.wait('@bannerQuery', { timeout: 60000 })
        cy.get(BANNER, { timeout: 30000 }).should('be.visible')
    })

    it('a user without adminTemplates never gets the banner', () => {
        provision('banner-defaults.yml')
        bannerUntil({ visible: true })

        cy.apolloClient({ username: DENIED_USER, password: PASSWORD })
        cy.apollo({ query: getBanner, errorPolicy: 'all' }).then((result: never) => {
            const r = result as { graphQLErrors?: Array<{ message: string }>; errors?: Array<{ message: string }> }
            const errors = r.graphQLErrors ?? r.errors ?? []
            expect(errors.map(e => e.message).join(' ')).to.contain('Permission denied')
        })

        let bannerQueries = 0
        cy.intercept('POST', '**/modules/graphql', req => {
            if (typeof req.body?.query === 'string' && req.body.query.includes('SecurityUpdatesBanner {')) {
                bannerQueries += 1
            }
        })
        cy.login(DENIED_USER, PASSWORD)
        cy.visit(APP_PATH, { failOnStatusCode: false })
        // eslint-disable-next-line cypress/no-unnecessary-waiting
        cy.wait(10000)
        cy.get(BANNER).should('not.exist')
        cy.then(() => expect(bannerQueries, 'the banner query is not even sent').to.equal(0))
    })
})
