import {requestErrorCode, splitErrors} from '../settingsResult';

describe('settings results', () => {
    test('splits field errors from general ones, first error per field', () => {
        const {byField, general} = splitErrors([
            {field: 'refreshIntervalHours', code: 'OUT_OF_RANGE'},
            {field: 'refreshIntervalHours', code: 'MISSING'},
            {field: null, code: 'WRITE_FAILED'}
        ]);
        expect({...byField}).toEqual({refreshIntervalHours: 'OUT_OF_RANGE'});
        expect(general).toBe('WRITE_FAILED');
    });

    test.each(['__proto__', 'constructor', 'hasOwnProperty'])('a field named %s is a general error and pollutes nothing', field => {
        const {byField, general} = splitErrors([{field, code: 'INVALID_VALUE'}]);
        expect(Object.keys(byField)).toEqual([]);
        expect(Object.getPrototypeOf(byField)).toBeNull();
        expect(general).toBe('INVALID_VALUE');
        expect({}.INVALID_VALUE).toBeUndefined();
    });

    test('an unknown code reads as an invalid value; no errors at all is nothing', () => {
        expect(splitErrors([{field: 'enabled', code: 'SOMETHING_NEW'}]).byField.enabled).toBe('INVALID_VALUE');
        expect(splitErrors(undefined)).toEqual({byField: expect.any(Object), general: null});
        expect(splitErrors([null]).general).toBe('INVALID_VALUE');
    });

    test('a refused request is DENIED, any other failure REQUEST_FAILED', () => {
        expect(requestErrorCode({graphQLErrors: [{message: 'Permission denied'}]})).toBe('DENIED');
        expect(requestErrorCode(new Error('GraphQL error: Permission denied'))).toBe('DENIED');
        expect(requestErrorCode(new Error('Failed to fetch'))).toBe('REQUEST_FAILED');
        expect(requestErrorCode(undefined)).toBe('REQUEST_FAILED');
    });
});
