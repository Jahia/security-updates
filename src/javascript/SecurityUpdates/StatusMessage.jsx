import React from 'react';
import PropTypes from 'prop-types';
import {Information, Typography, Warning} from '@jahia/moonstone';
import styles from './SecurityUpdates.scss';

/**
 * A status line in the style of the other Jahia administration pages: a Moonstone Information or Warning icon and
 * the message as Moonstone Typography. The icon is decorative; the text says what happened.
 */
export const StatusMessage = ({tone, role, children, ...rest}) => {
    const Icon = tone === 'warning' ? Warning : Information;
    return (
        <div className={styles.statusMessage} role={role} data-tone={tone} {...rest}>
            <Icon aria-hidden="true" size="default" className={tone === 'warning' ? styles.iconWarning : styles.iconInfo}/>
            <Typography component="span">{children}</Typography>
        </div>
    );
};

StatusMessage.propTypes = {
    tone: PropTypes.oneOf(['info', 'warning']).isRequired,
    role: PropTypes.string,
    children: PropTypes.node.isRequired
};
