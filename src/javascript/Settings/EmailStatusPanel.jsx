import {useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {GET_EMAIL_STATUS, SEND_TEST_EMAIL} from './Settings.gql';
import {StatusMessage} from '../SecurityUpdates/StatusMessage';
import {formatDateTime} from '../SecurityUpdates/securityUpdatesUtils';
import {NS, QUERY_FETCH_POLICY} from '../common/constants';
import {values} from '../common/i18n';

/** Outcomes of the test email that the UI names; any other answer reads as FAILED. */
export const EMAIL_OUTCOMES = ['SENT', 'MAIL_SERVER_NOT_CONFIGURED', 'NO_RECIPIENT', 'RATE_LIMITED', 'FAILED'];

const TITLE_ID = 'security-updates-email-status-title';

/** Where the email goes, or why none can be sent: one message, a warning when nothing can be sent. */
const recipientMessage = (t, email) => {
    if (!email.mailServerReady) {
        return {tone: 'warning', text: t('settings.email.notConfigured')};
    }

    if (email.recipientMissing) {
        return {tone: 'warning', text: t('settings.email.noRecipient')};
    }

    return {
        tone: 'info',
        text: email.recipient ? t('settings.email.recipient', values({recipient: email.recipient})) : t('settings.email.recipientDefault')
    };
};

/**
 * The state of the security email: whether Jahia's mail service can send it and to whom (the address is shown to
 * server administrators only), whether this cluster node sends it, and when the last one went out. With
 * {@code hasTestButton}, a button sends a test email; its outcome is announced in a polite status.
 */
export const EmailStatusPanel = ({hasTestButton}) => {
    const {t, i18n} = useTranslation(NS);
    const {data, error} = useQuery(GET_EMAIL_STATUS, {fetchPolicy: QUERY_FETCH_POLICY});
    const [sendTestEmail] = useMutation(SEND_TEST_EMAIL);
    const [result, setResult] = useState('');
    const busy = useRef(false);
    const email = data?.securityUpdates?.email;

    const onSendTest = () => {
        if (busy.current) {
            return;
        }

        busy.current = true;
        setResult(t('settings.email.sending'));
        Promise.resolve()
            .then(() => sendTestEmail())
            .then(response => {
                const outcome = response?.data?.securityUpdates?.sendTestEmail;
                setResult(t(`settings.email.outcome.${EMAIL_OUTCOMES.includes(outcome) ? outcome : 'FAILED'}`));
            })
            .catch(() => setResult(t('settings.email.outcome.REQUEST_FAILED')))
            .finally(() => {
                busy.current = false;
            });
    };

    let body;
    if (email) {
        const recipient = recipientMessage(t, email);
        const lastSent = formatDateTime(email.lastSentAt, i18n?.language);
        body = (
            <>
                <StatusMessage tone={recipient.tone} data-testid="security-updates-email-recipient">{recipient.text}</StatusMessage>
                {!email.processingServer && (
                    <StatusMessage tone="info" data-testid="security-updates-email-processing">{t('settings.email.otherNode')}</StatusMessage>
                )}
                <Typography className={styles.help} data-testid="security-updates-email-last-sent">
                    {lastSent ? t('settings.email.lastSent', values({date: lastSent})) : t('settings.email.neverSent')}
                </Typography>
            </>
        );
    } else if (error) {
        body = <StatusMessage tone="warning">{t('settings.email.loadError')}</StatusMessage>;
    } else {
        body = <Typography className={styles.help}>{t('settings.email.loading')}</Typography>;
    }

    return (
        <div className={styles.emailStatus} role="group" aria-labelledby={TITLE_ID} data-testid="security-updates-email-status">
            <Typography id={TITLE_ID} weight="bold">{t('settings.email.title')}</Typography>
            {body}
            {hasTestButton && (
                <>
                    <div className={styles.actions}>
                        <Button
                            type="button"
                            size="big"
                            variant="outlined"
                            className={styles.target}
                            label={t('settings.email.sendTest')}
                            data-sel-role="security-updates-send-test-email"
                            onClick={onSendTest}
                        />
                    </div>
                    <p role="status" aria-live="polite" className={styles.status} data-testid="security-updates-email-test-status">{result}</p>
                </>
            )}
        </div>
    );
};

EmailStatusPanel.propTypes = {hasTestButton: PropTypes.bool};

export default EmailStatusPanel;
