// Test doubles for the @jahia/moonstone components used by the module. They mirror the markup the real components
// render (checked against Moonstone 2.21.1): Input, Switch and Textarea put id and aria-* on the control; Button is
// type="button" unless told otherwise and calls onClick unconditionally; Header renders an h1; Table cells take
// `component`; Modal renders nothing when closed, an aria-modal dialog when open (closed by Escape through
// onOpenChange), and ModalHeader its title as an h4. Each double carries data-moonstone="<Name>" so that tests can
// check what is used.
import React from 'react';

const mark = name => ({'data-moonstone': name});

export const Button = ({label, onClick, isDisabled, variant, size, color, icon, ...rest}) => (
    <button type="button" disabled={isDisabled} data-variant={variant} data-size={size} data-color={color} onClick={event => onClick(event)} {...mark('Button')} {...rest}>
        {icon}{label}
    </button>
);

export const Loader = () => <span data-testid="loader" {...mark('Loader')}/>;

export const Typography = ({children, component: Component = 'p', variant, weight, isNowrap, ...rest}) => (
    <Component data-variant={variant} {...mark('Typography')} {...rest}>{children}</Component>
);

export const LayoutContent = ({header, content, isLoading, hasPadding, ...rest}) => (
    <div {...mark('LayoutContent')} {...rest}>
        {header}
        <div>{isLoading ? <span data-testid="loader"/> : content}</div>
    </div>
);

export const Header = ({title, mainActions, status, toolbarLeft, toolbarRight, ...rest}) => (
    <header {...mark('Header')} {...rest}>
        <h1>{title}</h1>
        <div>{mainActions}</div>
        <div>{status}{toolbarLeft}{toolbarRight}</div>
    </header>
);

export const Separator = () => <hr {...mark('Separator')}/>;

export const Chip = ({label, color, variant, icon, ...rest}) => (
    <span data-color={color} {...mark('Chip')} {...rest}>{icon}<span>{label}</span></span>
);

export const Table = ({children, ...rest}) => <table {...mark('Table')} {...rest}>{children}</table>;
export const TableHead = ({children, ...rest}) => <thead {...mark('TableHead')} {...rest}>{children}</thead>;
export const TableBody = ({children, ...rest}) => <tbody {...mark('TableBody')} {...rest}>{children}</tbody>;
export const TableRow = ({children, ...rest}) => <tr {...mark('TableRow')} {...rest}>{children}</tr>;
export const TableHeadCell = ({children, ...rest}) => <th {...mark('TableHeadCell')} {...rest}>{children}</th>;
export const TableBodyCell = ({children, component: Component = 'td', verticalAlign, ...rest}) => (
    <Component {...mark('TableBodyCell')} {...rest}>{children}</Component>
);

export const Modal = ({isOpen, onOpenChange, size, children, ...rest}) => (isOpen ? (
    <div
        role="dialog"
        aria-modal="true"
        data-size={size}
        onKeyDown={event => event.key === 'Escape' && onOpenChange && onOpenChange(false)}
        {...mark('Modal')}
        {...rest}
    >
        {children}
    </div>
) : null);

export const ModalHeader = ({title, children, ...rest}) => (
    <header {...mark('ModalHeader')} {...rest}><h4>{title}</h4>{children}</header>
);

export const ModalBody = ({children, ...rest}) => <div {...mark('ModalBody')} {...rest}>{children}</div>;

export const ModalFooter = ({children, ...rest}) => <footer {...mark('ModalFooter')} {...rest}>{children}</footer>;

export const Input = ({isDisabled, onChange, value, ...rest}) => (
    <div><input disabled={isDisabled} value={value} onChange={onChange} {...mark('Input')} {...rest}/></div>
);

export const Textarea = ({isDisabled, onChange, value, ...rest}) => (
    <textarea disabled={isDisabled} value={value} onChange={onChange} {...mark('Textarea')} {...rest}/>
);

export const Switch = ({isDisabled, onChange, checked, value = '', ...rest}) => (
    <div>
        <input
            type="checkbox"
            role="checkbox"
            checked={checked}
            disabled={isDisabled}
            onChange={event => onChange && onChange(event, value, event.target.checked)}
            {...mark('Switch')}
            {...rest}
        />
    </div>
);

const RadioContext = React.createContext({});

export const RadioGroup = ({name, value, onChange, children, ...rest}) => (
    <RadioContext.Provider value={{name, value, onChange}}>
        <div {...mark('RadioGroup')} {...rest}>{children}</div>
    </RadioContext.Provider>
);

export const RadioItem = ({id, label, value, ...rest}) => {
    const group = React.useContext(RadioContext);
    return (
        <label {...mark('RadioItem')}>
            <input
                type="radio"
                id={id}
                name={group.name}
                value={value}
                aria-labelledby={`${id}-label`}
                checked={group.value === value}
                onChange={event => group.onChange && group.onChange(event, value)}
                {...rest}
            />
            <span id={`${id}-label`}>{label}</span>
        </label>
    );
};

export const Information = props => <svg data-testid="icon-information" {...mark('Information')} {...props}/>;
export const Warning = props => <svg data-testid="icon-warning" {...mark('Warning')} {...props}/>;
