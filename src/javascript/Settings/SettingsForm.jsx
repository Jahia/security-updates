import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {GET_SETTINGS, RESET_SETTINGS, UPDATE_SETTINGS} from './Settings.gql';
import {FIELDS, fromSettings, toInput} from './settingsValues';
import {contrastRatio, formatRatio, MIN_CONTRAST, normalizeHex} from './colorUtils';

const NS = 'security-updates';
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const POLICIES = ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'];
const DEFAULT_BACKGROUND = '#1B1B1B';
const DEFAULT_TEXT = '#FFFFFF';

const idOf = field => `security-updates-setting-${field}`;

/** Help text, then the error when there is one: both are read with the control. */
const describedBy = (field, errors) => [`${idOf(field)}-help`, errors[field] ? `${idOf(field)}-error` : null]
    .filter(Boolean).join(' ');

const FieldMessages = ({field, errors, ratio}) => {
    const {t} = useTranslation(NS);
    return (
        <>
            <p id={`${idOf(field)}-help`} className={styles.help}>{t(`settings.fields.${field}.help`)}</p>
            {errors[field] && (
                <p id={`${idOf(field)}-error`} className={styles.error}>
                    <span aria-hidden="true">✖ </span>
                    {t(`settings.errors.${errors[field]}`, {ratio})}
                </p>
            )}
        </>
    );
};

FieldMessages.propTypes = {field: PropTypes.string.isRequired, errors: PropTypes.object.isRequired, ratio: PropTypes.string};

const CheckboxField = ({field, form, errors, onChange}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={styles.field}>
            <div className={styles.checkbox}>
                <input
                    id={idOf(field)}
                    type="checkbox"
                    checked={form[field]}
                    aria-describedby={describedBy(field, errors)}
                    aria-invalid={errors[field] ? 'true' : undefined}
                    onChange={event => onChange(field, event.target.checked)}
                />
                <label htmlFor={idOf(field)}>{t(`settings.fields.${field}.label`)}</label>
            </div>
            <FieldMessages field={field} errors={errors}/>
        </div>
    );
};

CheckboxField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired
};

const InputField = ({field, form, errors, onChange, type, isDisabled, children}) => {
    const {t} = useTranslation(NS);
    const common = {
        id: idOf(field),
        value: form[field],
        disabled: isDisabled,
        'aria-describedby': describedBy(field, errors),
        'aria-invalid': errors[field] ? 'true' : undefined,
        onChange: event => onChange(field, event.target.value)
    };
    let control;
    if (type === 'select') {
        control = <select {...common}>{children}</select>;
    } else if (type === 'textarea') {
        control = <textarea {...common} rows={3}/>;
    } else if (type === 'number') {
        control = <input {...common} type="number" min={1} max={720} step={1} inputMode="numeric"/>;
    } else {
        control = <input {...common} type="text" spellCheck={false} autoComplete="off"/>;
    }

    return (
        <div className={styles.field}>
            <label htmlFor={idOf(field)}>{t(`settings.fields.${field}.label`)}</label>
            {control}
            <FieldMessages field={field} errors={errors}/>
        </div>
    );
};

InputField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    type: PropTypes.oneOf(['text', 'number', 'select', 'textarea']),
    isDisabled: PropTypes.bool,
    children: PropTypes.node
};

const ColorField = ({field, form, errors, onChange, ratio}) => {
    const {t} = useTranslation(NS);
    const hex = normalizeHex(form[field]);
    return (
        <div className={styles.field}>
            <label htmlFor={idOf(field)}>{t(`settings.fields.${field}.label`)}</label>
            <div className={styles.color}>
                <input
                    id={`${idOf(field)}-picker`}
                    type="color"
                    aria-label={t(`settings.fields.${field}.picker`)}
                    value={(hex || '#000000').toLowerCase()}
                    onChange={event => onChange(field, event.target.value.toUpperCase())}
                />
                <input
                    id={idOf(field)}
                    type="text"
                    value={form[field]}
                    spellCheck={false}
                    autoComplete="off"
                    maxLength={7}
                    aria-describedby={describedBy(field, errors)}
                    aria-invalid={errors[field] ? 'true' : undefined}
                    onChange={event => onChange(field, event.target.value)}
                />
            </div>
            <FieldMessages field={field} errors={errors} ratio={ratio}/>
        </div>
    );
};

ColorField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    ratio: PropTypes.string
};

const Preview = ({form}) => {
    const {t} = useTranslation(NS);
    const background = normalizeHex(form.bannerBackgroundColor);
    const text = normalizeHex(form.bannerTextColor);
    const ratio = contrastRatio(background, text);
    let verdict = t('settings.preview.invalid');
    if (ratio !== null) {
        verdict = ratio >= MIN_CONTRAST ? t('settings.preview.pass') : t('settings.preview.fail');
    }

    const shown = ratio !== null && ratio >= MIN_CONTRAST;
    return (
        <div className={styles.preview}>
            <h4 className={styles.subtitle}>{t('settings.preview.title')}</h4>
            <div
                data-testid="security-updates-banner-preview"
                className={styles.sample}
                style={{backgroundColor: shown ? background : DEFAULT_BACKGROUND, color: shown ? text : DEFAULT_TEXT}}
            >
                <p>{t('banner.message', {count: 3, threshold: t(`banner.threshold.${form.bannerMinimumSeverity}`)})}</p>
                <span className={styles.sampleLink}>{t('banner.link')}</span>
            </div>
            <p data-testid="security-updates-contrast" aria-live="polite" className={styles.ratio}>
                {ratio !== null && <>{t('settings.preview.ratio')} <strong>{formatRatio(ratio)}:1</strong> </>}
                {verdict}
            </p>
        </div>
    );
};

