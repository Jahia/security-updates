import {useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {CHANGED_EVENT} from '../common/constants';
import {pluralKey, values} from '../common/i18n';
import {checkOutcome} from './reportView';
import {statusText} from './ReportOverview';

export const POLL_INTERVAL_MS = 2000;
/** About three minutes of polling, then the page says how to see the results later. */
export const MAX_POLLS = 90;
/** Polls failing in a row before the page stops and says the check could not be followed (the server is gone). */
export const MAX_POLL_FAILURES = 3;

const reportOf = result => result?.data?.securityUpdates?.report;

/** What a finished check found, in one sentence: the number of components to update, or what the report means. */
const outcomeText = (report, t, language) => {
    const outcome = checkOutcome(report);
    if (outcome.count) {
        return t(`page.summary.${pluralKey(outcome.count, language)}`, values({n: outcome.count}));
    }

    return statusText(outcome, report, t, language);
};

/**
 * "Check now" and the polling of a running check. The refresh mutation only starts a check; the report query is then
 * polled until inProgress is false (the query stays the only source of the report shown). A check already running
 * when the page opens is followed the same way. One message per action: the outcome when the check ends (after the
 * page has the new report), a failure after MAX_POLL_FAILURES failed polls in a row, and, once per check, that a
 * check is already running when Check now is used again.
 *
 * @param {{report: object, refetch: function, refreshReport: function, t: function, language: string}} options
 * @returns {{isChecking: boolean, isUserCheck: boolean, hasFailed: boolean, isAlreadyRunning: boolean,
 *            announcement: string, onRefresh: function}}
 */
export const useCheck = ({report, refetch, refreshReport, t, language}) => {
    const [isChecking, setChecking] = useState(false);
    const [isUserCheck, setUserCheck] = useState(false);
    const [hasFailed, setFailed] = useState(false);
    const [isAlreadyRunning, setAlreadyRunning] = useState(false);
    const [announcement, setAnnouncement] = useState('');
    const busy = useRef(false);
    const mounted = useRef(true);
    const timer = useRef(null);
    const polls = useRef(0);
    const failures = useRef(0);
    const poll = useRef(() => undefined);

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            clearTimeout(timer.current);
        };
    }, []);

    const finish = useCallback(text => {
        busy.current = false;
        clearTimeout(timer.current);
        setChecking(false);
        setAlreadyRunning(false);
        setAnnouncement(text);
    }, []);

    const fail = useCallback(() => {
        setFailed(true);
        finish(t('page.refreshError'));
    }, [finish, t]);

    const completed = useCallback(next => {
        finish(t('page.refreshed', values({result: outcomeText(next, t, language)})));
        window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
    }, [finish, t, language]);

    // The latest refetch and callbacks, read by the timer: set after each render, never during it.
    useLayoutEffect(() => {
        const onPollFailed = () => {
            failures.current += 1;
            if (failures.current >= MAX_POLL_FAILURES) {
                fail();
            } else if (polls.current >= MAX_POLLS) {
                finish(t('page.refreshSlow'));
            } else {
                poll.current();
            }
        };

        const onPolled = next => {
            if (!next) {
                // An answer without a report is no better than no answer.
                onPollFailed();
                return;
            }

            failures.current = 0;
            if (!next.inProgress) {
                completed(next);
            } else if (polls.current >= MAX_POLLS) {
                finish(t('page.refreshSlow'));
            } else {
                poll.current();
            }
        };

        poll.current = () => {
            clearTimeout(timer.current);
            timer.current = setTimeout(() => {
                polls.current += 1;
                Promise.resolve()
                    .then(() => refetch())
                    .then(result => mounted.current && onPolled(reportOf(result)), () => mounted.current && onPollFailed());
            }, POLL_INTERVAL_MS);
        };
    });

    const start = useCallback(isUser => {
        busy.current = true;
        polls.current = 0;
        failures.current = 0;
        setChecking(true);
        setUserCheck(isUser);
        setFailed(false);
        setAlreadyRunning(false);
        setAnnouncement('');
    }, []);

    const isRunning = Boolean(report?.inProgress);
    useEffect(() => {
        if (isRunning && !busy.current) {
            start(false);
            poll.current();
        }
    }, [isRunning, start]);

    // The check ended before the mutation answered: the outcome is announced once the page shows the new report.
    const followEndedCheck = ended => {
        Promise.resolve()
            .then(() => refetch())
            .then(result => {
                if (mounted.current) {
                    completed(reportOf(result) || ended);
                }
            }, () => {
                if (mounted.current) {
                    window.dispatchEvent(new CustomEvent(CHANGED_EVENT));
                    fail();
                }
            });
    };

    const onRefresh = () => {
        // The button stays enabled (disabling it would drop the keyboard focus): a click during a check says, once,
        // that a check is already running.
        if (busy.current) {
            setAlreadyRunning(true);
            return;
        }

        start(true);
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
                    followEndedCheck(next);
                } else {
                    finish(t('page.notRefreshed'));
                }
            })
            .catch(() => {
                if (mounted.current) {
                    fail();
                }
            });
    };

    return {isChecking, isUserCheck, hasFailed, isAlreadyRunning, announcement, onRefresh};
};
