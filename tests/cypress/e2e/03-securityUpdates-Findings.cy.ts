import {
    ADMIN_PATH,
    Finding,
    Report,
    checkUntil,
    isFixtureReport,
    provision,
    readReport
} from '../utils/securityUpdates';

/**
 * Findings computed from the fixture store (https://storefixture, files in tests/storefixture/contents). It publishes:
 *   - JSA-2099-0001: jahia-core fixed in 99.0.0.0 only           -> affected, Jahia upgrade required
 *   - JSA-2099-0002: security-updates fixed in 99.0.0            -> affected, 99.0.0 is compatible
 *   - JSA-2099-0003: security-updates fixed in 0.1.0             -> not affected (installed is newer)
 *   - JSA-2099-0004: a module that is not installed              -> no row
 *   - an owner flag (high) on security-updates 1.0.1 (same major) -> affected, 1.0.1 requires Jahia 8.99
 * The module under test is installed as 1.0.0-SNAPSHOT.
 */
describe('Security updates: findings from a known store', () => {
    const REGION = '[role="region"][aria-labelledby="security-updates-findings-title"]';

    const row = (report: Report, component: string, source: string): Finding => {
        const found = report.findings.find(f => f.component === component && f.source === source);
        expect(found, `${component} ${source} row`).to.not.equal(undefined);
        return found as Finding;
    };

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

    it('reads the https fixture store without error', () => {
        readReport().then(report => {
            expect(report.errors, 'store errors').to.deep.equal([]);
            expect(report.status).to.equal('OK');
        });
    });

    it('reports the platform advisory as a Jahia upgrade', () => {
        readReport().then(report => {
            const core = row(report, 'jahia-core', 'ADVISORY');
            expect(core.installedVersion).to.match(/^8\.\d+\.\d+\.\d+$/);
            expect(core.recommendedVersion).to.equal('99.0.0.0');
            expect(core.compatibility).to.equal('JAHIA_UPGRADE');
            expect(core.severity).to.equal('CRITICAL');
            expect(core.advisories.map(a => a.id)).to.deep.equal(['JSA-2099-0001']);
            expect(core.advisories[0].cves).to.deep.equal(['CVE-2099-0001']);
            expect(core.advisories[0].url).to.equal('https://academy.jahia.com/fixture-jsa-2099-0001');
        });
    });

    it('reports the module advisory with a compatible fixed version, and skips the old one', () => {
        readReport().then(report => {
            const module = row(report, 'security-updates', 'ADVISORY');
            expect(module.recommendedVersion).to.equal('99.0.0');
            expect(module.compatibility).to.equal('COMPATIBLE');
            expect(module.severity).to.equal('HIGH');
            expect(module.advisories.map(a => a.id), 'JSA-2099-0003 is fixed in an older version').to.deep.equal([
                'JSA-2099-0002'
            ]);
        });
    });

    it('reports the owner flag separately, with its Jahia requirement', () => {
        readReport().then(report => {
            const owner = row(report, 'security-updates', 'OWNER_REPORTED');
            expect(owner.recommendedVersion).to.equal('1.0.1');
            expect(owner.compatibility).to.equal('INCOMPATIBLE');
            expect(owner.requiredJahiaVersion).to.equal('8.99.0.0');
            expect(owner.severity).to.equal('HIGH');
            expect(owner.advisories.map(a => a.id)).to.deep.equal(['GHSA-2c2c-3f3f-4g4g']);
        });
    });

    it('lists no row for a module that is not installed', () => {
        readReport().then(report => {
            expect(report.findings, 'exactly the three expected rows').to.have.length(3);
            expect(report.findings.map(f => f.component)).to.not.include('not-installed-module');
        });
    });

    it('shows every finding by default when module owners report some, with counts, severity and actions', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        cy.contains('h2', 'Components to update').should('be.visible');
        cy.contains('2 components need a security update.').should('be.visible');
        cy.contains('By highest severity: Critical: 1, High: 1.').should('be.visible');
        cy.contains('label', 'Jahia advisories only (2)').should('exist');
        cy.contains('label', 'All, including reports by module owners (3)').should('exist');
        cy.get('#security-updates-filter-all').should('be.checked');

        cy.get(`${REGION} tbody tr`).should('have.length', 3);
        cy.get(`${REGION} tbody`).contains('tr', 'jahia-core').within(() => {
            cy.contains('Critical').should('be.visible');
            cy.contains('99.0.0.0').should('be.visible');
            cy.contains('Jahia upgrade required').should('be.visible');
            cy.contains('a', 'JSA-2099-0001: Fixture platform advisory')
                .should('have.attr', 'href', 'https://academy.jahia.com/fixture-jsa-2099-0001')
                .and('have.attr', 'target', '_blank')
                .and('have.attr', 'rel')
                .and('contain', 'noopener');
            cy.contains('a', 'CVE-2099-0001 on cve.org')
                .should('have.attr', 'href', 'https://www.cve.org/CVERecord?id=CVE-2099-0001');
            cy.contains('a', 'Jahia upgrade documentation').should('have.attr', 'href').and('match', /^https:\/\//);
        });
        cy.get(`${REGION} tbody`).contains('tr', 'Reported by the module owner').within(() => {
            cy.contains('1.0.1').should('be.visible');
            cy.contains('Minimum Jahia: 8.99.0.0').should('be.visible');
            cy.contains('GHSA-2c2c-3f3f-4g4g').should('be.visible');
            cy.contains('a', 'Update security-updates in Manage modules')
                .should('have.attr', 'href')
                .and('contain', '/jahia/administration/manageModules');
        });
    });

    it('the filter shows the Jahia advisories only, from the radio button or the address', () => {
        cy.login();
        cy.visit(ADMIN_PATH);
        cy.get(`${REGION} tbody tr`).should('have.length', 3);
        cy.contains('label', 'Jahia advisories only (2)').click();
        cy.get('#security-updates-filter-advisories').should('be.checked');
        cy.get(`${REGION} tbody tr`).should('have.length', 2);
        cy.get(`${REGION} tbody`).should('contain', 'jahia-core').and('not.contain', 'Reported by the module owner');

        cy.visit(`${ADMIN_PATH}?filter=advisories`);
        cy.get('#security-updates-filter-advisories').should('be.checked');
        cy.get(`${REGION} tbody tr`).should('have.length', 2);
    });
});
