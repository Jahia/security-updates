// Reading the answers of the settings mutations. The field names come from the server: only the known settings keys
// are used as field names (anything else is a general error), and the errors are kept in a null-prototype object.

import {FIELDS} from './settingsValues';

export const ERROR_CODES = ['MISSING',
    'INVALID_BOOLEAN',
    'INVALID_NUMBER',
    'OUT_OF_RANGE',
    'INVALID_URL',
    'NOT_HTTPS',
    'TOO_MANY',
    'INVALID_VALUE',
    'INVALID_COLOR',
    'LOW_CONTRAST',
    'WRITE_FAILED',
    'DENIED',
    'REQUEST_FAILED'];

const knownCode = code => (ERROR_CODES.includes(code) ? code : 'INVALID_VALUE');

/**
 * @param {Array<{field: string|null, code: string}>} errors the errors of an update or reset
 * @returns {{byField: object, general: string|null}} the first error code of each known field, and the first error
 *          that belongs to no known field
 */
export const splitErrors = errors => {
    const byField = Object.create(null);
    let general = null;
    (Array.isArray(errors) ? errors : []).forEach(error => {
        const code = knownCode(error?.code);
        if (FIELDS.includes(error?.field)) {
            byField[error.field] = byField[error.field] || code;
        } else {
            general = general || code;
        }
    });
    return {byField, general};
};

const PERMISSION_DENIED = /permission denied/i;

/** The error code of a request that failed: DENIED when the server refused it, REQUEST_FAILED otherwise. */
export const requestErrorCode = error => {
    const messages = [error?.message, ...(error?.graphQLErrors || []).map(e => e?.message)];
    return messages.some(message => PERMISSION_DENIED.test(String(message || ''))) ? 'DENIED' : 'REQUEST_FAILED';
};
