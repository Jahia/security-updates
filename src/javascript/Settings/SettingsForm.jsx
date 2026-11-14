import {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Separator, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {GET_SETTINGS, RESET_SETTINGS, UPDATE_SETTINGS} from './Settings.gql';
import {FIELDS, fromSettings, restartsSchedule, toInput} from './settingsValues';
import {contrastRatio, formatRatio} from './colorUtils';
import {requestErrorCode, splitErrors} from './settingsResult';
import {ColorField, idOf, SelectField, SwitchField, TextField} from './fields';
import {BannerPreview} from './BannerPreview';
import {SettingsSummary} from './SettingsSummary';
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
const INVALID_LIST_ID = 'security-updates-settings-invalid-list';
const COLOR_FIELDS = ['bannerBackgroundColor', 'bannerTextColor'];

/**
 * The errors left once a field is edited: its own, and, for a colour, the low contrast error of the other colour (the
 * contrast is that of the pair). The same object when there is nothing to clear.
 */
const withoutErrorsOf = (errors, field) => {
    const cleared = Object.keys(errors).filter(key => key === field ||
        (COLOR_FIELDS.includes(field) && COLOR_FIELDS.includes(key) && errors[key] === 'LOW_CONTRAST'));
    return cleared.length === 0 ? errors : Object.fromEntries(Object.entries(errors).filter(([key]) => !cleared.includes(key)));
};

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
 * The alert of a refused save: what happened, then (for field errors) a link to each setting to correct, which moves
 * the focus to its control.
 */
const FormAlert = ({formError, errors}) => {
    const {t} = useTranslation(NS);
    const invalid = FIELDS.filter(field => errors[field]);
    const goTo = (event, field) => {
        event.preventDefault();
        document.getElementById(idOf(field))?.focus();
    };

    return (
        <div role="alert" className={styles.alert} data-testid="security-updates-settings-alert">
            <StatusMessage tone="warning">{formError === 'invalid' ? t('settings.invalid') : t(`settings.errors.${formError}`)}</StatusMessage>
            {invalid.length > 0 && (
                <>
                    <Typography id={INVALID_LIST_ID} weight="bold">{t('settings.invalidList')}</Typography>
                    <ul className={styles.invalidList} aria-labelledby={INVALID_LIST_ID}>
                        {invalid.map(field => (
                            <li key={field}>
                                <a className={styles.invalidLink} href={`#${idOf(field)}`} onClick={event => goTo(event, field)}>
                                    {t(`settings.fields.${field}.label`)}
                                </a>
                            </li>
                        ))}
                    </ul>
                </>
            )}
        </div>
    );
};

FormAlert.propTypes = {formError: PropTypes.string.isRequired, errors: PropTypes.object.isRequired};

/**
 * The settings of the module. Users who cannot change them (settings.canEdit false) see them as text. The form is
 * filled once from the query (a later answer of the query does not overwrite the edits), then from the values
 * returned by each save or reset. While a save or reset runs, the controls do not take edits (text fields are read
 * only, the others ignore changes, the group is aria-busy), since the answer replaces every value; they keep the focus.
 *
 * @param {{onSettingsChanged: function}} props onSettingsChanged is called after each successful save or reset (the
 *        page reads its report again)
 */
export const SettingsForm = ({onSettingsChanged}) => {
    const {t} = useTranslation(NS);
    const {data, error} = useQuery(GET_SETTINGS, {fetchPolicy: QUERY_FETCH_POLICY});
    const [updateSettings] = useMutation(UPDATE_SETTINGS);
    const [resetSettings] = useMutation(RESET_SETTINGS);
    const [form, setForm] = useState(null);
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [status, setStatus] = useState('');
    const [isConfirming, setConfirming] = useState(false);
    const [isSaving, setSaving] = useState(false);
    // The confirmation of an applied colour suggestion, shown next to the preview.
    const [applied, setApplied] = useState('');
    // The settings as the server last returned them: a save says when the check schedule restarts.
    const [saved, setSaved] = useState(null);
    const busy = useRef(false);
    const isFocusOnError = useRef(false);
    const focusNext = useRef(null);
    const loaded = data?.securityUpdates?.settings;
    const isReadOnly = loaded ? loaded.canEdit !== true : true;

    useEffect(() => {
        if (loaded && !form) {
            setForm(fromSettings(loaded));
            setSaved(loaded);
        }
    }, [loaded, form]);

    // After a refused save only (not when an edit clears an error): the first invalid field gets the focus.
    useEffect(() => {
        const first = FIELDS.find(field => errors[field]);
        if (isFocusOnError.current && first) {
            document.getElementById(idOf(first))?.focus();
        }

        isFocusOnError.current = false;
    }, [errors]);

    // A colour suggestion removes the button that applied it: the focus goes to the colour field it changed.
    useEffect(() => {
        if (focusNext.current) {
            document.getElementById(focusNext.current)?.focus();
            focusNext.current = null;
        }
    });

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

    // An edited field loses its error (and its aria-invalid) at once, and the message of the last save or suggestion
    // goes: it no longer describes the form. Edits wait for the running save or reset.
    const onChange = (field, value) => {
        if (busy.current) {
            return;
        }

        setForm(previous => ({...previous, [field]: value}));
        setErrors(previous => withoutErrorsOf(previous, field));
        setStatus('');
        setApplied('');
    };

    const ratio = formatRatio(contrastRatio(form.bannerBackgroundColor, form.bannerTextColor));

    // Applies a suggested colour (or, with a null field, the default pair), says so next to the preview, and moves the
    // focus to the colour field that changed (the background one for the default pair).
    const onApply = (field, value) => {
        if (busy.current) {
            return;
        }

        if (field) {
            onChange(field, value);
            setApplied(t(field === 'bannerTextColor' ? 'settings.suggest.appliedText' : 'settings.suggest.appliedBackground', values({value})));
        } else {
            onChange('bannerBackgroundColor', DEFAULT_BANNER_BACKGROUND);
            onChange('bannerTextColor', DEFAULT_BANNER_TEXT);
            setApplied(t('settings.suggest.appliedDefaults'));
        }

        focusNext.current = idOf(field || 'bannerBackgroundColor');
    };

    const showErrors = (byField, general) => {
        isFocusOnError.current = true;
        setErrors(byField);
        setFormError(general || 'invalid');
        setStatus('');
    };

    // The message says when the next check runs only when the change restarted the check schedule.
    const handleResult = (result, done) => {
        if (result?.ok && result.settings) {
            setForm(fromSettings(result.settings));
            setSaved(result.settings);
            setErrors({});
            setFormError(null);
            setStatus(t(restartsSchedule(saved, result.settings) ? `settings.${done}` : `settings.${done}Now`));
            window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
            onSettingsChanged?.();
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
        setSaving(true);
        setApplied('');
        setStatus(t('settings.saving'));
        Promise.resolve()
            .then(mutate)
            .then(response => handleResult(pick(response?.data?.securityUpdates), done))
            .catch(failure => showErrors({}, requestErrorCode(failure)))
            .finally(() => {
                busy.current = false;
                setSaving(false);
            });
    };

    const onSubmit = event => {
        event.preventDefault();
        if (busy.current) {
            return;
        }

        const {input, errors: local} = toInput(form);
        if (!input) {
            showErrors(local, null);
            return;
        }

        run(() => updateSettings({variables: {input}}), r => r?.updateSettings, 'saved');
    };

    const onConfirmReset = () => {
        setConfirming(false);
        run(() => resetSettings(), r => r?.resetSettings, 'resetDone');
    };

    if (isReadOnly) {
        return (
            <Section>
                <Typography className={styles.text}>{t('settings.intro')}</Typography>
                <StatusMessage tone="info">{t('settings.readOnly')}</StatusMessage>
                <SettingsSummary form={form}/>
            </Section>
        );
    }

    const fieldProps = {form, errors, onChange, isBusy: isSaving};
    return (
        <Section>
            <Typography className={styles.text}>{t('settings.intro')}</Typography>
            <form noValidate className={styles.form} onSubmit={onSubmit}>
                <fieldset className={styles.group} aria-busy={isSaving ? 'true' : undefined}>
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
                        <BannerPreview form={form} applied={applied} onApply={onApply}/>
                        <SelectField field="bannerDismissPolicy" {...fieldProps}>
                            {DISMISS_POLICIES.map(policy => <option key={policy} value={policy}>{t(`settings.dismiss.${policy}`)}</option>)}
                        </SelectField>
                        <TextField
                            field="bannerDismissHours"
                            type="number"
                            isDisabled={form.bannerDismissPolicy !== 'HOURS'}
                            note={form.bannerDismissPolicy === 'HOURS' ? undefined : t('settings.fields.bannerDismissHours.disabled')}
                            {...fieldProps}
                        />
                    </fieldset>
                </fieldset>
                {formError && <FormAlert formError={formError} errors={errors}/>}
                <p role="status" aria-live="polite" data-testid="security-updates-settings-status" className={styles.status}>{status}</p>
                <div className={styles.actions}>
                    {/* The form's only submit path: Enter in a field or this button. Moonstone's Button calls onClick
                        unconditionally (a missing one throws on click), hence the no-op: the submit does the work. */}
                    <Button size="big" color="accent" type="submit" className={styles.target} label={t('settings.save')} onClick={() => undefined}/>
                    <Button size="big" variant="ghost" className={styles.target} label={t('settings.reset')} onClick={() => setConfirming(true)}/>
                </div>
            </form>
            <ResetDialog isOpen={isConfirming} onCancel={() => setConfirming(false)} onConfirm={onConfirmReset}/>
        </Section>
    );
};

SettingsForm.propTypes = {onSettingsChanged: PropTypes.func};

export default SettingsForm;
