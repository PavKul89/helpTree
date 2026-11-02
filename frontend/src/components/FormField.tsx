import React from 'react';
import { theme } from '../theme';

interface FormFieldProps {
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
  style?: React.CSSProperties;
}

export const FormField: React.FC<FormFieldProps> = ({ label, error, required, children, style }) => (
  <div style={{ marginBottom: '16px', ...style }}>
    <label style={labelStyle}>
      {label}
      {required && <span style={{ color: theme.colors.error, marginLeft: 4 }}>*</span>}
    </label>
    {children}
    {error && <div style={errorStyle}>{error}</div>}
  </div>
);

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
}

export const Input: React.FC<InputProps> = ({ error, style, onFocus, onBlur, ...props }) => (
  <input
    style={{
      width: '100%',
      minHeight: 44,
      padding: '12px 16px',
      fontSize: '15px',
      backgroundColor: 'var(--forest-bg-secondary)',
      border: `1.5px solid ${error ? theme.colors.error : theme.colors.border}`,
      borderRadius: theme.borderRadius.md,
      color: theme.colors.text,
      boxSizing: 'border-box',
      transition: 'border-color 0.18s ease, box-shadow 0.18s ease',
      ...style,
    }}
    aria-invalid={error || undefined}
    onFocus={(event) => {
      event.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.borderFocus;
      event.currentTarget.style.boxShadow = error
        ? '0 0 0 3px rgba(255, 119, 119, 0.2)'
        : 'var(--forest-shadow-focus)';
      onFocus?.(event);
    }}
    onBlur={(event) => {
      event.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.border;
      event.currentTarget.style.boxShadow = 'none';
      onBlur?.(event);
    }}
    {...props}
  />
);

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean;
}

export const Textarea: React.FC<TextareaProps> = ({ error, style, onFocus, onBlur, ...props }) => (
  <textarea
    style={{
      width: '100%',
      minHeight: 100,
      padding: '12px 16px',
      fontSize: '15px',
      backgroundColor: 'var(--forest-bg-secondary)',
      border: `1.5px solid ${error ? theme.colors.error : theme.colors.border}`,
      borderRadius: theme.borderRadius.md,
      color: theme.colors.text,
      boxSizing: 'border-box' as const,
      resize: 'vertical' as const,
      transition: 'border-color 0.18s ease, box-shadow 0.18s ease',
      ...style,
    }}
    aria-invalid={error || undefined}
    onFocus={(event) => {
      event.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.borderFocus;
      event.currentTarget.style.boxShadow = error
        ? '0 0 0 3px rgba(255, 119, 119, 0.2)'
        : 'var(--forest-shadow-focus)';
      onFocus?.(event);
    }}
    onBlur={(event) => {
      event.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.border;
      event.currentTarget.style.boxShadow = 'none';
      onBlur?.(event);
    }}
    {...props}
  />
);

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '14px',
  fontWeight: 600,
  color: 'var(--forest-text-secondary)',
  marginBottom: '8px',
};

const errorStyle: React.CSSProperties = {
  color: theme.colors.error,
  fontSize: '13px',
  marginTop: '6px',
  fontWeight: 500,
};
