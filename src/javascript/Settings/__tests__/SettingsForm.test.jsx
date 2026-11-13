import React from 'react';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {useMutation, useQuery} from '@apollo/client';
import {SettingsForm} from '../SettingsForm';

const settings = {
    enabled: true,
    refreshIntervalHours: 24,
    useForgeSettings: true,
    defaultStoreUrl: 'https://store.jahia.com',
    extraFeedUrls: '',
    bannerEnabled: true,
    bannerMinimumSeverity: 'HIGH',
    bannerIncludeOwnerReports: true,
    bannerBackgroundColor: '#1B1B1B',
    bannerTextColor: '#FFFFFF',
    bannerDismissPolicy: 'SESSION',
    bannerDismissHours: 24,
    contrastRatio: 17.22
};

describe('SettingsForm', () => {
    let update;
    let reset;
    let refetch;

    beforeEach(() => {
        refetch = jest.fn(() => Promise.resolve());
        useQuery.mockReturnValue({data: {securityUpdates: {settings}}, loading: false, error: undefined, refetch});
        update = jest.fn(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: true, errors: [], settings}}}}));
        reset = jest.fn(() => Promise.resolve({data: {securityUpdates: {resetSettings: {ok: true, errors: [], settings}}}}));
        useMutation.mockImplementation(doc => [String(doc).includes('resetSettings') ? reset : update, {loading: false}]);
    });

    const section = () => screen.getByRole('region', {name: 'settings.title'});

    test('shows every setting in a labelled control with its current value', () => {
        render(<SettingsForm/>);
        const form = within(section());

        expect(form.getByLabelText('settings.fields.enabled.label')).toBeChecked();
        expect(form.getByLabelText('settings.fields.refreshIntervalHours.label')).toHaveValue(24);
        expect(form.getByLabelText('settings.fields.useForgeSettings.label')).toBeChecked();
        expect(form.getByLabelText('settings.fields.defaultStoreUrl.label')).toHaveValue('https://store.jahia.com');
        expect(form.getByLabelText('settings.fields.extraFeedUrls.label')).toHaveValue('');
        expect(form.getByLabelText('settings.fields.bannerEnabled.label')).toBeChecked();
        expect(form.getByLabelText('settings.fields.bannerMinimumSeverity.label')).toHaveValue('HIGH');
        expect(form.getByLabelText('settings.fields.bannerIncludeOwnerReports.label')).toBeChecked();
        expect(form.getByLabelText('settings.fields.bannerBackgroundColor.label')).toHaveValue('#1B1B1B');
        expect(form.getByLabelText('settings.fields.bannerBackgroundColor.picker')).toHaveValue('#1b1b1b');
        expect(form.getByLabelText('settings.fields.bannerTextColor.label')).toHaveValue('#FFFFFF');
        expect(form.getByLabelText('settings.fields.bannerDismissPolicy.label')).toHaveValue('SESSION');
        expect(form.getByLabelText('settings.fields.bannerDismissHours.label')).toHaveValue(24);
    });

    test('every control has a help text', () => {
        render(<SettingsForm/>);
        const control = within(section()).getByLabelText('settings.fields.refreshIntervalHours.label');
        const help = document.getElementById(control.getAttribute('aria-describedby').split(' ')[0]);
        expect(help).toHaveTextContent('settings.fields.refreshIntervalHours.help');
    });

    test('the preview uses the chosen colours and shows the contrast ratio', () => {
        render(<SettingsForm/>);
        const background = within(section()).getByLabelText('settings.fields.bannerBackgroundColor.label');

        fireEvent.change(background, {target: {value: '#0b3d91'}});

        const preview = screen.getByTestId('security-updates-banner-preview');
        expect(preview.style.backgroundColor).toBe('rgb(11, 61, 145)');
        expect(preview.style.color).toBe('rgb(255, 255, 255)');
        expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('10.04');
        expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.pass');
    });

    test('the colour picker and the hex field stay in sync', () => {
        render(<SettingsForm/>);
        const form = within(section());

        fireEvent.change(form.getByLabelText('settings.fields.bannerTextColor.picker'), {target: {value: '#ffd400'}});

        expect(form.getByLabelText('settings.fields.bannerTextColor.label')).toHaveValue('#FFD400');
    });

    test('a low contrast is shown as failing in the preview', () => {
        render(<SettingsForm/>);
        const form = within(section());
        fireEvent.change(form.getByLabelText('settings.fields.bannerBackgroundColor.label'), {target: {value: '#888888'}});
        fireEvent.change(form.getByLabelText('settings.fields.bannerTextColor.label'), {target: {value: '#777777'}});

        expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('1.26');
        expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.fail');
    });

    test('Save sends every field, typed, and announces the result', async () => {
        render(<SettingsForm/>);
        fireEvent.change(within(section()).getByLabelText('settings.fields.refreshIntervalHours.label'), {target: {value: '6'}});

        fireEvent.click(screen.getByRole('button', {name: 'settings.save'}));

        await waitFor(() => expect(screen.getByTestId('security-updates-settings-status')).toHaveTextContent('settings.saved'));
        const input = update.mock.calls[0][0].variables.input;
        expect(input.refreshIntervalHours).toBe(6);
        expect(input.enabled).toBe(true);
        expect(Object.keys(input)).toHaveLength(12);
        expect(refetch).toHaveBeenCalled();
    });

    test('field errors from the server are shown, linked and announced; the first invalid field gets the focus', async () => {
        update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
            {field: 'refreshIntervalHours', code: 'OUT_OF_RANGE', message: 'Must be from 1 to 720 hours'},
            {field: 'bannerBackgroundColor', code: 'LOW_CONTRAST', message: 'The contrast ratio is 1.26:1'},
            {field: 'bannerTextColor', code: 'LOW_CONTRAST', message: 'The contrast ratio is 1.26:1'}
        ]}}}}));
        render(<SettingsForm/>);

        fireEvent.click(screen.getByRole('button', {name: 'settings.save'}));

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('settings.invalid');
        const interval = within(section()).getByLabelText('settings.fields.refreshIntervalHours.label');
        expect(interval).toHaveAttribute('aria-invalid', 'true');
        const errorId = interval.getAttribute('aria-describedby').split(' ').find(id => id.endsWith('-error'));
        expect(document.getElementById(errorId)).toHaveTextContent('settings.errors.OUT_OF_RANGE');
        expect(within(section()).getByLabelText('settings.fields.bannerTextColor.label')).toHaveAttribute('aria-invalid', 'true');
        expect(document.activeElement).toBe(interval);
    });

    test('a number field that is not a whole number is refused without calling the server', async () => {
        render(<SettingsForm/>);
        fireEvent.change(within(section()).getByLabelText('settings.fields.refreshIntervalHours.label'), {target: {value: ''}});

        fireEvent.click(screen.getByRole('button', {name: 'settings.save'}));

        expect(await screen.findByRole('alert')).toHaveTextContent('settings.invalid');
        expect(update).not.toHaveBeenCalled();
        expect(within(section()).getByLabelText('settings.fields.refreshIntervalHours.label')).toHaveAttribute('aria-invalid', 'true');
    });

    test('a write failure is shown', async () => {
        update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
            {field: null, code: 'WRITE_FAILED', message: 'The settings could not be saved'}
        ]}}}}));
        render(<SettingsForm/>);

        fireEvent.click(screen.getByRole('button', {name: 'settings.save'}));

        expect(await screen.findByRole('alert')).toHaveTextContent('settings.errors.WRITE_FAILED');
    });

    test('Reset to defaults calls the reset mutation and announces it', async () => {
        render(<SettingsForm/>);

        fireEvent.click(screen.getByRole('button', {name: 'settings.reset'}));

        await waitFor(() => expect(screen.getByTestId('security-updates-settings-status')).toHaveTextContent('settings.resetDone'));
        expect(reset).toHaveBeenCalled();
    });

    test('the dismissal duration is only offered with the HOURS policy', () => {
        render(<SettingsForm/>);
        const hours = within(section()).getByLabelText('settings.fields.bannerDismissHours.label');
        expect(hours).toBeDisabled();

        fireEvent.change(within(section()).getByLabelText('settings.fields.bannerDismissPolicy.label'), {target: {value: 'HOURS'}});

        expect(hours).not.toBeDisabled();
    });

    test('renders nothing until the settings are loaded', () => {
        useQuery.mockReturnValue({data: undefined, loading: true, error: undefined, refetch});
        const {container} = render(<SettingsForm/>);
        expect(container).toBeEmptyDOMElement();
    });

    test('is built from Moonstone Field, Switch, Input, Textarea and Button; selects keep their own name', () => {
        const {container} = render(<SettingsForm/>);
        const used = name => container.querySelectorAll(`[data-moonstone="${name}"]`).length;

        expect(used('Field')).toBe(12);
        expect(used('Switch')).toBe(4);
        expect(used('Input')).toBe(5);
        expect(used('Textarea')).toBe(1);
        expect(screen.getByRole('button', {name: 'settings.save'})).toHaveAttribute('data-moonstone', 'Button');
        expect(screen.getByRole('heading', {level: 2, name: 'settings.title'})).toBeInTheDocument();
        expect(screen.getByRole('combobox', {name: 'settings.fields.bannerDismissPolicy.label'})).toHaveValue('SESSION');
    });

    describe('contrast suggestions', () => {
        const fail = (background, text) => {
            render(<SettingsForm/>);
            const form = within(section());
            fireEvent.change(form.getByLabelText('settings.fields.bannerBackgroundColor.label'), {target: {value: background}});
            fireEvent.change(form.getByLabelText('settings.fields.bannerTextColor.label'), {target: {value: text}});
        };

        test('a passing pair shows no suggestion', () => {
            render(<SettingsForm/>);
            expect(screen.queryByTestId('security-updates-suggestions')).toBeNull();
        });

        test('a failing pair shows both suggestions, with a decorative swatch, the value and ratio as text, and named buttons', () => {
            fail('#1B1B1B', '#333333');

            const forText = screen.getByTestId('security-updates-suggestion-text');
            expect(forText.querySelector('[aria-hidden="true"]').style.backgroundColor).toBe('rgb(166, 166, 166)');
            expect(within(forText).getByText('settings.suggest.text')).toBeInTheDocument();
            expect(within(forText).getByRole('button', {name: 'settings.suggest.use_text'})).toBeInTheDocument();
            expect(screen.getByTestId('security-updates-suggestion-background')).toBeInTheDocument();
        });

        test('using a suggestion fills the field, passes the preview and is announced', () => {
            fail('#1B1B1B', '#333333');

            fireEvent.click(within(screen.getByTestId('security-updates-suggestion-text')).getByRole('button'));

            expect(within(section()).getByLabelText('settings.fields.bannerTextColor.label')).toHaveValue('#A6A6A6');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.pass');
            expect(screen.getByTestId('security-updates-settings-status')).toHaveTextContent('settings.suggest.appliedText');
            expect(screen.queryByTestId('security-updates-suggestions')).toBeNull();
        });

        test('using the background suggestion fills the background field', () => {
            fail('#1B1B1B', '#333333');

            fireEvent.click(within(screen.getByTestId('security-updates-suggestion-background')).getByRole('button'));

            expect(within(section()).getByLabelText('settings.fields.bannerBackgroundColor.label')).toHaveValue('#C1C1C1');
            expect(screen.getByTestId('security-updates-settings-status')).toHaveTextContent('settings.suggest.appliedBackground');
        });

        test('when no single change can reach 7:1, the default colours are offered', () => {
            fail('#777777', '#888888');

            expect(screen.getByText('settings.suggest.none')).toBeInTheDocument();
            fireEvent.click(screen.getByRole('button', {name: 'settings.suggest.useDefaults'}));

            expect(within(section()).getByLabelText('settings.fields.bannerBackgroundColor.label')).toHaveValue('#1B1B1B');
            expect(within(section()).getByLabelText('settings.fields.bannerTextColor.label')).toHaveValue('#FFFFFF');
            expect(screen.getByTestId('security-updates-settings-status')).toHaveTextContent('settings.suggest.appliedDefaults');
        });
    });
});
