import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {NS} from '../common/constants';

/** The terms of the page, in the order they appear. */
export const TERMS = ['advisory', 'cve', 'store', 'catalogue'];

/**
 * "What these terms mean": the words of the page explained in plain language (WCAG 3.1.3 and 3.1.5), in a native
 * details element, closed at first so that it does not lengthen the page for those who know them.
 */
export const Glossary = () => {
    const {t} = useTranslation(NS);
    return (
        <details className={styles.glossary} data-testid="security-updates-glossary">
            <summary className={styles.glossarySummary}>
                <Typography component="span" weight="bold">{t('page.glossary.title')}</Typography>
            </summary>
            <dl className={styles.glossaryList}>
                {TERMS.map(term => (
                    <div key={term} className={styles.glossaryEntry}>
                        <Typography component="dt" weight="bold">{t(`page.glossary.${term}.term`)}</Typography>
                        <Typography component="dd">{t(`page.glossary.${term}.definition`)}</Typography>
                    </div>
                ))}
            </dl>
        </details>
    );
};

export default Glossary;
