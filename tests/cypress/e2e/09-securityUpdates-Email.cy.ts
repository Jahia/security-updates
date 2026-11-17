import {
    FIXTURE_SETTINGS,
    SETTINGS_PATH,
    checkPageAccessibility,
    ensureFixtureReport,
    errorsOf,
    provision,
    raw,
    readEmailStatus,
    readSettings,
    runCheck,
    sendTestEmail,
    until
} from '../utils/securityUpdates';

/**
 * The security email, sent through Jahia's mail service to the Mailpit sink of the test stack (service smtp-server;
 * @jahia/cypress points Jahia's mail service at it from SMTP_SERVER_URL, and the recipient of Jahia's notifications is
 * admin@smtp-server.localhost on every Jahia version).
 *
 * Fixture store: two components need an update of at least HIGH severity (jahia-core CRITICAL; security-updates HIGH).
 */
describe('Security updates: security email', () => {
    const MODULE = 'security-updates';
    const RECIPIENT = 'admin@smtp-server.localhost';
    const PANEL = '[data-testid="security-updates-email-status"]';
    const SEND_TEST = '[data-sel-role="security-updates-send-test-email"]';
    const TEST_STATUS = '[data-testid="security-updates-email-test-status"]';
    const TEST_SUBJECT = 'Test email: security updates of Jahia on';
    const SCHEDULED_SUBJECT = '2 components need a security update';

    const isAvailable = (result: unknown) => errorsOf(result).length === 0 &&
        Boolean((result as { data?: { securityUpdates?: unknown } }).data?.securityUpdates);

    const bundle = (operation: 'stopBundle' | 'startBundle') => {
        cy.runProvisioningScript({script: [{[operation]: MODULE}]});
    };

    /** The messages whose subject holds the text, once there are `count` of them (never more). */
    const mailsWithSubject = (text: string, count = 1) => cy.waitUntil(
        () => cy.mailpitSearchEmails(`subject:"${text}"`).then(result => (result.messages_count >= count ? result : false)),
        {timeout: 60000, interval: 1000, errorMsg: `no ${count} email(s) with the subject "${text}" in Mailpit`}
    ).then(result => {
        expect(result.messages_count, `emails with the subject "${text}"`).to.equal(count);
        return result.messages;
    });

    before(() => {
        cy.apolloClient();
        // Named failure when the sink is missing, rather than a timeout on an empty mailbox.
        cy.mailpitReady().should('equal', true);
        provision('settings-fixture-defaults.yml');
        until(readSettings, FIXTURE_SETTINGS, 'fixture settings');

        // No previous email: the time of the last one lives in the data area of the module, and in memory.
        bundle('stopBundle');
        cy.waitUntil(() => raw.report().then(result => !isAvailable(result)), {timeout: 120000, interval: 2000, errorMsg: 'the module never stopped'});
        cy.executeGroovy('groovy/resetEmailState.groovy', {});
        bundle('startBundle');
        cy.waitUntil(() => raw.report().then(isAvailable), {timeout: 120000, interval: 2000, errorMsg: 'the module never started again'});

        ensureFixtureReport();
        cy.mailpitDeleteAllEmails();
    });

    after(() => {
        cy.apolloClient();
        provision('settings-fixture-defaults.yml');
        cy.mailpitDeleteAllEmails();
    });

    it('the Settings tab shows where the email goes, and that none was sent yet', () => {
        readEmailStatus().then(email => {
            expect(email.mailServerReady, 'Jahia\'s mail service points at Mailpit').to.equal(true);
            expect(email.recipientMissing).to.equal(false);
            expect(email.processingServer, 'a single node is the processing server').to.equal(true);
            expect(email.lastSentAt).to.equal(null);
        });

        cy.login();
        cy.visit(SETTINGS_PATH);
        cy.get('#security-updates-setting-emailEnabled').should('not.be.checked');
        cy.get('#security-updates-setting-emailFrequency').should('have.value', 'WEEKLY');
        cy.get('#security-updates-setting-emailMinimumSeverity').should('have.value', 'HIGH');
        cy.get(PANEL).find('[data-testid="security-updates-email-recipient"]')
            .should('have.attr', 'data-tone', 'info')
            .and('contain', 'the address that receives Jahia\'s notifications');
        cy.get(PANEL).find('[data-testid="security-updates-email-last-sent"]').should('contain', 'No email has been sent from this server yet.');
        checkPageAccessibility();
    });

    it('"Send a test email" sends the current findings to the notification address, at most once a minute', () => {
        cy.login();
        cy.visit(SETTINGS_PATH);
        cy.get(SEND_TEST).click();
        cy.get(TEST_STATUS).should('have.attr', 'role', 'status')
            .and('contain', 'Test email handed to Jahia\'s mail service.');

        mailsWithSubject(TEST_SUBJECT).then(messages => {
            expect(messages[0].To.map(to => to.Address)).to.deep.equal([RECIPIENT]);
            cy.mailpitGetMail(messages[0].ID).then(mail => {
                expect(mail.Text).to.contain('This is a test email')
                    .and.to.contain('jahia-core')
                    .and.to.contain('Jahia upgrade required')
                    .and.to.contain('security-updates')
                    .and.to.contain('The security updates email is off');
                expect(mail.HTML).to.contain('<h1').and.to.contain('jahia-core');
            });
        });

        // A second one within the minute is refused, and nothing more reaches the mailbox.
        cy.get(SEND_TEST).click();
        cy.get(TEST_STATUS).should('contain', 'a test email was sent less than a minute ago');
        sendTestEmail().should('equal', 'RATE_LIMITED');
        mailsWithSubject(TEST_SUBJECT, 1);
        // A test email is not the scheduled one.
        readEmailStatus().then(email => expect(email.lastSentAt, 'no scheduled email').to.equal(null));
    });

    it('with the email on, a check sends it once; the next check within the period sends nothing', () => {
        cy.mailpitDeleteAllEmails();
        provision('email-daily.yml');
        until(readSettings, {emailEnabled: true, emailFrequency: 'DAILY'}, 'email settings');

        // A check ends once its email has gone out.
        runCheck();
        mailsWithSubject(SCHEDULED_SUBJECT).then(messages => {
            expect(messages[0].Subject).to.match(/^Jahia on .+: 2 components need a security update$/);
            expect(messages[0].To.map(to => to.Address)).to.deep.equal([RECIPIENT]);
            cy.mailpitGetMail(messages[0].ID).then(mail => {
                expect(mail.Text).to.contain('2 components need a security update (severity High or above).')
                    .and.to.contain('jahia-core')
                    .and.to.contain('security-updates')
                    .and.to.contain('Reported by the module owner')
                    .and.to.contain('daily at most');
                expect(mail.Text).to.not.contain('This is a test email');
            });
        });
        readEmailStatus().then(email => {
            expect(email.lastSentAt, 'the time of the email').to.be.a('string');
            cy.wrap(email.lastSentAt).as('lastSentAt');
        });

        runCheck();
        cy.get('@lastSentAt').then(lastSentAt => readEmailStatus().then(email => {
            expect(email.lastSentAt, 'no second email within the period').to.equal(lastSentAt);
        }));
        cy.mailpitSearchEmails(`subject:"${SCHEDULED_SUBJECT}"`).its('messages_count').should('equal', 1);

        cy.login();
        cy.visit(SETTINGS_PATH);
        cy.get(PANEL).find('[data-testid="security-updates-email-last-sent"]').should('contain', 'Last email from this server:');
    });
});
