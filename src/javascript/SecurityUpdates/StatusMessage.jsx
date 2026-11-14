import PropTypes from 'prop-types';
import {Information, Typography, Warning} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';

/**
 * A status line in the style of the other Jahia administration pages: a Moonstone Information or Warning icon and
 * the message as Moonstone Typography. The icon is decorative; the text says what happened.
 */
export const StatusMessage = ({tone, children, ...rest}) => {
    const Icon = tone === 'warning' ? Warning : Information;
    return (
        <div className={styles.statusMessage} data-tone={tone} {...rest}>
            <Icon aria-hidden="true" size="default" className={tone === 'warning' ? styles.iconWarning : styles.iconInfo}/>
            <Typography component="span" className={styles.text}>{children}</Typography>
        </div>
    );
};

StatusMessage.propTypes = {
    tone: PropTypes.oneOf(['info', 'warning']).isRequired,
    children: PropTypes.node.isRequired
};