Preview.propTypes = {form: PropTypes.object.isRequired};

export const SettingsForm = () => {
    const {t} = useTranslation(NS);
    const {data, refetch} = useQuery(GET_SETTINGS, {fetchPolicy: 'network-only'});
    const [updateSettings] = useMutation(UPDATE_SETTINGS);
    const [resetSettings] = useMutation(RESET_SETTINGS);
    const [form, setForm] = useState(null);
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState(null);
    const [status, setStatus] = useState('');
    const [busy, setBusy] = useState(false);
    const loaded = data?.securityUpdates?.settings;

    useEffect(() => {
        if (loaded) {
            setForm(fromSettings(loaded));
        }
    }, [loaded]);

    useEffect(() => {
        const first = FIELDS.find(field => errors[field]);
        if (first) {
            document.getElementById(idOf(first))?.focus();
        }
    }, [errors]);

    if (!form) {
        return null;
    }

    const onChange = (field, value) => setForm(previous => ({...previous, [field]: value}));
    const ratio = formatRatio(contrastRatio(form.bannerBackgroundColor, form.bannerTextColor));

    const handleResult = (result, done) => {
        if (result?.ok) {
            setErrors({});
            setFormError(null);
            setStatus(done);
            return refetch();
        }

        const byField = {};
        let general = null;
        (result?.errors || []).forEach(error => {
            if (error.field) {
                byField[error.field] = byField[error.field] || error.code;
            } else {
                general = error.code;
            }
        });
        setErrors(byField);
        setFormError(general || 'invalid');
        setStatus('');
        return null;
    };

    const run = (mutate, pick, done) => {
        setBusy(true);
        setStatus(t('settings.saving'));
        return mutate()
            .then(response => handleResult(pick(response?.data?.securityUpdates), done))
            .catch(() => {
                setFormError('WRITE_FAILED');
                setStatus('');
            })
            .finally(() => setBusy(false));
    };

    const onSave = event => {
        if (event) {
            event.preventDefault();
        }

        if (busy) {
            return;
        }

        const {input, errors: local} = toInput(form);
        if (!input) {
            setErrors(local);
            setFormError('invalid');
            setStatus('');
            return;
        }

        run(() => updateSettings({variables: {input}}), r => r?.updateSettings, t('settings.saved'));
    };

    const onReset = () => {
        if (!busy) {
            run(() => resetSettings(), r => r?.resetSettings, t('settings.resetDone'));
        }
    };

    const fieldProps = {form, errors, onChange};
    return (
        <section className={styles.settings} aria-labelledby="security-updates-settings-title">
            <h3 id="security-updates-settings-title" className={styles.title}>{t('settings.title')}</h3>
            <p className={styles.help}>{t('settings.intro')}</p>
            <form noValidate onSubmit={onSave}>
                <fieldset className={styles.group}>
                    <legend>{t('settings.groups.checks')}</legend>
                    <CheckboxField field="enabled" {...fieldProps}/>
                    <InputField field="refreshIntervalHours" type="number" {...fieldProps}/>
                    <CheckboxField field="useForgeSettings" {...fieldProps}/>
                    <InputField field="defaultStoreUrl" type="text" {...fieldProps}/>
                    <InputField field="extraFeedUrls" type="textarea" {...fieldProps}/>
                </fieldset>
                <fieldset className={styles.group}>
                    <legend>{t('settings.groups.banner')}</legend>
                    <CheckboxField field="bannerEnabled" {...fieldProps}/>
                    <InputField field="bannerMinimumSeverity" type="select" {...fieldProps}>
                        {SEVERITIES.map(severity => <option key={severity} value={severity}>{t(`page.severity.${severity}`)}</option>)}
                    </InputField>
                    <CheckboxField field="bannerIncludeOwnerReports" {...fieldProps}/>
                    <ColorField field="bannerBackgroundColor" ratio={ratio} {...fieldProps}/>
                    <ColorField field="bannerTextColor" ratio={ratio} {...fieldProps}/>
                    <Preview form={form}/>
                    <InputField field="bannerDismissPolicy" type="select" {...fieldProps}>
                        {POLICIES.map(policy => <option key={policy} value={policy}>{t(`settings.dismiss.${policy}`)}</option>)}
                    </InputField>
                    <InputField
                        field="bannerDismissHours"
                        type="number"
                        isDisabled={form.bannerDismissPolicy !== 'HOURS'}
                        {...fieldProps}
                    />
                </fieldset>
                {formError && (
                    <p role="alert" className={styles.alert}>
                        {formError === 'invalid' ? t('settings.invalid') : t(`settings.errors.${formError}`)}
                    </p>
                )}
                <p role="status" aria-live="polite" data-testid="security-updates-settings-status" className={styles.status}>{status}</p>
                <div className={styles.actions}>
                    <Button label={t('settings.save')} variant="primary" onClick={onSave}/>
                    <Button label={t('settings.reset')} variant="ghost" onClick={onReset}/>
                </div>
            </form>
        </section>
    );
};

export default SettingsForm;
