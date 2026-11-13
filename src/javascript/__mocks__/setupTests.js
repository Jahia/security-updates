import '@testing-library/jest-dom';

// The config panel reads window.contextJsParameters.contextPath at module load.
if (!window.contextJsParameters) {
    window.contextJsParameters = {contextPath: ''};
}
