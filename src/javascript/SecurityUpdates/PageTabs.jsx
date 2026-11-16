import {useCallback, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Tab, TabItem} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {NS} from '../common/constants';

/** The tabs of the page, in order: the results of the last check, then the settings. */
export const TABS = ['results', 'settings'];
/** The address parameter that opens a tab (?tab=settings); the first tab has none. */
export const TAB_PARAM = 'tab';

export const tabId = tab => `security-updates-tab-${tab}`;
export const panelId = tab => `security-updates-panel-${tab}`;

/** @returns {string} the tab named by the address, else the first one */
export const tabFromSearch = search => {
    const value = new URLSearchParams(search || '').get(TAB_PARAM);
    return TABS.includes(value) ? value : TABS[0];
};

/**
 * The selected tab, read from the address once, and written back to it (replaceState: no new history entry, so the
 * Back button leaves the page as before), so that a link or a reload opens the same tab.
 *
 * @returns {[string, function(string): void]} the selected tab and the function that selects one
 */
export const usePageTab = () => {
    const [tab, setTab] = useState(() => tabFromSearch(window.location.search));
    const select = useCallback(next => {
        setTab(next);
        try {
            const url = new URL(window.location.href);
            if (next === TABS[0]) {
                url.searchParams.delete(TAB_PARAM);
            } else {
                url.searchParams.set(TAB_PARAM, next);
            }

            window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
        } catch (error) {
            // The address is a convenience: the tab is selected anyway.
            console.debug('[security-updates] tab not kept in the address', error);
        }
    }, []);
    return [tab, select];
};

const STEPS = {ArrowRight: 1, ArrowLeft: -1};

const targetIndex = (key, index) => {
    if (STEPS[key]) {
        return (index + STEPS[key] + TABS.length) % TABS.length;
    }

    if (key === 'Home') {
        return 0;
    }

    return key === 'End' ? TABS.length - 1 : null;
};

/**
 * The tab list (WAI-ARIA tabs pattern, automatic activation): only the selected tab is in the Tab order; the arrow
 * keys, Home and End select another tab and move the focus to it. Moonstone's Tab and TabItem give the tablist and tab
 * roles and aria-selected; the ids, aria-controls, tabIndex and keys are set here.
 */
export const PageTabs = ({selected, onSelect}) => {
    const {t} = useTranslation(NS);
    const onKeyDown = (event, index) => {
        const next = targetIndex(event.key, index);
        if (next === null) {
            return;
        }

        event.preventDefault();
        onSelect(TABS[next]);
        document.getElementById(tabId(TABS[next]))?.focus();
    };

    return (
        <Tab aria-label={t('page.tabs.label')} className={styles.tabs} data-testid="security-updates-tabs">
            {TABS.map((tab, index) => (
                <TabItem
                    key={tab}
                    id={tabId(tab)}
                    aria-controls={panelId(tab)}
                    className={styles.target}
                    label={t(`page.tabs.${tab}`)}
                    size="big"
                    isSelected={tab === selected}
                    tabIndex={tab === selected ? 0 : -1}
                    data-sel-role={`security-updates-tab-${tab}`}
                    onClick={() => onSelect(tab)}
                    onKeyDown={event => onKeyDown(event, index)}
                />
            ))}
        </Tab>
    );
};

PageTabs.propTypes = {
    selected: PropTypes.oneOf(TABS).isRequired,
    onSelect: PropTypes.func.isRequired
};

/** A tab panel, kept in the page when hidden, so that the settings being edited survive a change of tab. */
export const TabPanel = ({tab, selected, children}) => (
    <div
        role="tabpanel"
        id={panelId(tab)}
        aria-labelledby={tabId(tab)}
        hidden={tab !== selected}
        className={styles.panel}
        data-testid={panelId(tab)}
    >
        {children}
    </div>
);

TabPanel.propTypes = {
    tab: PropTypes.oneOf(TABS).isRequired,
    selected: PropTypes.oneOf(TABS).isRequired,
    children: PropTypes.node
};
