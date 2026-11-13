import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Field, Input, Separator, Switch, Textarea, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {GET_SETTINGS, RESET_SETTINGS, UPDATE_SETTINGS} from './Settings.gql';
import {FIELDS, fromSettings, toInput} from './settingsValues';
import {contrastRatio, formatRatio, MIN_CONTRAST, normalizeHex} from './colorUtils';
import {suggestColor} from './contrastSuggestions';
import {StatusMessage} from '../SecurityUpdates/StatusMessage';

const NS = 'security-updates';
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const POLICIES = ['SESSION', 'HOURS', 'UNTIL_CHANGE', 'NEVER'];
const DEFAULT_BACKGROUND = '#1B1B1B';
const DEFAULT_TEXT = '#FFFFFF';

const idOf = field => `security-updates-setting-${field}`;

/** Help text, then the error when there is one: both are read with the control. */
const describedBy = (field, errors) => [`${idOf(field)}-help`, errors[field] ? `${idOf(field)}-error` : null]
    .filter(Boolean).join(' ');

/**
 * A Moonstone Field. Field puts its id on its wrapper and renders a <label> that is not associated with the control
 * (and its helper has no id), so, as in the other Jahia modules, each control carries an aria-label with the label
 * text, and the help and error texts are our own Typography with ids for aria-describedby.
 */
const SettingField = ({field, errors, ratio, children}) => {
    const {t} = useTranslation(NS);
    return (
        <Field id={`${idOf(field)}-field`} label={t(`settings.fields.${field}.label`)} hasError={Boolean(errors[field])}>
            {children}
            <Typography id={`${idOf(field)}-help`} variant="caption">{t(`settings.fields.${field}.help`)}</Typography>
            {errors[field] && (
                <Typography id={`${idOf(field)}-error`} variant="caption" weight="bold" className={styles.error}>
                    {t(`settings.errors.${errors[field]}`, {ratio})}
                </Typography>
            )}
        </Field>
    );
};

SettingField.propTypes = {
    field: PropTypes.string.isRequired,
    errors: PropTypes.object.isRequired,
    ratio: PropTypes.string,
    children: PropTypes.node.isRequired
};

const controlProps = (field, errors, t) => ({
    id: idOf(field),
    'aria-label': t(`settings.fields.${field}.label`),
    'aria-describedby': describedBy(field, errors),
    'aria-invalid': errors[field] ? 'true' : undefined
});

const SwitchField = ({field, form, errors, onChange}) => {
    const {t} = useTranslation(NS);
    return (
        <SettingField field={field} errors={errors}>
            <Switch {...controlProps(field, errors, t)} checked={form[field]} onChange={(event, value, checked) => onChange(field, checked)}/>
        </SettingField>
    );
};

SwitchField.propTypes = {field: PropTypes.string.isRequired, form: PropTypes.object.isRequired, errors: PropTypes.object.isRequired, onChange: PropTypes.func.isRequired};

const TextField = ({field, form, errors, onChange, type, isDisabled}) => {
    const {t} = useTranslation(NS);
    const props = controlProps(field, errors, t);
    const handle = event => onChange(field, event.target.value);
    let control;
    if (type === 'textarea') {
        control = <Textarea {...props} value={form[field]} rows={3} isDisabled={isDisabled} onChange={handle}/>;
    } else if (type === 'number') {
        control = <Input {...props} type="number" min={1} max={720} step={1} inputMode="numeric" value={form[field]} isDisabled={isDisabled} onChange={handle}/>;
    } else {
        control = <Input {...props} type="text" spellCheck={false} autoComplete="off" value={form[field]} isDisabled={isDisabled} onChange={handle}/>;
    }

    return <SettingField field={field} errors={errors}>{control}</SettingField>;
};

TextField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    type: PropTypes.oneOf(['text', 'number', 'textarea']),
    isDisabled: PropTypes.bool
};

