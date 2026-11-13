import {act, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {axe} from 'jest-axe';
import {useMutation, useQuery} from '@apollo/client';
import {SettingsForm} from '../SettingsForm';
import {CHANGED_EVENT} from '../../common/constants';

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
    contrastRatio: 17.22,
    canEdit: true
};

const loaded = (values = settings) => ({data: {securityUpdates: {settings: values}}, loading: false, error: undefined, refetch: jest.fn()});

describe('SettingsForm', () => {
    let update;
    let reset;

    beforeEach(() => {
        useQuery.mockReturnValue(loaded());
        update = jest.fn(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: true, errors: [], settings}}}}));
        reset = jest.fn(() => Promise.resolve({data: {securityUpdates: {resetSettings: {ok: true, errors: [], settings}}}}));
        useMutation.mockImplementation(doc => [String(doc).includes('resetSettings') ? reset : update, {loading: false}]);
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    const section = () => screen.getByRole('region', {name: 'settings.title'});
    const field = name => within(section()).getByLabelText(`settings.fields.${name}.label`);
    const status = () => screen.getByTestId('security-updates-settings-status');
    const save = () => screen.getByRole('button', {name: 'settings.save'});

    describe('loading', () => {
        test('says the settings are loading', () => {
            useQuery.mockReturnValue({data: undefined, loading: true, error: undefined});
            render(<SettingsForm/>);
            expect(within(section()).getByText('settings.loading')).toBeInTheDocument();
            expect(within(section()).getByTestId('loader')).toBeInTheDocument();
        });

        test('says when the settings could not be loaded', () => {
            useQuery.mockReturnValue({data: undefined, loading: false, error: new Error('boom')});
            render(<SettingsForm/>);
            expect(within(section()).getByRole('alert')).toHaveTextContent('settings.loadError');
            expect(screen.queryByRole('button', {name: 'settings.save'})).toBeNull();
        });

        test('the error state has no axe violation', async () => {
            useQuery.mockReturnValue({data: undefined, loading: false, error: new Error('boom')});
            const {container} = render(<SettingsForm/>);
            expect(await axe(container)).toHaveNoViolations();
        });
    });

    test('shows every setting in a control named by its own label, with its current value', () => {
        render(<SettingsForm/>);

        expect(field('enabled')).toBeChecked();
        expect(field('refreshIntervalHours')).toHaveValue(24);
        expect(field('useForgeSettings')).toBeChecked();
        expect(field('defaultStoreUrl')).toHaveValue('https://store.jahia.com');
        expect(field('extraFeedUrls')).toHaveValue('');
        expect(field('bannerEnabled')).toBeChecked();
        expect(field('bannerMinimumSeverity')).toHaveValue('HIGH');
        expect(field('bannerIncludeOwnerReports')).toBeChecked();
        expect(field('bannerBackgroundColor')).toHaveValue('#1B1B1B');
        expect(within(section()).getByLabelText('settings.fields.bannerBackgroundColor.picker')).toHaveValue('#1b1b1b');
        expect(field('bannerTextColor')).toHaveValue('#FFFFFF');
        expect(field('bannerDismissPolicy')).toHaveValue('SESSION');
        expect(field('bannerDismissHours')).toHaveValue(24);
    });

    test('the settings are read outside the Apollo cache (see apolloCache.test.js)', () => {
        render(<SettingsForm/>);
        expect(useQuery).toHaveBeenCalledWith(expect.anything(), {fetchPolicy: 'no-cache'});
    });

    test('labels are native labels: no control is named by aria-label only', () => {
        const {container} = render(<SettingsForm/>);
        container.querySelectorAll('input, select, textarea').forEach(control => {
            expect(control).not.toHaveAttribute('aria-label');
            expect(container.querySelector(`label[for="${control.id}"]`)).not.toBeNull();
        });
    });

    test('every control has a help text', () => {
        render(<SettingsForm/>);
        const help = document.getElementById(field('refreshIntervalHours').getAttribute('aria-describedby').split(' ')[0]);
        expect(help).toHaveTextContent('settings.fields.refreshIntervalHours.help');
    });

    describe('read only', () => {
        test('a user who cannot change the settings sees them, disabled, with an explanation and no buttons', () => {
            useQuery.mockReturnValue(loaded({...settings, canEdit: false}));
            render(<SettingsForm/>);

            expect(within(section()).getByText('settings.readOnly')).toBeInTheDocument();
            expect(field('refreshIntervalHours')).toBeDisabled();
            expect(field('enabled')).toBeDisabled();
            expect(field('bannerMinimumSeverity')).toBeDisabled();
            expect(screen.queryByRole('button', {name: 'settings.save'})).toBeNull();
            expect(screen.queryByRole('button', {name: 'settings.reset'})).toBeNull();
        });
    });

    describe('preview', () => {
        test('uses the chosen colours and shows the contrast ratio', () => {
            render(<SettingsForm/>);

            fireEvent.change(field('bannerBackgroundColor'), {target: {value: '#0b3d91'}});

            const preview = screen.getByTestId('security-updates-banner-preview');
            expect(preview.style.backgroundColor).toBe('rgb(11, 61, 145)');
            expect(preview.style.color).toBe('rgb(255, 255, 255)');
            expect(preview).toHaveTextContent('banner.message.other {n=3, threshold=banner.threshold.HIGH}');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('10.04');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.pass');
        });

        test('a failing pair is still previewed, labelled as one that cannot be saved', () => {
            render(<SettingsForm/>);
            fireEvent.change(field('bannerBackgroundColor'), {target: {value: '#888888'}});
            fireEvent.change(field('bannerTextColor'), {target: {value: '#777777'}});

            const preview = screen.getByTestId('security-updates-banner-preview');
            expect(preview.style.backgroundColor).toBe('rgb(136, 136, 136)');
            expect(preview.style.color).toBe('rgb(119, 119, 119)');
            expect(screen.getByText('settings.preview.failLabel')).toBeInTheDocument();
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('1.26');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.fail');
        });

        test('an invalid colour keeps the picker on the default colour of its field, not black', () => {
            render(<SettingsForm/>);
            fireEvent.change(field('bannerTextColor'), {target: {value: '#FFF'}});

            expect(within(section()).getByLabelText('settings.fields.bannerTextColor.picker')).toHaveValue('#ffffff');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.invalid');
        });

        test('the colour picker and the text field stay in sync', () => {
            render(<SettingsForm/>);

            fireEvent.change(within(section()).getByLabelText('settings.fields.bannerTextColor.picker'), {target: {value: '#ffd400'}});

            expect(field('bannerTextColor')).toHaveValue('#FFD400');
        });

        test('the verdict is not live on every key; only a change between pass and fail is announced, after a pause', () => {
            jest.useFakeTimers();
            render(<SettingsForm/>);
            const live = screen.getByTestId('security-updates-contrast-status');
            expect(screen.getByTestId('security-updates-contrast')).not.toHaveAttribute('aria-live');

            fireEvent.change(field('bannerTextColor'), {target: {value: '#33'}});
            fireEvent.change(field('bannerTextColor'), {target: {value: '#333'}});
            fireEvent.change(field('bannerTextColor'), {target: {value: '#333333'}});
            expect(live).toBeEmptyDOMElement();
            act(() => jest.advanceTimersByTime(1000));
            expect(live).toHaveTextContent('settings.preview.announceFail');

            fireEvent.change(field('bannerTextColor'), {target: {value: '#333334'}});
            act(() => jest.advanceTimersByTime(1000));
            expect(live).toHaveTextContent('settings.preview.announceFail');

            fireEvent.change(field('bannerTextColor'), {target: {value: '#FFFFFF'}});
            act(() => jest.advanceTimersByTime(1000));
            expect(live).toHaveTextContent('settings.preview.announcePass');
        });
    });

    describe('save', () => {
        test('sends every field, typed, shows the saved values without asking the server again, and tells the banner', async () => {
            const changed = jest.fn();
            window.addEventListener(CHANGED_EVENT, changed);
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {
                ok: true, errors: [], settings: {...settings, refreshIntervalHours: 6, defaultStoreUrl: 'https://saved.example'}
            }}}}));
            const query = loaded();
            useQuery.mockReturnValue(query);
            render(<SettingsForm/>);
            fireEvent.change(field('refreshIntervalHours'), {target: {value: '6'}});

            fireEvent.click(save());

            // The check settings changed: the schedule restarts.
            await waitFor(() => expect(status().textContent).toBe('settings.saved'));
            const input = update.mock.calls[0][0].variables.input;
            expect(input.refreshIntervalHours).toBe(6);
            expect(input.enabled).toBe(true);
            expect(Object.keys(input)).toHaveLength(12);
            expect(field('defaultStoreUrl')).toHaveValue('https://saved.example');
            expect(query.refetch).not.toHaveBeenCalled();
            expect(changed).toHaveBeenCalledTimes(1);
            window.removeEventListener(CHANGED_EVENT, changed);
        });

        test('Save is the submit button of the form: submitting with Enter saves once', async () => {
            render(<SettingsForm/>);
            expect(save()).toHaveAttribute('type', 'submit');

            fireEvent.submit(field('refreshIntervalHours').closest('form'));
            fireEvent.submit(field('refreshIntervalHours').closest('form'));

            await waitFor(() => expect(status()).toHaveTextContent('settings.saved'));
            expect(update).toHaveBeenCalledTimes(1);
        });

        test.each([
            ['only banner settings changed: they apply at once', {bannerMinimumSeverity: 'CRITICAL', bannerTextColor: '#EEEEEE'}, 'settings.savedNow'],
            ['a check setting changed: the next check runs in about one minute', {extraFeedUrls: 'https://feed.example/a.json'}, 'settings.saved'],
            ['checks turned on again', {enabled: true}, 'settings.saved'],
            ['checks turned off: nothing runs, the change applies at once', {enabled: false, refreshIntervalHours: 6}, 'settings.savedNow']
        ])('the saved message depends on what changed: %s', async (name, changes, message) => {
            const before = changes.enabled === true ? {...settings, enabled: false} : settings;
            useQuery.mockReturnValue(loaded(before));
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: true, errors: [], settings: {...settings, ...changes}}}}}));
            render(<SettingsForm/>);

            fireEvent.click(save());

            await waitFor(() => expect(status().textContent).toBe(message));
        });

        test('a second save compares with what the first one saved', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: true, errors: [], settings: {...settings, refreshIntervalHours: 6}}}}}));
            render(<SettingsForm/>);
            fireEvent.click(save());
            await waitFor(() => expect(status().textContent).toBe('settings.saved'));

            fireEvent.click(save());

            await waitFor(() => expect(status().textContent).toBe('settings.savedNow'));
        });

        test('a new answer of the settings query does not overwrite the edits in progress', () => {
            const {rerender} = render(<SettingsForm/>);
            fireEvent.change(field('refreshIntervalHours'), {target: {value: '7'}});

            useQuery.mockReturnValue(loaded({...settings, refreshIntervalHours: 48}));
            rerender(<SettingsForm/>);

            expect(field('refreshIntervalHours')).toHaveValue(7);
        });

        test('field errors from the server are shown, linked and announced; the first invalid field gets the focus', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: 'refreshIntervalHours', code: 'OUT_OF_RANGE', message: 'Must be from 1 to 720 hours'},
                {field: 'extraFeedUrls', code: 'NOT_HTTPS', message: 'https only'},
                {field: 'bannerBackgroundColor', code: 'LOW_CONTRAST', message: 'The contrast ratio is 1.26:1'},
                {field: 'bannerTextColor', code: 'LOW_CONTRAST', message: 'The contrast ratio is 1.26:1'}
            ]}}}}));
            render(<SettingsForm/>);

            fireEvent.click(save());

            const alert = await screen.findByRole('alert');
            expect(alert).toHaveTextContent('settings.invalid');
            const interval = field('refreshIntervalHours');
            expect(interval).toHaveAttribute('aria-invalid', 'true');
            const errorId = interval.getAttribute('aria-describedby').split(' ').find(id => id.endsWith('-error'));
            expect(document.getElementById(errorId)).toHaveTextContent('settings.errors.OUT_OF_RANGE');
            expect(screen.getByText('settings.errors.NOT_HTTPS')).toBeInTheDocument();
            expect(field('bannerTextColor')).toHaveAttribute('aria-invalid', 'true');
            expect(document.activeElement).toBe(interval);
        });

        test.each(['__proto__', 'constructor', 'toString', 'unknownField'])('an error on the unknown field "%s" pollutes nothing and is shown as a general error', async name => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: name, code: 'INVALID_VALUE', message: 'x'}
            ]}}}}));
            render(<SettingsForm/>);

            fireEvent.click(save());

            expect(await screen.findByRole('alert')).toHaveTextContent('settings.errors.INVALID_VALUE');
            expect({}.code).toBeUndefined();
            expect(Object.prototype.code).toBeUndefined();
            document.querySelectorAll('input, select, textarea').forEach(control => expect(control).not.toHaveAttribute('aria-invalid'));
        });

        test('editing a field clears its error at once, and the focus stays in it', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: 'refreshIntervalHours', code: 'OUT_OF_RANGE', message: 'x'},
                {field: 'extraFeedUrls', code: 'NOT_HTTPS', message: 'x'}
            ]}}}}));
            render(<SettingsForm/>);
            fireEvent.click(save());
            await screen.findByRole('alert');
            const interval = field('refreshIntervalHours');
            expect(document.activeElement).toBe(interval);

            fireEvent.change(interval, {target: {value: '12'}});

            expect(interval).not.toHaveAttribute('aria-invalid');
            expect(interval.getAttribute('aria-describedby')).toBe('security-updates-setting-refreshIntervalHours-help');
            expect(document.getElementById('security-updates-setting-refreshIntervalHours-error')).toBeNull();
            expect(document.activeElement).toBe(interval);
            expect(field('extraFeedUrls')).toHaveAttribute('aria-invalid', 'true');
        });

        test('editing one colour clears the low contrast error of both, not another error of the other colour', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: 'bannerBackgroundColor', code: 'LOW_CONTRAST', message: 'x'},
                {field: 'bannerTextColor', code: 'LOW_CONTRAST', message: 'x'}
            ]}}}}));
            render(<SettingsForm/>);
            fireEvent.click(save());
            await screen.findByRole('alert');

            fireEvent.change(field('bannerTextColor'), {target: {value: '#EEEEEE'}});

            expect(field('bannerTextColor')).not.toHaveAttribute('aria-invalid');
            expect(field('bannerBackgroundColor')).not.toHaveAttribute('aria-invalid');
        });

        test('a colour that is not valid keeps its error when the other colour is edited', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: 'bannerBackgroundColor', code: 'INVALID_COLOR', message: 'x'},
                {field: 'bannerTextColor', code: 'LOW_CONTRAST', message: 'x'}
            ]}}}}));
            render(<SettingsForm/>);
            fireEvent.click(save());
            await screen.findByRole('alert');

            fireEvent.change(field('bannerTextColor'), {target: {value: '#EEEEEE'}});

            expect(field('bannerTextColor')).not.toHaveAttribute('aria-invalid');
            expect(field('bannerBackgroundColor')).toHaveAttribute('aria-invalid', 'true');
        });

        test('help texts and errors are body text, not the 12px caption', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: 'refreshIntervalHours', code: 'OUT_OF_RANGE', message: 'x'}
            ]}}}}));
            render(<SettingsForm/>);
            fireEvent.click(save());
            await screen.findByRole('alert');

            expect(document.getElementById('security-updates-setting-refreshIntervalHours-help')).toHaveAttribute('data-variant', 'body');
            expect(document.getElementById('security-updates-setting-refreshIntervalHours-error')).toHaveAttribute('data-variant', 'body');
            expect(screen.getByText('settings.intro')).not.toHaveAttribute('data-variant', 'caption');
        });

        test('a number field that is not a whole number is refused without calling the server', async () => {
            render(<SettingsForm/>);
            fireEvent.change(field('refreshIntervalHours'), {target: {value: ''}});

            fireEvent.click(save());

            expect(await screen.findByRole('alert')).toHaveTextContent('settings.invalid');
            expect(update).not.toHaveBeenCalled();
            expect(field('refreshIntervalHours')).toHaveAttribute('aria-invalid', 'true');
        });

        test('a write failure on the server is shown', async () => {
            update.mockImplementation(() => Promise.resolve({data: {securityUpdates: {updateSettings: {ok: false, settings: null, errors: [
                {field: null, code: 'WRITE_FAILED', message: 'The settings could not be saved'}
            ]}}}}));
            render(<SettingsForm/>);

            fireEvent.click(save());

            expect(await screen.findByRole('alert')).toHaveTextContent('settings.errors.WRITE_FAILED');
        });

        test('a request that does not reach the server is not called a write failure', async () => {
            update.mockImplementation(() => Promise.reject(new Error('Failed to fetch')));
            render(<SettingsForm/>);

            fireEvent.click(save());

            expect(await screen.findByRole('alert')).toHaveTextContent('settings.errors.REQUEST_FAILED');
        });

        test('a permission denied by the server says so', async () => {
            update.mockImplementation(() => Promise.reject(Object.assign(new Error('Permission denied'), {graphQLErrors: [{message: 'Permission denied'}]})));
            render(<SettingsForm/>);

            fireEvent.click(save());

            expect(await screen.findByRole('alert')).toHaveTextContent('settings.errors.DENIED');
        });
    });

    describe('reset', () => {
        test('asks for confirmation in a dialog; cancelling changes nothing', () => {
            render(<SettingsForm/>);

            fireEvent.click(screen.getByRole('button', {name: 'settings.reset'}));

            const dialog = screen.getByRole('dialog', {name: 'settings.resetConfirm.title'});
            expect(dialog).toHaveAttribute('aria-modal', 'true');
            expect(within(dialog).getByText('settings.resetConfirm.body')).toBeInTheDocument();
            fireEvent.click(within(dialog).getByRole('button', {name: 'settings.resetConfirm.cancel'}));
            expect(screen.queryByRole('dialog')).toBeNull();
            expect(reset).not.toHaveBeenCalled();
        });

        test('Escape closes the dialog without resetting', () => {
            render(<SettingsForm/>);
            fireEvent.click(screen.getByRole('button', {name: 'settings.reset'}));

            fireEvent.keyDown(screen.getByRole('dialog'), {key: 'Escape'});

            expect(screen.queryByRole('dialog')).toBeNull();
            expect(reset).not.toHaveBeenCalled();
        });

        test('confirming resets, shows the default values returned by the server, and announces it', async () => {
            reset.mockImplementation(() => Promise.resolve({data: {securityUpdates: {resetSettings: {
                ok: true, errors: [], settings: {...settings, refreshIntervalHours: 24, extraFeedUrls: ''}
            }}}}));
            render(<SettingsForm/>);
            fireEvent.change(field('refreshIntervalHours'), {target: {value: '5'}});
            fireEvent.click(screen.getByRole('button', {name: 'settings.reset'}));

            fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'settings.resetConfirm.confirm'}));

            await waitFor(() => expect(status().textContent).toBe('settings.resetDoneNow'));
            expect(reset).toHaveBeenCalledTimes(1);
            expect(field('refreshIntervalHours')).toHaveValue(24);
            expect(screen.queryByRole('dialog')).toBeNull();
        });
    });

    test('a reset that changes the check settings says when the next check runs', async () => {
        useQuery.mockReturnValue(loaded({...settings, refreshIntervalHours: 12}));
        render(<SettingsForm/>);
        fireEvent.click(screen.getByRole('button', {name: 'settings.reset'}));

        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'settings.resetConfirm.confirm'}));

        await waitFor(() => expect(status().textContent).toBe('settings.resetDone'));
    });

    test('the dismissal duration is only offered with the HOURS policy, and says so while it is disabled', () => {
        render(<SettingsForm/>);
        const hours = field('bannerDismissHours');
        expect(hours).toBeDisabled();
        const note = document.getElementById('security-updates-setting-bannerDismissHours-note');
        expect(note).toHaveTextContent('settings.fields.bannerDismissHours.disabled');
        expect(hours.getAttribute('aria-describedby').split(' ')).toContain(note.id);

        fireEvent.change(field('bannerDismissPolicy'), {target: {value: 'HOURS'}});

        expect(field('bannerDismissHours')).not.toBeDisabled();
        expect(document.getElementById('security-updates-setting-bannerDismissHours-note')).toBeNull();
    });

    test('is built from Moonstone Switch, Input, Textarea and Button; selects are native', () => {
        const {container} = render(<SettingsForm/>);
        const used = name => container.querySelectorAll(`[data-moonstone="${name}"]`).length;

        expect(used('Switch')).toBe(4);
        expect(used('Input')).toBe(5);
        expect(used('Textarea')).toBe(1);
        expect(save()).toHaveAttribute('data-moonstone', 'Button');
        expect(screen.getByRole('heading', {level: 2, name: 'settings.title'})).toBeInTheDocument();
        expect(screen.getByRole('combobox', {name: 'settings.fields.bannerDismissPolicy.label'})).toHaveValue('SESSION');
    });

    test('has no axe violation', async () => {
        const {container} = render(<SettingsForm/>);
        expect(await axe(container)).toHaveNoViolations();
    });

    describe('contrast suggestions', () => {
        const fail = (background, text) => {
            render(<SettingsForm/>);
            fireEvent.change(field('bannerBackgroundColor'), {target: {value: background}});
            fireEvent.change(field('bannerTextColor'), {target: {value: text}});
        };

        test('a passing pair shows no suggestion', () => {
            render(<SettingsForm/>);
            expect(screen.queryByTestId('security-updates-suggestions')).toBeNull();
        });

        test('a failing pair shows both suggestions, with a decorative swatch, the value and ratio as text, and named buttons', () => {
            fail('#1B1B1B', '#333333');

            const forText = screen.getByTestId('security-updates-suggestion-text');
            expect(forText.querySelector('[aria-hidden="true"]').style.backgroundColor).toBe('rgb(166, 166, 166)');
            expect(within(forText).getByText('settings.suggest.text {value=#A6A6A6, ratio=7.07}')).toBeInTheDocument();
            expect(within(forText).getByRole('button', {name: 'settings.suggest.use_text {value=#A6A6A6}'})).toBeInTheDocument();
            expect(screen.getByTestId('security-updates-suggestion-background')).toBeInTheDocument();
        });

        test('using a suggestion fills the field, passes the preview and is announced', () => {
            fail('#1B1B1B', '#333333');

            fireEvent.click(within(screen.getByTestId('security-updates-suggestion-text')).getByRole('button'));

            expect(field('bannerTextColor')).toHaveValue('#A6A6A6');
            expect(screen.getByTestId('security-updates-contrast')).toHaveTextContent('settings.preview.pass');
            expect(status()).toHaveTextContent('settings.suggest.appliedText');
            expect(screen.queryByTestId('security-updates-suggestions')).toBeNull();
            // The button is gone: the focus is on the field it changed.
            expect(document.activeElement).toBe(field('bannerTextColor'));
        });

        test('using the background suggestion fills the background field', () => {
            fail('#1B1B1B', '#333333');

            fireEvent.click(within(screen.getByTestId('security-updates-suggestion-background')).getByRole('button'));

            expect(field('bannerBackgroundColor')).toHaveValue('#C1C1C1');
            expect(status()).toHaveTextContent('settings.suggest.appliedBackground');
            expect(document.activeElement).toBe(field('bannerBackgroundColor'));
        });

        test('when no single change can reach 7:1, the default colours are offered', () => {
            fail('#777777', '#888888');

            expect(screen.getByText('settings.suggest.none')).toBeInTheDocument();
            fireEvent.click(screen.getByRole('button', {name: /^settings\.suggest\.useDefaults/}));

            expect(field('bannerBackgroundColor')).toHaveValue('#1B1B1B');
            expect(field('bannerTextColor')).toHaveValue('#FFFFFF');
            expect(status()).toHaveTextContent('settings.suggest.appliedDefaults');
            expect(document.activeElement).toBe(field('bannerBackgroundColor'));
        });
    });
});
