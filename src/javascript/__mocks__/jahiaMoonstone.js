// Lightweight test doubles for the @jahia/moonstone components used by the module.
import React from 'react';

export const Button = ({label, onClick, isDisabled, type = 'button', ...rest}) => (
    <button type={type} disabled={isDisabled} aria-describedby={rest['aria-describedby']} onClick={onClick}>
        {label}
    </button>
);

export const Loader = () => <span data-testid="loader"/>;

export const Typography = ({children, component: Component = 'span', id, className}) => (
    <Component id={id} className={className}>{children}</Component>
);