/**
 * A native select, styled with Moonstone's variables. Moonstone's Dropdown renders a role="listbox" named after the
 * selected value rather than after the field, which loses the field's accessible name; a select keeps it, and keeps
 * the keyboard behaviour every browser and screen reader knows.
 */
const SelectField = ({field, form, errors, onChange, children}) => {
    const {t} = useTranslation(NS);
    return (
        <SettingField field={field} errors={errors}>
            <select {...controlProps(field, errors, t)} className={styles.select} value={form[field]} onChange={event => onChange(field, event.target.value)}>
                {children}
            </select>
        </SettingField>
    );
};

SelectField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    children: PropTypes.node.isRequired
};

/** A native colour picker (Moonstone has none) next to a Moonstone Input for the #RRGGBB value. */
const ColorField = ({field, form, errors, onChange, ratio}) => {
    const {t} = useTranslation(NS);
    const hex = normalizeHex(form[field]);
    return (
        <SettingField field={field} errors={errors} ratio={ratio}>
            <div className={styles.color}>
                <input
                    id={`${idOf(field)}-picker`}
                    type="color"
                    className={styles.picker}
                    aria-label={t(`settings.fields.${field}.picker`)}
                    value={(hex || '#000000').toLowerCase()}
                    onChange={event => onChange(field, event.target.value.toUpperCase())}
                />
                <Input
                    {...controlProps(field, errors, t)}
                    type="text"
                    value={form[field]}
                    spellCheck={false}
                    autoComplete="off"
                    maxLength={7}
                    onChange={event => onChange(field, event.target.value)}
                />
            </div>
        </SettingField>
    );
};

ColorField.propTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    ratio: PropTypes.string
};

/** One suggested colour: a decorative swatch, the value and ratio as text, and a button naming the value. */
const Suggestion = ({kind, suggestion, onUse}) => {
    const {t} = useTranslation(NS);
    return (
        <li className={styles.suggestion} data-testid={`security-updates-suggestion-${kind}`}>
            <span aria-hidden="true" className={styles.swatch} style={{backgroundColor: suggestion.value}}/>
            <Typography component="span">
                {t(`settings.suggest.${kind}`, {value: suggestion.value, ratio: suggestion.ratio})}
            </Typography>
            <Button
                variant="outlined"
                label={t(`settings.suggest.use_${kind}`, {value: suggestion.value})}
                onClick={() => onUse(suggestion.value)}
            />
        </li>
    );
};

Suggestion.propTypes = {
    kind: PropTypes.oneOf(['text', 'background']).isRequired,
    suggestion: PropTypes.shape({value: PropTypes.string.isRequired, ratio: PropTypes.string.isRequired}).isRequired,
    onUse: PropTypes.func.isRequired
};

