import { Input } from 'antd';
import { type ReactNode, useId } from 'react';
import './form-fields.css';

interface FieldFrameProps {
  label: string;
  hint?: string;
  error?: string;
  children: (controlId: string) => ReactNode;
}

function FieldFrame({ label, hint, error, children }: FieldFrameProps) {
  const controlId = useId();
  return (
    <div className="field">
      <label className="field__label" htmlFor={controlId}>{label}</label>
      {children(controlId)}
      {error === undefined ? null : <div className="field__error" role="alert">{error}</div>}
      {hint === undefined || error !== undefined ? null : <div className="field__hint">{hint}</div>}
    </div>
  );
}

interface TextFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'password' | 'number' | 'date';
  min?: string;
  hint?: string;
  error?: string;
  disabled?: boolean;
  autoComplete?: string;
  inputMode?: 'numeric' | 'text';
}

export function TextField({ label, value, onChange, type = 'text', min, hint, error, disabled = false, autoComplete, inputMode }: TextFieldProps) {
  return (
    <FieldFrame label={label} {...(hint === undefined ? {} : { hint })} {...(error === undefined ? {} : { error })}>
      {(id) => type === 'password'
        ? <Input.Password id={id} value={value} disabled={disabled} {...(autoComplete === undefined ? {} : { autoComplete })} status={error === undefined ? '' : 'error'} onChange={(event) => onChange(event.target.value)} />
        : <Input id={id} type={type} value={value} disabled={disabled} {...(min === undefined ? {} : { min })} {...(autoComplete === undefined ? {} : { autoComplete })} {...(inputMode === undefined ? {} : { inputMode })} status={error === undefined ? '' : 'error'} onChange={(event) => onChange(event.target.value)} />}
    </FieldFrame>
  );
}

interface TextAreaFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
  rows?: number;
}

export function TextAreaField({ label, value, onChange, hint, error, rows = 3 }: TextAreaFieldProps) {
  return (
    <FieldFrame label={label} {...(hint === undefined ? {} : { hint })} {...(error === undefined ? {} : { error })}>
      {(id) => <Input.TextArea id={id} rows={rows} value={value} status={error === undefined ? '' : 'error'} onChange={(event) => onChange(event.target.value)} />}
    </FieldFrame>
  );
}

interface SelectFieldProps {
  label: string;
  value: string;
  options: readonly { value: string; label: string }[];
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
}

export function SelectField({ label, value, options, onChange, hint, error }: SelectFieldProps) {
  return (
    <FieldFrame label={label} {...(hint === undefined ? {} : { hint })} {...(error === undefined ? {} : { error })}>
      {(id) => (
        <select id={id} className="field__select" value={value} onChange={(event) => onChange(event.target.value)}>
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      )}
    </FieldFrame>
  );
}
