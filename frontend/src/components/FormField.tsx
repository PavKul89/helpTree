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

export const Input: React.FC<InputProps> = ({ error, style, ...props }) => (
  <input
    style={{
      width: '100%',
      padding: '12px 16px',
      fontSize: '15px',
      backgroundColor: 'rgba(255,255,255,0.08)',
      border: `1.5px solid ${error ? theme.colors.error : 'rgba(255,255,255,0.2)'}`,
      borderRadius: theme.borderRadius.md,
      outline: 'none',
      color: theme.colors.text,
      boxSizing: 'border-box' as const,
      transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
      ...style,
    }}
    onFocus={(e) => {
      e.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.borderFocus;
      e.currentTarget.style.boxShadow = `0 0 0 3px ${error ? 'rgba(239,68,68,0.25)' : 'rgba(56,189,248,0.2)'}`;
      props.onFocus?.(e);
    }}
    onBlur={(e) => {
      e.currentTarget.style.borderColor = error ? theme.colors.error : 'rgba(255,255,255,0.2)';
      e.currentTarget.style.boxShadow = 'none';
      props.onBlur?.(e);
    }}
    {...props}
  />
);

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean;
}

export const Textarea: React.FC<TextareaProps> = ({ error, style, ...props }) => (
  <textarea
    style={{
      width: '100%',
      padding: '12px 16px',
      fontSize: '15px',
      backgroundColor: 'rgba(255,255,255,0.08)',
      border: `1.5px solid ${error ? theme.colors.error : 'rgba(255,255,255,0.2)'}`,
      borderRadius: theme.borderRadius.md,
      outline: 'none',
      color: theme.colors.text,
      boxSizing: 'border-box' as const,
      resize: 'vertical' as const,
      transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
      ...style,
    }}
    onFocus={(e) => {
      e.currentTarget.style.borderColor = error ? theme.colors.error : theme.colors.borderFocus;
      e.currentTarget.style.boxShadow = `0 0 0 3px ${error ? 'rgba(239,68,68,0.25)' : 'rgba(56,189,248,0.2)'}`;
      props.onFocus?.(e);
    }}
    onBlur={(e) => {
      e.currentTarget.style.borderColor = error ? theme.colors.error : 'rgba(255,255,255,0.2)';
      e.currentTarget.style.boxShadow = 'none';
      props.onBlur?.(e);
    }}
    {...props}
  />
);

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '14px',
  fontWeight: 600,
  color: 'rgba(255,255,255,0.85)',
  marginBottom: '8px',
};

const errorStyle: React.CSSProperties = {
  color: theme.colors.errorLight,
  fontSize: '13px',
  marginTop: '6px',
  fontWeight: 500,
};
