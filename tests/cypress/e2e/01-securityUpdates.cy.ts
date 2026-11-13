import { DocumentNode } from 'graphql'

/**
 * Functional checks of the security updates module on a running Jahia.
 *
 * The store security feed (modules-repository.securityUpdates.json) may not be published by the store the
 * test Jahia reaches (store.jahia.com answers 404 until it is deployed), and the container may have no
 * internet access. The specs therefore assert the invariants that hold in every case: the report has a
 * known status, and an unavailable feed is never shown as "no known security update".
 */
describe('Security updates', () => {
    const ADMIN_PATH = '/jahia/administration/securityUpdates'
    const STATUSES = ['NOT_CHECKED', 'DISABLED', 'OK', 'PARTIAL', 'UNAVAILABLE']

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getReport: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesReport.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const refresh: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/refreshSecurityUpdates.graphql')

    before(() => {
        cy.apolloClient()
        // Run one check so that the following specs see a checked report (rate limited server side).
        cy.apollo({ mutation: refresh })
    })

    it('exposes the report under the securityUpdates namespace', () => {
        cy.apollo({ query: getReport }).then((result: never) => {
            const report = (result as { data: { securityUpdates: { report: Record<string, unknown> } } }).data
                .securityUpdates.report
            expect(STATUSES).to.include(report.status)
            expect(report.status, 'a check has run').to.not.equal('NOT_CHECKED')
            expect(report.jahiaVersion).to.match(/^8\.\d+\.\d+\.\d+$/)
            expect(report.checkedAt, 'ISO-8601 check time').to.match(/^\d{4}-\d{2}-\d{2}T/)
            expect(report.errors).to.be.an('array')
            expect(report.findings).to.be.an('array')
        })
    })

    it('never reports OK when a store feed is missing or not understood', () => {
        cy.apollo({ query: getReport }).then((result: never) => {
            const report = (result as {
                data: { securityUpdates: { report: { status: string; errors: Array<{ kind: string }> } } }
            }).data.securityUpdates.report
            const feedErrors = report.errors.filter(e =>
                ['NOT_PUBLISHED', 'UNAVAILABLE', 'NOT_UNDERSTOOD', 'INVALID_URL', 'NO_STORE'].includes(e.kind)
            )
            if (feedErrors.length > 0) {
                expect(report.status).to.be.oneOf(['PARTIAL', 'UNAVAILABLE'])
            }
        })
    })

    it('the refresh mutation returns a report', () => {
        cy.apollo({ mutation: refresh }).then((result: never) => {
            const report = (result as { data: { securityUpdates: { refresh: { status: string } } } }).data
                .securityUpdates.refresh
            expect(STATUSES).to.include(report.status)
        })
    })

    it('renders the admin page with its controls and an honest status', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.get('h2').contains('Security updates').should('be.visible')
        cy.contains('button', 'Check now').should('be.visible')
        cy.get('[role="status"]').should('exist')
        cy.contains('a', 'Open Manage modules').should('have.attr', 'href').and('contain', '/jahia/administration/manageModules')

        cy.apollo({ query: getReport }).then((result: never) => {
            const report = (result as { data: { securityUpdates: { report: { status: string; findings: unknown[] } } } })
                .data.securityUpdates.report
            if (report.status === 'UNAVAILABLE') {
                cy.contains('Security feed unavailable').should('be.visible')
                cy.contains('No known security update for this Jahia').should('not.exist')
            }
            if (report.status === 'OK' && report.findings.length === 0) {
                cy.contains('No known security update for this Jahia').should('be.visible')
            }
            if (report.findings.length > 0) {
                cy.get('table caption').should('contain', 'Components with security updates')
                cy.get('table th[scope="col"]').should('have.length', 7)
            }
        })
    })

    it('the Check now button runs a check and announces it', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.contains('button', 'Check now').click()
        cy.get('[role="status"]').should('contain', 'Check completed')
    })
})
