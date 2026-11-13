module.exports = {
    testEnvironment: 'jsdom',
    roots: ['<rootDir>/src/javascript'],
    testMatch: ['**/__tests__/**/*.test.js', '**/__tests__/**/*.test.jsx'],
    transform: {
        '^.+\\.[jt]sx?$': ['babel-jest', {
            // Isolated transform: do not read project babel config (webpack uses its own).
            configFile: false,
            babelrc: false,
            presets: [
                ['@babel/preset-env', {targets: {node: 'current'}}],
                ['@babel/preset-react', {runtime: 'automatic'}]
            ]
        }]
    },
    moduleNameMapper: {
        // SCSS / CSS Modules -> identity proxy so styles.foo === 'foo'
        '\\.(scss|css)$': 'identity-obj-proxy',
        // Mock the Jahia + 3rd-party modules that are not available under jsdom.
        '^@jahia/moonstone$': '<rootDir>/src/javascript/__mocks__/jahiaMoonstone.js',
        '^react-i18next$': '<rootDir>/src/javascript/__mocks__/reactI18next.js',
        '^@apollo/client$': '<rootDir>/src/javascript/__mocks__/apolloClient.js'
    },
    setupFilesAfterEnv: ['<rootDir>/src/javascript/__mocks__/setupTests.js'],
    collectCoverageFrom: [
        'src/javascript/**/*.{js,jsx}',
        '!src/javascript/__mocks__/**',
        '!src/javascript/index.js',
        '!src/javascript/init.js'
    ]
};
