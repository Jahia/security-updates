import {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Separator, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {GET_SETTINGS, RESET_SETTINGS, UPDATE_SETTINGS} from './Settings.gql';
import {FIELDS, fromSettings, toInput} from './settingsValues';
import {contrastRatio, formatRatio} from './colorUtils';
import {requestErrorCode, splitErrors} from './settingsResult';
import {ColorField, idOf, SelectField, SwitchField, TextField} from './fields';
import {BannerPreview} from './BannerPreview';
import {ResetDialog} from './ResetDialog';
import {StatusMessage} from '../SecurityUpdates/StatusMessage';
import {
    CHANGED_EVENT,
    DEFAULT_BANNER_BACKGROUND,
    DEFAULT_BANNER_TEXT,
    DISMISS_POLICIES,
    NS,
    QUERY_FETCH_POLICY,
    THRESHOLD_SEVERITIES
} from '../common/constants';
import {values} from '../common/i18n';

const TITLE_ID = 'security-updates-settings-title';

const Section = ({children}) => {
    const {t} = useTranslation(NS);
    return (
        <section className={styles.settings} aria-labelledby={TITLE_ID}>
            <Separator/>
            <Typography component="h2" variant="heading" id={TITLE_ID}>{t('settings.title')}</Typography>
            {children}
        </section>
    );
};

Section.propTypes = {children: PropTypes.node.isRequired};

/**
 * The settings of the module. Users who cannot change them (settings.canEdit false) see them read-only. The form is
 * filled once from the query, then from the values returned by each save or reset (no refetch that could overwrite
 * edits in progress).
 */
export const SettingsForm = () => {
    const {t} = useTranslation(NS);
    const {data, error} = useQuery(GET_SETTINGS, {fetchPolicy: QUERY_FETCH_POLICY});
    const [updateSettings] = useMutation(UPDATE_SETTINGS);
    const [resetSettings] = useMutation(RESET_SETTINGS);
    const [form, setForm] = useState(null);
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [status, setStatus] = useState('');
    const [isConfirming, setConfirming] = useState(false);
    const busy = useRef(false);
    const loaded = data?.securityUpdates?.settings;
    const isReadOnly = loaded ? loaded.canEdit !== true : true;

    useEffect(() => {
        if (loaded && !form) {
            setForm(fromSettings(loaded));
        }
    }, [loaded, form]);

    useEffect(() => {
        const first = FIELDS.find(field => errors[field]);
        if (first) {
            document.getElementById(idOf(first))?.focus();
        }
    }, [errors]);

    if (!form) {
        return (
            <Section>
                {error ?
                    <StatusMessage tone="warning" role="alert">{t('settings.loadError')}</StatusMessage> :
                    (
                        <div className={styles.loading}>
                            <span aria-hidden="true"><Loader size="small"/></span>
                            <Typography component="span">{t('settings.loading')}</Typography>
                        </div>
                    )}
            </Section>
        );
    }

    const onChange = (field, value) => setForm(previous => ({...previous, [field]: value}));
    const ratio = formatRatio(contrastRatio(form.bannerBackgroundColor, form.bannerTextColor));

    // Applies a suggested colour (or, with a null field, the default pair) and says so in the status region.
    const onApply = (field, value) => {
        if (field) {
            onChange(field, value);
            setStatus(t(field === 'bannerTextColor' ? 'settings.suggest.appliedText' : 'settings.suggest.appliedBackground', values({value})));
        } else {
            setForm(previous => ({...previous, bannerBackgroundColor: DEFAULT_BANNER_BACKGROUND, bannerTextColor: DEFAULT_BANNER_TEXT}));
            setStatus(t('settings.suggest.appliedDefaults'));
        }
    };

    const showErrors = (byField, general) => {
        setErrors(byField);
        setFormError(general || 'invalid');
        setStatus('');
    };

    const handleResult = (result, done) => {
        if (result?.ok && result.settings) {
            setForm(fromSettings(result.settings));
            setErrors({});
            setFormError(null);
            setStatus(done);
            window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
            return;
        }

        const {byField, general} = splitErrors(result?.errors);
        showErrors({...byField}, general);
    };

    // One write at a time: a second submit while one is running is ignored.
    const run = (mutate, pick, done) => {
        if (busy.current) {
            return;
        }

        busy.current = true;
        setStatus(t('settings.saving'));
        Promise.resolve()
            .then(mutate)
            .then(response => handleResult(pick(response?.data?.securityUpdates), done))
            .catch(failure => showErrors({}, requestErrorCode(failure)))
            .finally(() => {
                busy.current = false;
            });
    };

    const onSubmit = event => {
        event.preventDefault();
        if (isReadOnly || busy.current) {
            return;
        }

        const {input, errors: local} = toInput(form);
        if (!input) {
            showErrors(local, null);
            return;
        }

        run(() => updateSettings({variables: {input}}), r => r?.updateSettings, t('settings.saved'));
    };

    const onConfirmReset = () => {
        setConfirming(false);
        run(() => resetSettings(), r => r?.resetSettings, t('settings.resetDone'));
    };

    const fieldProps = {form, errors, onChange};
    return (
        <Section>
            <Typography variant="caption" className={styles.text}>{t('settings.intro')}</Typography>
            {isReadOnly && <StatusMessage tone="info">{t('settings.readOnly')}</StatusMessage>}
            <form noValidate className={styles.form} onSubmit={onSubmit}>
                {/* A disabled fieldset disables every control in it: the read-only view. */}
                <fieldset className={styles.group} disabled={isReadOnly}>
                    <fieldset className={styles.group}>
                        <Typography component="legend" variant="subheading" weight="bold">{t('settings.groups.checks')}</Typography>
                        <SwitchField field="enabled" {...fieldProps}/>
                        <TextField field="refreshIntervalHours" type="number" {...fieldProps}/>
                        <SwitchField field="useForgeSettings" {...fieldProps}/>
                        <TextField field="defaultStoreUrl" type="text" {...fieldProps}/>
                        <TextField field="extraFeedUrls" type="textarea" {...fieldProps}/>
                    </fieldset>
                    <fieldset className={styles.group}>
                        <Typography component="legend" variant="subheading" weight="bold">{t('settings.groups.banner')}</Typography>
                        <SwitchField field="bannerEnabled" {...fieldProps}/>
                        <SelectField field="bannerMinimumSeverity" {...fieldProps}>
                            {THRESHOLD_SEVERITIES.map(severity => <option key={severity} value={severity}>{t(`page.severity.${severity}`)}</option>)}
                        </SelectField>
                        <SwitchField field="bannerIncludeOwnerReports" {...fieldProps}/>
                        <ColorField field="bannerBackgroundColor" ratio={ratio} {...fieldProps}/>
                        <ColorField field="bannerTextColor" ratio={ratio} {...fieldProps}/>
                        <BannerPreview form={form} isReadOnly={isReadOnly} onApply={onApply}/>
                        <SelectField field="bannerDismissPolicy" {...fieldProps}>
                            {DISMISS_POLICIES.map(policy => <option key={policy} value={policy}>{t(`settings.dismiss.${policy}`)}</option>)}
                        </SelectField>
                        <TextField field="bannerDismissHours" type="number" isDisabled={form.bannerDismissPolicy !== 'HOURS'} {...fieldProps}/>
                    </fieldset>
                </fieldset>
                {formError && (
                    <StatusMessage tone="warning" role="alert">
                        {formError === 'invalid' ? t('settings.invalid') : t(`settings.errors.${formError}`)}
                    </StatusMessage>
                )}
                <p role="status" aria-live="polite" data-testid="security-updates-settings-status" className={styles.status}>{status}</p>
                {!isReadOnly && (
                    <div className={styles.actions}>
                        {/* The form's only submit path: Enter in a field or this button. */}
                        <Button size="big" color="accent" type="submit" className={styles.target} label={t('settings.save')} onClick={() => undefined}/>
                        <Button size="big" variant="ghost" className={styles.target} label={t('settings.reset')} onClick={() => setConfirming(true)}/>
                    </div>
                )}
            </form>
            <ResetDialog isOpen={isConfirming} onCancel={() => setConfirming(false)} onConfirm={onConfirmReset}/>
        </Section>
    );
};

export default SettingsForm;
