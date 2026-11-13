import '@testing-library/jest-dom';
import {toHaveNoViolations} from 'jest-axe';

expect.extend(toHaveNoViolations);

// The page and the banner read window.contextJsParameters.contextPath.
if (typeof window !== 'undefined' && !window.contextJsParameters) {
    window.contextJsParameters = {contextPath: ''};
}
