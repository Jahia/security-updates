// Mock react-i18next: t() simply echoes the key so assertions can target keys.
export const useTranslation = () => ({
    t: key => key,
    i18n: {language: 'en', changeLanguage: () => Promise.resolve()}
});

export const initReactI18next = {type: '3rdParty', init: () => {}};
