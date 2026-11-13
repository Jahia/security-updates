const path = require('path');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const {CleanWebpackPlugin} = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const ModuleFederationPlugin = require('webpack/lib/container/ModuleFederationPlugin');
const moonstone = require('@jahia/moonstone/dist/rulesconfig-wp');
const {CycloneDxWebpackPlugin} = require('@cyclonedx/webpack-plugin');
const getModuleFederationConfig = require('@jahia/webpack-config/getModuleFederationConfig');
const packageJson = require('./package.json');

/** @type {import('@cyclonedx/webpack-plugin').CycloneDxWebpackPluginOptions} */
const cycloneDxWebpackPluginOptions = {
    specVersion: '1.4',
    rootComponentType: 'library',
    outputLocation: './bom'
};

module.exports = (env, argv) => {
    // Production unless asked otherwise (`yarn watch` asks for development).
    const mode = argv.mode || 'production';
    const production = mode === 'production';
    const config = {
        entry: {
            main: path.resolve(__dirname, 'src/javascript/index')
        },
        output: {
            path: path.resolve(__dirname, 'src/main/resources/javascript/apps/'),
            filename: 'security-updates.bundle.js',
            chunkFilename: '[name].jahia.[chunkhash:6].js'
        },
        resolve: {
            mainFields: ['module', 'main'],
            extensions: ['.mjs', '.js', '.jsx', '.json', '.scss'],
            fallback: {url: false}
        },
        module: {
            rules: [
                ...moonstone,
                {
                    test: /\.m?js$/,
                    type: 'javascript/auto'
                },
                {
                    test: /\.jsx?$/,
                    include: [path.join(__dirname, 'src')],
                    use: {
                        loader: 'babel-loader',
                        options: {
                            presets: [
                                ['@babel/preset-env', {
                                    modules: false,
                                    targets: {chrome: '60', edge: '44', firefox: '54', safari: '12'}
                                }],
                                // Same JSX runtime as the Jest transform (jest.config.js)
                                ['@babel/preset-react', {runtime: 'automatic'}]
                            ]
                        }
                    }
                },
                {
                    test: /\.scss$/i,
                    include: [path.join(__dirname, 'src')],
                    sideEffects: true,
                    use: [
                        'style-loader',
                        {
                            loader: 'css-loader',
                            options: {
                                modules: {
                                    mode: 'local',
                                    // css-loader 7 defaults to named exports; keep the default-export object used by `import styles from '*.scss'`
                                    namedExport: false,
                                    exportLocalsConvention: 'as-is',
                                    localIdentName: '[name]__[local]--[hash:base64:5]'
                                }
                            }
                        },
                        'sass-loader'
                    ]
                },
                {
                    test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
                    use: [{
                        loader: 'file-loader',
                        options: {
                            name: '[name].[ext]',
                            outputPath: 'fonts/'
                        }
                    }]
                }
            ]
        },
        plugins: [
            // Shared singletons (react, i18next 21, react-i18next 11, @apollo/client, @jahia/moonstone...) come from the
            // Jahia UI at runtime: their versions in package.json follow the ones the Jahia 8.2 UI provides.
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson, {
                library: {type: 'assign', name: 'appShell.remotes.securityUpdates'}
            })),
            new CleanWebpackPlugin({verbose: false}),
            new CopyWebpackPlugin({patterns: [{from: './package.json', to: ''}]})
        ],
        mode
    };

    if (production) {
        // The SBOM describes what is shipped: only the production build writes it.
        config.plugins.push(new CycloneDxWebpackPlugin(cycloneDxWebpackPluginOptions));
    }

    // Production source maps are written for debugging: the bundles do not reference them (no sourceMappingURL), and
    // the module jar leaves the *.map files out.
    config.devtool = production ? 'hidden-source-map' : 'eval-source-map';

    if (argv.analyze) {
        config.devtool = 'source-map';
        config.plugins.push(new BundleAnalyzerPlugin());
    }

    return config;
};
