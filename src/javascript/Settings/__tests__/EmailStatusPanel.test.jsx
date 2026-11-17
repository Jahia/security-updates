import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {axe} from 'jest-axe';
import {useMutation, useQuery} from '@apollo/client';
import {EmailStatusPanel} from '../EmailStatusPanel';

const status = overrides => ({
    data: {
        securityUpdates: {
            email: {
                mailServerReady: true,
                recipient: 'admin@example.org',
                recipientMissing: false,
                processingServer: true,
                lastSentAt: null,
                ...overrides
            }
        }
    },
    loading: false,
    error: undefined
});

describe('EmailStatusPanel', () => {
    let send;

    beforeEach(() => {
        useQuery.mockReturnValue(status());
        send = jest.fn(() => Promise.resolve({data: {securityUpdates: {sendTestEmail: 'SENT'}}}));
        useMutation.mockReturnValue([send, {loading: false}]);
    });

    const recipient = () => screen.getByTestId('security-updates-email-recipient');
    const result = () => screen.getByTestId('security-updates-email-test-status');
    const sendButton = () => screen.getByRole('button', {name: 'settings.email.sendTest'});

    test('names the recipient when the server gives it, in a group named by its title', () => {
        render(<EmailStatusPanel/>);

        expect(screen.getByRole('group', {name: 'settings.email.title'})).toBeInTheDocument();
        expect(recipient()).toHaveTextContent('settings.email.recipient');
        expect(recipient()).toHaveAttribute('data-tone', 'info');
        expect(screen.getByTestId('security-updates-email-last-sent')).toHaveTextContent('settings.email.neverSent');
        expect(screen.queryByTestId('security-updates-email-processing')).toBeNull();
    });

    test('reads the state outside the Apollo cache', () => {
        render(<EmailStatusPanel/>);
        expect(useQuery).toHaveBeenCalledWith(expect.anything(), {fetchPolicy: 'no-cache'});
    });

    test('without the address (not an administrator, or the mail service chooses it) speaks of the notification address', () => {
        useQuery.mockReturnValue(status({recipient: null}));
        render(<EmailStatusPanel/>);
        expect(recipient()).toHaveTextContent('settings.email.recipientDefault');
    });

    test.each([
        [{mailServerReady: false}, 'settings.email.notConfigured'],
        [{recipientMissing: true, recipient: null}, 'settings.email.noRecipient']
    ])('warns when no email can be sent (%o)', (overrides, text) => {
        useQuery.mockReturnValue(status(overrides));
        render(<EmailStatusPanel/>);
        expect(recipient()).toHaveTextContent(text);
        expect(recipient()).toHaveAttribute('data-tone', 'warning');
    });

    test('says when another node sends the email, and when the last email went out', () => {
        useQuery.mockReturnValue(status({processingServer: false, lastSentAt: '2026-11-13T10:00:00Z'}));
        render(<EmailStatusPanel/>);
        expect(screen.getByTestId('security-updates-email-processing')).toHaveTextContent('settings.email.otherNode');
        expect(screen.getByTestId('security-updates-email-last-sent')).toHaveTextContent('settings.email.lastSent');
    });

    test('says while loading, and when the state cannot be loaded', () => {
        useQuery.mockReturnValue({data: undefined, loading: true, error: undefined});
        const {unmount} = render(<EmailStatusPanel/>);
        expect(screen.getByText('settings.email.loading')).toBeInTheDocument();
        unmount();

        useQuery.mockReturnValue({data: undefined, loading: false, error: new Error('boom')});
        render(<EmailStatusPanel/>);
        expect(screen.getByText('settings.email.loadError')).toBeInTheDocument();
    });

    test('has no test button unless asked', () => {
        render(<EmailStatusPanel/>);
        expect(screen.queryByRole('button', {name: 'settings.email.sendTest'})).toBeNull();
        expect(screen.queryByTestId('security-updates-email-test-status')).toBeNull();
    });

    test('the test button sends one test email at a time and announces the outcome', async () => {
        let finish;
        send.mockImplementationOnce(() => new Promise(resolve => {
            finish = resolve;
        }));
        render(<EmailStatusPanel hasTestButton/>);

        expect(result()).toHaveAttribute('role', 'status');
        fireEvent.click(sendButton());
        fireEvent.click(sendButton());
        await waitFor(() => expect(result()).toHaveTextContent('settings.email.sending'));
        expect(send).toHaveBeenCalledTimes(1);

        finish({data: {securityUpdates: {sendTestEmail: 'SENT'}}});
        await waitFor(() => expect(result()).toHaveTextContent('settings.email.outcome.SENT'));
    });

    test.each(['MAIL_SERVER_NOT_CONFIGURED', 'NO_RECIPIENT', 'RATE_LIMITED', 'FAILED'])('announces the outcome %s', async outcome => {
        send.mockResolvedValueOnce({data: {securityUpdates: {sendTestEmail: outcome}}});
        render(<EmailStatusPanel hasTestButton/>);
        fireEvent.click(sendButton());
        await waitFor(() => expect(result()).toHaveTextContent(`settings.email.outcome.${outcome}`));
    });

    test('an unknown answer reads as FAILED, a failed request says so', async () => {
        send.mockResolvedValueOnce({data: {securityUpdates: {sendTestEmail: 'SOMETHING_NEW'}}});
        render(<EmailStatusPanel hasTestButton/>);
        fireEvent.click(sendButton());
        await waitFor(() => expect(result()).toHaveTextContent('settings.email.outcome.FAILED'));

        send.mockRejectedValueOnce(new Error('network'));
        fireEvent.click(sendButton());
        await waitFor(() => expect(result()).toHaveTextContent('settings.email.outcome.REQUEST_FAILED'));
    });

    test('jest-axe on the Moonstone double: no violation', async () => {
        const {container} = render(<EmailStatusPanel hasTestButton/>);
        expect(await axe(container)).toHaveNoViolations();
    });
});
