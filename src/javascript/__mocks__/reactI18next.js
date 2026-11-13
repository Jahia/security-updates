// Mock react-i18next: t() echoes the key, followed by the interpolated values when there are some, so that tests can
// check both the key and what was passed. The same t is returned on every render, like the real hook.
const OPTIONS = ['interpolation', 'defaultValue', 'ns', 'lng'];

export const translate = (key, options) => {
    const vars = Object.entries(options || {}).filter(([name]) => !OPTIONS.includes(name));
    return vars.length === 0 ? key : `${key} {${vars.map(([name, value]) => `${name}=${value}`).join(', ')}}`;
};

const i18n = {language: 'en', changeLanguage: () => Promise.resolve()};
const result = {t: translate, i18n};

export const useTranslation = () => result;

export const initReactI18next = {type: '3rdParty', init: () => {}};
