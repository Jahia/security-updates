import { DocumentNode } from 'graphql'
import { createUser, deleteUser } from '@jahia/cypress'

/**
 * The query and the mutation require the adminTemplates permission (@GraphQLRequiresPermission on the
 * namespace fields and on the nested fields), and the admin route is registered with
 * requiredPermission 'adminTemplates'.
 */
describe('Security updates - permission enforcement', () => {
    const DENIED_USER = 'securityUpdatesDeniedUser'
    const PASSWORD = 'SecUpd9Pwd-Test'
    const ADMIN_PATH = '/jahia/administration/securityUpdates'

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getReport: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSecurityUpdatesReport.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const refresh: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/refreshSecurityUpdates.graphql')

    const errorsOf = (result: { graphQLErrors?: Array<{ message: string }>; errors?: Array<{ message: string }> }) =>
        result.graphQLErrors ?? result.errors ?? []

    before(() => {
        cy.login()
        createUser(DENIED_USER, PASSWORD)
    })

    after(() => {
        cy.apolloClient()
        cy.login()
        deleteUser(DENIED_USER)
    })

    it('denies the report to a user without adminTemplates', () => {
        cy.apolloClient({ username: DENIED_USER, password: PASSWORD })
        cy.apollo({ query: getReport, errorPolicy: 'all' }).then((result: never) => {
            const errs = errorsOf(result)
            expect(errs, 'denial errors').to.have.length.greaterThan(0)
            expect(errs.map((e: { message: string }) => e.message).join(' ')).to.contain('Permission denied')
        })
    })

    it('denies the refresh mutation to a user without adminTemplates', () => {
        cy.apolloClient({ username: DENIED_USER, password: PASSWORD })
        cy.apollo({ mutation: refresh, errorPolicy: 'all' }).then((result: never) => {
            expect(errorsOf(result), 'denial errors').to.have.length.greaterThan(0)
        })
    })

    it('allows root', () => {
        cy.apolloClient()
        cy.apollo({ query: getReport }).then((result: never) => {
            expect(errorsOf(result), 'no errors').to.have.length(0)
        })
    })

    it('does not show the admin page to a user without adminTemplates', () => {
        cy.login(DENIED_USER, PASSWORD)
        cy.visit(ADMIN_PATH, { failOnStatusCode: false })
        cy.contains('button', 'Check now').should('not.exist')
    })
})
