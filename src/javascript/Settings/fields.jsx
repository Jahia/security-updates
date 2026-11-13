import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Input, Switch, Textarea, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {DEFAULT_BANNER_BACKGROUND, DEFAULT_BANNER_TEXT, MAX_HOURS, MIN_HOURS, NS} from '../common/constants';
import {values} from '../common/i18n';
import {normalizeHex} from './colorUtils';

export const idOf = field => `security-updates-setting-${field}`;

/** Help text, the note (why a control is disabled), then the error when there is one: all are read with the control. */
const describedBy = (field, errors, note) => [
    `${idOf(field)}-help`,
    note ? `${idOf(field)}-note` : null,
    errors[field] ? `${idOf(field)}-error` : null
].filter(Boolean).join(' ');

const controlProps = (field, errors, note) => ({
    id: idOf(field),
    'aria-describedby': describedBy(field, errors, note),
    'aria-invalid': errors[field] ? 'true' : undefined
});

const fieldPropTypes = {
    field: PropTypes.string.isRequired,
    form: PropTypes.object.isRequired,
    errors: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    // A save or reset is running: text controls are read only (they keep the focus; the form ignores other changes).
    isBusy: PropTypes.bool
};

/**
 * A setting: a native label bound to the control (clicking it focuses the control), the control, the help text, a
 * note and the error, with ids for aria-describedby. Moonstone's Field is not used: it does not bind its label. Help,
 * note and error are body text (Moonstone's caption is 12px), sized in rem by the stylesheet.
 */
const SettingField = ({field, errors, ratio, note, className, children}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={className || styles.field}>
            <Typography component="label" htmlFor={idOf(field)} weight="bold" className={styles.label}>
                {t(`settings.fields.${field}.label`)}
            </Typography>
            {children}
            <Typography id={`${idOf(field)}-help`} variant="body" className={styles.help}>{t(`settings.fields.${field}.help`)}</Typography>
            {note && <Typography id={`${idOf(field)}-note`} variant="body" weight="bold" className={styles.help}>{note}</Typography>}
            {errors[field] && (
                <Typography id={`${idOf(field)}-error`} variant="body" weight="bold" className={styles.error}>
                    {t(`settings.errors.${errors[field]}`, errors[field] === 'LOW_CONTRAST' ? values({ratio}) : undefined)}
                </Typography>
            )}
        </div>
    );
};

SettingField.propTypes = {
    field: PropTypes.string.isRequired,
    errors: PropTypes.object.isRequired,
    ratio: PropTypes.string,
    note: PropTypes.string,
    className: PropTypes.string,
    children: PropTypes.node.isRequired
};

export const SwitchField = ({field, form, errors, onChange}) => (
    <SettingField field={field} errors={errors} className={styles.switchField}>
        <Switch
            {...controlProps(field, errors)}
            className={styles.switch}
            checked={form[field]}
            onChange={(event, value, checked) => onChange(field, checked)}
        />
    </SettingField>
);

SwitchField.propTypes = fieldPropTypes;

export const TextField = ({field, form, errors, onChange, type, isDisabled, note, isBusy}) => {
    const props = {
        ...controlProps(field, errors, note),
        value: form[field],
        isDisabled,
        isReadOnly: Boolean(isBusy),
        onChange: event => onChange(field, event.target.value)
    };
    let control;
    if (type === 'textarea') {
        control = <Textarea {...props} rows={3}/>;
    } else if (type === 'number') {
        control = <Input {...props} type="number" min={MIN_HOURS} max={MAX_HOURS} step={1} inputMode="numeric"/>;
    } else {
        control = <Input {...props} type="text" spellCheck={false} autoComplete="off"/>;
    }

    return <SettingField field={field} errors={errors} note={note}>{control}</SettingField>;
};

TextField.propTypes = {
    ...fieldPropTypes,
    type: PropTypes.oneOf(['text', 'number', 'textarea']),
    isDisabled: PropTypes.bool,
    note: PropTypes.string
};

/**
 * A native select, styled with Moonstone's variables. Moonstone's Dropdown renders a role="listbox" named after the
 * selected value rather than after the field; a select keeps the field's name and the keyboard behaviour every
 * browser and screen reader knows.
 */
export const SelectField = ({field, form, errors, onChange, children}) => (
    <SettingField field={field} errors={errors}>
        <select {...controlProps(field, errors)} className={styles.select} value={form[field]} onChange={event => onChange(field, event.target.value)}>
            {children}
        </select>
    </SettingField>
);

SelectField.propTypes = {...fieldPropTypes, children: PropTypes.node.isRequired};

const DEFAULTS = {bannerBackgroundColor: DEFAULT_BANNER_BACKGROUND, bannerTextColor: DEFAULT_BANNER_TEXT};

/**
 * A native colour picker (Moonstone has none) next to a Moonstone Input for the colour code. While the code is not a
 * colour, the picker shows the default colour of the field (the preview says the code is not valid).
 */
export const ColorField = ({field, form, errors, onChange, ratio, isBusy}) => {
    const {t} = useTranslation(NS);
    const hex = normalizeHex(form[field]) || DEFAULTS[field];
    return (
        <SettingField field={field} errors={errors} ratio={ratio}>
            <div className={styles.color}>
                <label htmlFor={`${idOf(field)}-picker`} className={styles.srOnly}>{t(`settings.fields.${field}.picker`)}</label>
                <input
                    id={`${idOf(field)}-picker`}
                    type="color"
                    className={styles.picker}
                    value={hex.toLowerCase()}
                    onChange={event => onChange(field, event.target.value.toUpperCase())}
                />
                <Input
                    {...controlProps(field, errors)}
                    type="text"
                    value={form[field]}
                    spellCheck={false}
                    autoComplete="off"
                    maxLength={7}
                    isReadOnly={Boolean(isBusy)}
                    onChange={event => onChange(field, event.target.value)}
                />
            </div>
        </SettingField>
    );
};

ColorField.propTypes = {...fieldPropTypes, ratio: PropTypes.string};
