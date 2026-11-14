// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })
import 'cypress-wait-until';
import 'cypress-mailpit';

// Helpers down below are added for debugging purposes.
// They should be moved to @jahia/cypress helpers afterwards.

/**
 * Clears cookies based on their type (session or persistent)
 * @param {string} type - The type of cookies to clear ('session' or 'persistent')
 * @returns {void}
 */
Cypress.Commands.add('clearCookiesByType', (type = 'session') => {
    cy.getCookies().then(cookies => {
        let cookiesToClear = cookies.filter(cookie => type.toLowerCase() === 'session' ? !cookie.expiry : cookie.expiry);

        cy.log(`🗑️  Clearing ${cookiesToClear.length} ${type} cookie(s):`);
        cookiesToClear.forEach(cookie => {
            const info = cookie.expiry ? `expires ${new Date(cookie.expiry * 1000).toISOString()}` : 'session only';
            cy.log(`  - ${cookie.name} (${info})`);
            cy.clearCookie(cookie.name);
        });

        cy.log(`Cleared ${cookiesToClear.length} ${type} cookie(s)`);
    });
});

/**
 * Simulates closing the browser by clearing all storage and session cookies
 * @returns {void}
 */
Cypress.Commands.add('simulateBrowserClose', () => {
    cy.log('Simulating browser close...');

    // Clear all storage
    // cy.clearLocalStorage();
    cy.clearAllSessionStorage();

    // Clear session cookies only
    cy.clearCookiesByType('session');

    cy.log('Browser close simulated (storage + session cookies cleared)');
});

/**
 * Logs all session storage items in a structured format
 * @returns {void}
 */
Cypress.Commands.add('logSessionStorage', () => {
    cy.getAllSessionStorage().then(session => {
        cy.log(`sessionStorage: ${JSON.stringify(session)}`);
    });
});

/**
 * Logs all local storage items in a structured format
 * @returns {void}
 */
Cypress.Commands.add('logLocalStorage', () => {
    cy.getAllLocalStorage().then(local => {
        cy.log(`localStorage: ${JSON.stringify(local)}`);
    });
});
