import React, { useState } from 'react';
import { useTheme } from '../theme';

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
  const { theme } = useTheme();
  const [isHovered, setIsHovered] = useState(false);
  
  const getVariantStyles = (v: string, hovered: boolean): React.CSSProperties => {
    switch (v) {
      case 'primary':
        return hovered ? {
          background: theme.gradients.buttonHover,
          boxShadow: theme.shadows.buttonHover,
          transform: 'translateY(-2px) scale(1.02)',
        } : {
          background: theme.gradients.button,
          boxShadow: theme.shadows.button,
        };
      case 'outline':
        return hovered ? {
          background: theme.mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)',
          borderColor: theme.colors.accent,
          color: theme.colors.accent,
          transform: 'translateY(-2px)',
        } : {
          background: 'transparent',
          border: `1px solid ${theme.colors.border}`,
          color: theme.colors.text,
        };
      case 'danger':
        return hovered ? {
          background: '#dc2626',
          transform: 'translateY(-2px)',
        } : {
          background: theme.colors.error,
        };
      default:
        return {};
    }
  };
  
  return (
    <button 
      style={{ 
        padding: '12px 24px',
        borderRadius: theme.borderRadius.md,
        fontSize: '14px',
        fontWeight: 600,
        cursor: 'pointer',
        transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        border: 'none',
        outline: 'none',
        ...getVariantStyles(variant, isHovered),
        ...style,
      }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      {...props}
    >
      {children}
    </button>
  );
};
