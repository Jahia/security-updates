import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Modal, ModalBody, ModalFooter, ModalHeader, Typography} from '@jahia/moonstone';
import styles from './SettingsForm.scss';
import {NS} from '../common/constants';

const TITLE_ID = 'security-updates-reset-title';
const BODY_ID = 'security-updates-reset-body';

/**
 * Confirmation of "Reset to defaults" (it cannot be undone), in a Moonstone Modal: focus kept inside, Escape and a
 * click outside cancel. Moonstone names the modal after itself, so the name is set to the header here, and the body
 * text describes it. Cancel comes first: the Modal focuses the first tabbable element when it opens (Floating UI's
 * initialFocus 0), so the safe action has the focus, and the destructive one, in the danger colour, comes second in
 * the tab order and on screen (ModalFooter keeps the DOM order, aligned to the end).
 */
export const ResetDialog = ({isOpen, onCancel, onConfirm}) => {
    const {t} = useTranslation(NS);
    return (
        <Modal isOpen={isOpen} size="small" aria-labelledby={TITLE_ID} aria-describedby={BODY_ID} onOpenChange={open => !open && onCancel()}>
            <div className={styles.dialog}>
                <ModalHeader id={TITLE_ID} title={t('settings.resetConfirm.title')}/>
                <ModalBody>
                    <Typography id={BODY_ID}>{t('settings.resetConfirm.body')}</Typography>
                </ModalBody>
                <ModalFooter className={styles.dialogFooter}>
                    <Button size="big" variant="outlined" className={styles.target} label={t('settings.resetConfirm.cancel')} onClick={onCancel}/>
                    <Button size="big" color="danger" className={styles.target} label={t('settings.resetConfirm.confirm')} onClick={onConfirm}/>
                </ModalFooter>
            </div>
        </Modal>
    );
};

ResetDialog.propTypes = {isOpen: PropTypes.bool.isRequired, onCancel: PropTypes.func.isRequired, onConfirm: PropTypes.func.isRequired};
