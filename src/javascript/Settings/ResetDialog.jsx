import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Modal, ModalBody, ModalFooter, ModalHeader, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {NS} from '../common/constants';

const TITLE_ID = 'security-updates-reset-title';

/**
 * Confirmation of "Reset to defaults" (it cannot be undone), in a Moonstone Modal: focus kept inside, Escape and a
 * click outside cancel. Moonstone names the modal after itself, so the name is set to the header here.
 */
export const ResetDialog = ({isOpen, onCancel, onConfirm}) => {
    const {t} = useTranslation(NS);
    return (
        <Modal isOpen={isOpen} size="small" aria-labelledby={TITLE_ID} onOpenChange={open => !open && onCancel()}>
            <div className={styles.dialog}>
                <ModalHeader id={TITLE_ID} title={t('settings.resetConfirm.title')}/>
                <ModalBody>
                    <Typography>{t('settings.resetConfirm.body')}</Typography>
                </ModalBody>
                <ModalFooter className={styles.dialogFooter}>
                    <Button size="big" color="accent" className={styles.target} label={t('settings.resetConfirm.confirm')} onClick={onConfirm}/>
                    <Button size="big" variant="outlined" className={styles.target} label={t('settings.resetConfirm.cancel')} onClick={onCancel}/>
                </ModalFooter>
            </div>
        </Modal>
    );
};

ResetDialog.propTypes = {isOpen: PropTypes.bool.isRequired, onCancel: PropTypes.func.isRequired, onConfirm: PropTypes.func.isRequired};
