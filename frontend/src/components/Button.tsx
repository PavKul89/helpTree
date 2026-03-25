import React from 'react';
import { theme } from '../theme';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'outline' | 'danger';
  children: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({ 
  variant = 'primary', 
  children, 
  style,
  ...props 
}) => {
  return (
    <button 
      style={{ ...styles.button, ...styles[variant], ...style }}
      {...props}
    >
      {children}
    </button>
  );
};

const styles: Record<string, React.CSSProperties> = {
  button: {
    padding: '10px 20px',
    borderRadius: theme.borderRadius.md,
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    border: 'none',
  },
  primary: {
    background: theme.gradients.button,
    color: '#fff',
    boxShadow: theme.shadows.button,
  },
  outline: {
    background: 'transparent',
    color: theme.colors.text,
    border: `1px solid ${theme.colors.border}`,
  },
  danger: {
    background: theme.colors.error,
    color: '#fff',
  },
};
