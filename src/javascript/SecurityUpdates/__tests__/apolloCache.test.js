// The real Apollo Client (not the double of the other tests), with a cache set up like the Jahia UI's: no type policy
// for the module's types (app-shell src/javascript/apollo/client.js only has policies for JCR types). It shows why the
// page's queries stay out of the cache, and checks what the page relies on instead.
import {GET_SECURITY_REPORT} from '../SecurityUpdates.gql';
import {GET_SETTINGS} from '../../Settings/Settings.gql';
import {QUERY_FETCH_POLICY} from '../../common/constants';

jest.mock('@apollo/client', () => jest.requireActual('@apollo/client/core'));

const {ApolloClient, ApolloLink, InMemoryCache, Observable} = jest.requireActual('@apollo/client/core');

const reportOf = checkedAt => ({
    __typename: 'SecurityUpdatesReport',
    status: 'OK',
    inProgress: false,
    stale: false,
    checkedAt,
    lastSuccess: checkedAt,
    nextCheckAt: null,
    jahiaVersion: '8.2.3.7',
    errors: [],
    findings: []
});

const settings = {
    __typename: 'SecurityUpdatesSettings',
    enabled: true,
    refreshIntervalHours: 24,
    useForgeSettings: true,
    defaultStoreUrl: 'https://store.jahia.com',
    extraFeedUrls: '',
    bannerEnabled: true,
    bannerMinimumSeverity: 'HIGH',
    bannerIncludeOwnerReports: true,
    bannerBackgroundColor: '#1B1B1B',
    bannerTextColor: '#FFFFFF',
    bannerDismissPolicy: 'SESSION',
    bannerDismissHours: 24,
    contrastRatio: 17.22,
    canEdit: true
};

const banner = {__typename: 'SecurityUpdatesBanner', visible: false, count: 0, minimumSeverity: 'HIGH'};

const clientAnswering = () => {
    let check = 0;
    const link = new ApolloLink(operation => Observable.of(operation.operationName === 'SecurityUpdatesSettings' ?
        {data: {securityUpdates: {__typename: 'SecurityUpdatesQuery', settings}}} :
        {data: {securityUpdates: {__typename: 'SecurityUpdatesQuery', report: reportOf(`2026-11-13T1${check++}:00:00Z`), banner}}}));
    return new ApolloClient({link, cache: new InMemoryCache()});
};

describe('Apollo cache and Query.securityUpdates', () => {
    test('written to the cache, the settings answer replaces the report under Query.securityUpdates (why the page does not cache)', async () => {
        const warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
        const client = clientAnswering();

        await client.query({query: GET_SECURITY_REPORT, fetchPolicy: 'network-only'});
        expect(client.cache.readQuery({query: GET_SECURITY_REPORT})).not.toBeNull();
        await client.query({query: GET_SETTINGS, fetchPolicy: 'network-only'});

        expect(client.cache.readQuery({query: GET_SECURITY_REPORT})).toBeNull();
        warn.mockRestore();
    });

    test('with the page fetch policy, nothing is cached, both queries keep their data, and refetch updates the watcher', async () => {
        expect(QUERY_FETCH_POLICY).toBe('no-cache');
        const client = clientAnswering();
        const results = [];
        const report = client.watchQuery({query: GET_SECURITY_REPORT, fetchPolicy: QUERY_FETCH_POLICY});
        const subscription = report.subscribe(result => results.push(result.data?.securityUpdates?.report?.checkedAt));
        await new Promise(resolve => {
            setTimeout(resolve, 0);
        });

        const loadedSettings = await client.query({query: GET_SETTINGS, fetchPolicy: QUERY_FETCH_POLICY});
        const refetched = await report.refetch();

        expect(loadedSettings.data.securityUpdates.settings.refreshIntervalHours).toBe(24);
        expect(refetched.data.securityUpdates.report.checkedAt).toBe('2026-11-13T11:00:00Z');
        expect(report.getCurrentResult().data.securityUpdates.report.checkedAt).toBe('2026-11-13T11:00:00Z');
        expect(results).toEqual(['2026-11-13T10:00:00Z', '2026-11-13T11:00:00Z']);
        expect(client.cache.extract().ROOT_QUERY?.securityUpdates).toBeUndefined();
        subscription.unsubscribe();
    });
});
