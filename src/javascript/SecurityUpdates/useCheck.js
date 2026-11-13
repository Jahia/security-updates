import {useCallback, useEffect, useRef, useState} from 'react';
import {CHANGED_EVENT} from '../common/constants';

export const POLL_INTERVAL_MS = 2000;
/** About three minutes of polling, then the page says how to see the results later. */
export const MAX_POLLS = 90;

const reportOf = result => result?.data?.securityUpdates?.report;

/**
 * "Check now" and the polling of a running check. The refresh mutation only starts a check; the report query is then
 * polled until inProgress is false (the query stays the only source of the report shown). A check already running
 * when the page opens is followed the same way.
 *
 * @param {{report: object, refetch: function, refreshReport: function, t: function}} options
 * @returns {{isChecking: boolean, isUserCheck: boolean, hasFailed: boolean, announcement: string, onRefresh: function}}
 */
export const useCheck = ({report, refetch, refreshReport, t}) => {
    const [isChecking, setChecking] = useState(false);
    const [isUserCheck, setUserCheck] = useState(false);
    const [hasFailed, setFailed] = useState(false);
    const [announcement, setAnnouncement] = useState('');
    const busy = useRef(false);
    const mounted = useRef(true);
    const timer = useRef(null);
    const polls = useRef(0);
    const poll = useRef(() => undefined);

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            clearTimeout(timer.current);
        };
    }, []);

    const finish = useCallback(key => {
        busy.current = false;
        clearTimeout(timer.current);
        setChecking(false);
        setAnnouncement(key ? t(key) : '');
    }, [t]);

    const completed = useCallback(() => {
        finish('page.refreshed');
        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
    }, [finish]);

    poll.current = () => {
        clearTimeout(timer.current);
        timer.current = setTimeout(() => {
            polls.current += 1;
            Promise.resolve()
                .then(() => refetch())
                .then(reportOf, () => null)
                .then(next => {
                    if (!mounted.current) {
                        return;
                    }

                    if (next && !next.inProgress) {
                        completed();
                    } else if (polls.current >= MAX_POLLS) {
                        finish('page.refreshSlow');
                    } else {
                        poll.current();
                    }
                });
        }, POLL_INTERVAL_MS);
    };

    const start = useCallback(isUser => {
        busy.current = true;
        polls.current = 0;
        setChecking(true);
        setUserCheck(isUser);
    }, []);

    const isRunning = Boolean(report?.inProgress);
    useEffect(() => {
        if (isRunning && !busy.current) {
            start(false);
            poll.current();
        }
    }, [isRunning, start]);

    const onRefresh = () => {
        // The button stays enabled (disabling it would drop the keyboard focus): a click during a check is ignored.
        if (busy.current) {
            return;
        }

        start(true);
        setFailed(false);
        setAnnouncement(t('page.refreshing'));
        const previousCheck = report?.checkedAt;
        let started;
        try {
            started = Promise.resolve(refreshReport());
        } catch (failure) {
            started = Promise.reject(failure);
        }

        started
            .then(result => {
                if (!mounted.current) {
                    return;
                }

                const next = result?.data?.securityUpdates?.refresh;
                if (next?.inProgress) {
                    poll.current();
                } else if (next && next.checkedAt !== previousCheck) {
                    Promise.resolve().then(() => refetch()).catch(() => undefined);
                    completed();
                } else {
                    finish('page.notRefreshed');
                }
            })
            .catch(() => {
                if (mounted.current) {
                    setFailed(true);
                    finish('page.refreshError');
                }
            });
    };

    return {isChecking, isUserCheck, hasFailed, announcement, onRefresh};
};
