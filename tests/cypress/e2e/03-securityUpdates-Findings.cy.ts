import { DocumentNode } from 'graphql'

/**
 * Findings computed from a store that serves known data (the `storefixture` nginx service of
 * docker-compose.yml, files in tests/storefixture). It publishes:
 *   - JSA-2099-0001: jahia-core fixed in 99.0.0.0 only           -> affected, Jahia upgrade required
 *   - JSA-2099-0002: security-updates fixed in 99.0.0            -> affected, 99.0.0 is compatible
 *   - JSA-2099-0003: security-updates fixed in 0.1.0             -> not affected (installed is newer)
 *   - JSA-2099-0004: a module that is not installed              -> no row
 *   - an owner flag on security-updates 1.0.1 (same major)       -> affected, 1.0.1 requires Jahia 8.99
 * The module under test is installed as 1.0.0-SNAPSHOT on a Jahia 8.2.
 */
type Finding = {
    component: string
    groupId: string | null
    installedVersion: string
    recommendedVersion: string
    compatibility: string
    requiredJahiaVersion: string | null
    severity: string
    source: string
    advisories: Array<{ id: string; title: string; url: string | null; cves: string[]; severity: string }>
}
type Report = { status: string; errors: Array<{ storeUrl: string; kind: string }>; findings: Finding[] }

describe('Security updates findings from a known store', () => {
    const ADMIN_PATH = '/jahia/administration/securityUpdates'

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getReport: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesReport.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const refresh: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/refreshSecurityUpdates.graphql')

    const readReport = () =>
        cy
            .apollo({ query: getReport, fetchPolicy: 'no-cache' })
            .then((result: never) => (result as { data: { securityUpdates: { report: Report } } }).data.securityUpdates.report)

    /** On-demand checks are rate limited to one per 30 s: retry until the fixture store has been read. */
    const refreshUntilFixtureRead = (attempt = 0): Cypress.Chainable<Report> => {
        cy.apollo({ mutation: refresh })
        return readReport().then(report => {
            const fromFixture = report.findings.some(f => f.component === 'jahia-core')
            if (fromFixture || attempt >= 5) {
                return cy.wrap(report)
            }
            // eslint-disable-next-line cypress/no-unnecessary-waiting
            cy.wait(31000)
            return refreshUntilFixtureRead(attempt + 1)
        })
    }

    const row = (report: Report, component: string, source: string) =>
        report.findings.find(f => f.component === component && f.source === source)

    before(() => {
        cy.apolloClient()
        cy.runProvisioningScript({ script: { fileName: 'security-updates/use-fixture-store.yml', type: 'application/yaml' } })
    })

    after(() => {
        cy.runProvisioningScript({ script: { fileName: 'security-updates/use-default-store.yml', type: 'application/yaml' } })
    })

    it('reads the fixture store without error', () => {
        refreshUntilFixtureRead().then(report => {
            expect(report.errors, 'store errors').to.deep.equal([])
            expect(report.status).to.equal('OK')
        })
    })

    it('reports the platform advisory as a Jahia upgrade', () => {
        readReport().then(report => {
            const core = row(report, 'jahia-core', 'ADVISORY')
            expect(core, 'jahia-core row').to.not.equal(undefined)
            expect(core!.installedVersion).to.match(/^8\.\d+\.\d+\.\d+$/)
            expect(core!.recommendedVersion).to.equal('99.0.0.0')
            expect(core!.compatibility).to.equal('JAHIA_UPGRADE')
            expect(core!.severity).to.equal('CRITICAL')
            expect(core!.advisories.map(a => a.id)).to.deep.equal(['JSA-2099-0001'])
            expect(core!.advisories[0].cves).to.deep.equal(['CVE-2099-0001'])
            expect(core!.advisories[0].url).to.equal('https://academy.jahia.com/fixture-jsa-2099-0001')
        })
    })

    it('reports the module advisory with a compatible fixed version, and skips the old one', () => {
        readReport().then(report => {
            const module = row(report, 'security-updates', 'ADVISORY')
            expect(module, 'security-updates advisory row').to.not.equal(undefined)
            expect(module!.recommendedVersion).to.equal('99.0.0')
            expect(module!.compatibility).to.equal('COMPATIBLE')
            expect(module!.severity).to.equal('HIGH')
            expect(module!.advisories.map(a => a.id), 'JSA-2099-0003 is fixed in an older version').to.deep.equal([
                'JSA-2099-0002'
            ])
        })
    })

    it('reports the owner flag separately, with its Jahia requirement', () => {
        readReport().then(report => {
            const owner = row(report, 'security-updates', 'OWNER_REPORTED')
            expect(owner, 'owner-reported row').to.not.equal(undefined)
            expect(owner!.recommendedVersion).to.equal('1.0.1')
            expect(owner!.compatibility).to.equal('INCOMPATIBLE')
            expect(owner!.requiredJahiaVersion).to.equal('8.99.0.0')
            expect(owner!.severity).to.equal('MEDIUM')
        })
    })

    it('lists no row for a module that is not installed', () => {
        readReport().then(report => {
            expect(report.findings.map(f => f.component)).to.not.include('not-installed-module')
            expect(report.findings, 'exactly the three expected rows').to.have.length(3)
        })
    })

    it('shows Jahia advisories by default, and owner reports labelled as such under "All"', () => {
        cy.login()
        cy.visit(ADMIN_PATH)
        cy.get('table caption').should('contain', 'Components with security updates')
        cy.get('table tbody tr').should('have.length', 2)
        cy.get('table tbody').should('contain', 'jahia-core').and('contain', '99.0.0.0').and('contain', 'JSA-2099-0001')
        cy.get('table tbody').should('not.contain', 'Reported by the module owner')
        cy.get('table tbody a[href="https://academy.jahia.com/fixture-jsa-2099-0001"]')
            .should('have.attr', 'rel')
            .and('contain', 'noopener')

        cy.contains('label', 'All, including reports by module owners').click()
        cy.get('table tbody tr').should('have.length', 3)
        cy.get('table tbody').contains('tr', '1.0.1').should('contain', 'Reported by the module owner')
        cy.contains('not reviewed by Jahia').should('be.visible')
    })
})
