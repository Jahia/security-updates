// TEST-ONLY switch of the fixture store (see nginx.conf): makes the store at the path /flaky fail (HTTP 500) or work
// again, so that a spec can show a store that worked and then fails. The state is shared by the nginx workers and
// is "ok" each time nginx starts.

const FLAKY = 'flaky';
const STATES = ['ok', 'fail'];

/** The value of $flaky_failing: "1" when the flaky store must fail. */
function flakyFailing() {
    return ngx.shared.switches.get(FLAKY) === 'fail' ? '1' : '0';
}

/** POST /switch/flaky?state=ok|fail sets the state and answers it. */
function setFlaky(r) {
    const state = r.args.state;
    if (r.method !== 'POST' || STATES.indexOf(state) < 0) {
        r.return(400, 'POST /switch/flaky?state=ok|fail\n');
        return;
    }

    ngx.shared.switches.set(FLAKY, state);
    r.headersOut['Content-Type'] = 'text/plain';
    r.return(200, state + '\n');
}

export default {flakyFailing, setFlaky};
