import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Typography} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';
import {NS} from '../common/constants';
import {values} from '../common/i18n';
import {errorTexts, isNotPublishedOnly} from './reportView';
import {StatusMessage} from './StatusMessage';
import {storeErrorShape} from './propTypes';

const TITLE_ID = 'security-updates-errors-title';

/**
 * Stores that do not publish security data yet: the expected state before the data is published, explained by the
 * status line. Only the stores concerned are listed, as neutral information.
 */
const StoreDetails = ({errors}) => {
    const {t} = useTranslation(NS);
    return (
        <div className={styles.section}>
            <Typography component="h2" variant="heading" id={TITLE_ID}>{t('page.errors.infoTitle')}</Typography>
            <ul className={styles.list} aria-labelledby={TITLE_ID}>
                {errors.map((error, index) => (
                    // eslint-disable-next-line react/no-array-index-key
                    <li key={`${index}:${error.storeUrl}`}>
                        <StatusMessage tone="info">
                            <strong>{t('page.errorKind.NOT_PUBLISHED')}</strong>
                            {error.storeUrl && <span className={styles.block}>{t('page.errors.store', values({url: error.storeUrl}))}</span>}
                        </StatusMessage>
                    </li>
                ))}
            </ul>
        </div>
    );
};

StoreDetails.propTypes = {errors: PropTypes.arrayOf(storeErrorShape).isRequired};

/** The problems of the last check, localised from their kind and detail; the server's English text is a detail. */
export const StoreErrors = ({errors}) => {
    const {t} = useTranslation(NS);
    if (!errors || errors.length === 0) {
        return null;
    }

    if (isNotPublishedOnly(errors)) {
        return <StoreDetails errors={errors}/>;
    }

    return (
        <div className={styles.section}>
            <Typography component="h2" variant="heading" id={TITLE_ID}>{t('page.errors.title')}</Typography>
            <ul className={styles.list} aria-labelledby={TITLE_ID}>
                {errors.map((error, index) => {
                    const texts = errorTexts(error);
                    return (
                        // The same problem can be reported twice (two stores, same address): the position keeps keys unique.
                        // eslint-disable-next-line react/no-array-index-key
                        <li key={`${index}:${error.storeUrl}:${error.kind}`}>
                            <StatusMessage tone="warning">
                                <strong>{t(texts.title)}</strong>{' '}
                                <span>{t(texts.help)}</span>
                                {error.storeUrl && <span className={styles.block}>{t('page.errors.store', values({url: error.storeUrl}))}</span>}
                                {texts.isTechnicalShown && (
                                    <span lang="en" className={styles.block}>{t('page.errors.technical', values({message: error.message}))}</span>
                                )}
                            </StatusMessage>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
};

StoreErrors.propTypes = {errors: PropTypes.arrayOf(storeErrorShape)};