const Preview = ({form, onApply}) => {
    const {t} = useTranslation(NS);
    const background = normalizeHex(form.bannerBackgroundColor);
    const text = normalizeHex(form.bannerTextColor);
    const ratio = contrastRatio(background, text);
    const passes = ratio !== null && ratio >= MIN_CONTRAST;
    let verdict = t('settings.preview.invalid');
    if (ratio !== null) {
        verdict = passes ? t('settings.preview.pass') : t('settings.preview.fail');
    }

    const textSuggestion = ratio !== null && !passes ? suggestColor(background, text) : null;
    const backgroundSuggestion = ratio !== null && !passes ? suggestColor(text, background) : null;
    return (
        <div className={styles.preview}>
            <Typography component="h3" variant="subheading" weight="bold">{t('settings.preview.title')}</Typography>
            <div
                data-testid="security-updates-banner-preview"
                className={styles.sample}
                style={{backgroundColor: passes ? background : DEFAULT_BACKGROUND, color: passes ? text : DEFAULT_TEXT}}
            >
                <p>{t('banner.message', {count: 3, threshold: t(`banner.threshold.${form.bannerMinimumSeverity}`)})}</p>
                <span className={styles.sampleLink}>{t('banner.link')}</span>
            </div>
            <StatusMessage tone={passes ? 'info' : 'warning'} data-testid="security-updates-contrast" aria-live="polite">
                {ratio !== null && <>{t('settings.preview.ratio')} <strong>{formatRatio(ratio)}:1</strong> </>}
                {verdict}
            </StatusMessage>
            {ratio !== null && !passes && (
                <div className={styles.suggestions} data-testid="security-updates-suggestions">
                    {(textSuggestion || backgroundSuggestion) ? (
                        <>
                            <Typography>{t('settings.suggest.intro')}</Typography>
                            <ul className={styles.suggestionList}>
                                {textSuggestion && (
                                    <Suggestion kind="text" suggestion={textSuggestion} onUse={value => onApply('bannerTextColor', value)}/>
                                )}
                                {backgroundSuggestion && (
                                    <Suggestion kind="background" suggestion={backgroundSuggestion} onUse={value => onApply('bannerBackgroundColor', value)}/>
                                )}
                            </ul>
                        </>
                    ) : (
                        <>
                            <Typography>{t('settings.suggest.none')}</Typography>
                            <Button
                                variant="outlined"
                                label={t('settings.suggest.useDefaults', {background: DEFAULT_BACKGROUND, text: DEFAULT_TEXT})}
                                onClick={() => onApply(null)}
                            />
                        </>
                    )}
                </div>
            )}
        </div>
    );
};

Preview.propTypes = {form: PropTypes.object.isRequired, onApply: PropTypes.func.isRequired};

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

    // Applies a suggested colour (or, with a null field, the default pair) and says so in the status region.
    const onApply = (field, value) => {
        if (field) {
            onChange(field, value);
            setStatus(t(field === 'bannerTextColor' ? 'settings.suggest.appliedText' : 'settings.suggest.appliedBackground', {value}));
        } else {
            setForm(previous => ({...previous, bannerBackgroundColor: DEFAULT_BACKGROUND, bannerTextColor: DEFAULT_TEXT}));
            setStatus(t('settings.suggest.appliedDefaults'));
        }
    };

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
            <Separator/>
            <Typography component="h2" variant="heading" id="security-updates-settings-title">{t('settings.title')}</Typography>
            <Typography variant="caption">{t('settings.intro')}</Typography>
            <form noValidate className={styles.form} onSubmit={onSave}>
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
                        {SEVERITIES.map(severity => <option key={severity} value={severity}>{t(`page.severity.${severity}`)}</option>)}
                    </SelectField>
                    <SwitchField field="bannerIncludeOwnerReports" {...fieldProps}/>
                    <ColorField field="bannerBackgroundColor" ratio={ratio} {...fieldProps}/>
                    <ColorField field="bannerTextColor" ratio={ratio} {...fieldProps}/>
                    <Preview form={form} onApply={onApply}/>
                    <SelectField field="bannerDismissPolicy" {...fieldProps}>
                        {POLICIES.map(policy => <option key={policy} value={policy}>{t(`settings.dismiss.${policy}`)}</option>)}
                    </SelectField>
                    <TextField
                        field="bannerDismissHours"
                        type="number"
                        isDisabled={form.bannerDismissPolicy !== 'HOURS'}
                        {...fieldProps}
                    />
                </fieldset>
                {formError && (
                    <StatusMessage tone="warning" role="alert">
                        {formError === 'invalid' ? t('settings.invalid') : t(`settings.errors.${formError}`)}
                    </StatusMessage>
                )}
                <Typography role="status" aria-live="polite" data-testid="security-updates-settings-status" className={styles.status}>{status}</Typography>
                <div className={styles.actions}>
                    <Button size="big" color="accent" label={t('settings.save')} onClick={onSave}/>
                    <Button size="big" variant="ghost" label={t('settings.reset')} onClick={onReset}/>
                </div>
            </form>
        </section>
    );
};

export default SettingsForm;
