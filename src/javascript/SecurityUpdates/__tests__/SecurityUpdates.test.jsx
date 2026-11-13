import React from 'react';
import {fireEvent, render, screen, within} from '@testing-library/react';
import {useMutation, useQuery} from '@apollo/client';
import {SecurityUpdates} from '../SecurityUpdates';

const advisory = (id, url, severity = 'HIGH') => ({id, title: `Title ${id}`, url, cves: ['CVE-2026-1234'], severity});

const finding = (component, severity, source = 'ADVISORY', extra = {}) => ({
    component,
    groupId: 'org.jahia.modules',
    installedVersion: '3.4.0',
    recommendedVersion: '3.10.0',
    compatibility: 'COMPATIBLE',
    requiredJahiaVersion: '8.2.0.0',
    severity,
    source,
    advisories: [advisory(`JSA-${component}`, 'https://academy.jahia.com/jsa')],
    ...extra
});

const report = (overrides = {}) => ({
    status: 'OK',
    checkedAt: '2026-11-13T10:00:00Z',
    lastSuccess: '2026-11-13T10:00:00Z',
    jahiaVersion: '8.2.2.1',
    errors: [],
    findings: [],
    ...overrides
});

const mockReport = r => useQuery.mockReturnValue({data: {securityUpdates: {report: r}}, loading: false, error: undefined});

describe('SecurityUpdates page', () => {
    let refresh;

    beforeEach(() => {
        refresh = jest.fn(() => Promise.resolve({data: {securityUpdates: {refresh: report({checkedAt: '2026-11-13T11:00:00Z'})}}}));
        useMutation.mockReturnValue([refresh, {loading: false}]);
        window.contextJsParameters = {contextPath: ''};
    });

    test('shows a loader while loading', () => {
        useQuery.mockReturnValue({data: undefined, loading: true, error: undefined});
        render(<SecurityUpdates/>);
        expect(screen.getByTestId('loader')).toBeInTheDocument();
        expect(screen.getByRole('status')).toHaveTextContent('page.loading');
    });

    test('reports a query error', () => {
        useQuery.mockReturnValue({data: undefined, loading: false, error: new Error('denied')});
        render(<SecurityUpdates/>);
        expect(screen.getByRole('alert')).toHaveTextContent('page.error');
    });

    test('an OK report without findings shows the explicit empty state', () => {
        mockReport(report());
        render(<SecurityUpdates/>);
        expect(screen.getByText('page.empty')).toBeInTheDocument();
        expect(screen.queryByRole('table')).toBeNull();
    });

    test.each(['UNAVAILABLE', 'NOT_CHECKED', 'DISABLED'])('a %s report is not shown as up to date', status => {
        mockReport(report({status, errors: [{storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', message: 'HTTP 404'}]}));
        render(<SecurityUpdates/>);
        expect(screen.queryByText('page.empty')).toBeNull();
        expect(screen.getByText(`page.status.${status}`)).toBeInTheDocument();
    });

    test('lists store errors with their kind and message', () => {
        mockReport(report({status: 'UNAVAILABLE', errors: [{storeUrl: 'https://store.jahia.com', kind: 'NOT_PUBLISHED', message: 'HTTP 404'}]}));
        render(<SecurityUpdates/>);
        const errors = screen.getByRole('list', {name: 'page.errors.title'});
        expect(within(errors).getByText('page.errorKind.NOT_PUBLISHED')).toBeInTheDocument();
        expect(within(errors).getByText(/HTTP 404/)).toBeInTheDocument();
        expect(within(errors).getByText(/store\.jahia\.com/)).toBeInTheDocument();
    });

    test('renders findings in a real table, most severe first, severity as text', () => {
        mockReport(report({findings: [finding('low-module', 'LOW'), finding('critical-module', 'CRITICAL'), finding('jahia-core', 'HIGH', 'ADVISORY', {compatibility: 'JAHIA_UPGRADE', groupId: null})]}));
        render(<SecurityUpdates/>);

        const table = screen.getByRole('table');
        expect(within(table).getByText('page.table.caption')).toBeInTheDocument();
        expect(within(table).getAllByRole('columnheader')).toHaveLength(7);
        const rows = within(table).getAllByRole('row').slice(1);
        expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent('critical-module');
        expect(within(rows[0]).getByText('page.severity.CRITICAL')).toBeInTheDocument();
        expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('jahia-core');
        expect(within(rows[1]).getByText('page.compatibility.JAHIA_UPGRADE')).toBeInTheDocument();
        expect(within(rows[2]).getByRole('rowheader')).toHaveTextContent('low-module');
    });

    test('advisory links open in a new tab without opener; non-https links are plain text', () => {
        mockReport(report({findings: [finding('m', 'HIGH', 'ADVISORY', {advisories: [
            advisory('JSA-SAFE', 'https://academy.jahia.com/jsa-safe'),
            advisory('JSA-UNSAFE', ['java', 'script:alert(1)'].join(''))
        ]})]}));
        render(<SecurityUpdates/>);

        const link = screen.getByRole('link', {name: /JSA-SAFE/});
        expect(link).toHaveAttribute('href', 'https://academy.jahia.com/jsa-safe');
        expect(link).toHaveAttribute('target', '_blank');
        expect(link.getAttribute('rel')).toContain('noopener');
        expect(screen.queryByRole('link', {name: /JSA-UNSAFE/})).toBeNull();
        expect(screen.getByText(/JSA-UNSAFE/)).toBeInTheDocument();
    });

    test('owner reports are hidden by default and labelled when shown', () => {
        mockReport(report({findings: [finding('advised', 'HIGH'), finding('owner-module', 'CRITICAL', 'OWNER_REPORTED')]}));
        render(<SecurityUpdates/>);

        expect(screen.queryByText('owner-module')).toBeNull();
        fireEvent.click(screen.getByLabelText('page.filter.all'));

        expect(screen.getByText('owner-module')).toBeInTheDocument();
        expect(screen.getAllByText('page.source.OWNER_REPORTED').length).toBeGreaterThan(0);
    });

    test('says when the filter hides every finding', () => {
        mockReport(report({findings: [finding('owner-module', 'CRITICAL', 'OWNER_REPORTED')]}));
        render(<SecurityUpdates/>);
        expect(screen.getByText('page.filteredEmpty')).toBeInTheDocument();
    });

    test('links to Manage modules', () => {
        mockReport(report({findings: [finding('m', 'HIGH')]}));
        render(<SecurityUpdates/>);
        expect(screen.getByRole('link', {name: 'page.manageModules'})).toHaveAttribute('href', '/jahia/administration/manageModules');
    });

    test('the Refresh button runs the refresh mutation and shows its report', async () => {
        mockReport(report());
        render(<SecurityUpdates/>);

        fireEvent.click(screen.getByRole('button', {name: 'page.refresh'}));

        expect(refresh).toHaveBeenCalled();
        expect(await screen.findByText('page.refreshed')).toBeInTheDocument();
    });

    test('a failed refresh is announced', async () => {
        refresh.mockImplementation(() => Promise.reject(new Error('boom')));
        mockReport(report());
        render(<SecurityUpdates/>);

        fireEvent.click(screen.getByRole('button', {name: 'page.refresh'}));

        expect(await screen.findByText('page.refreshError')).toBeInTheDocument();
    });
});
