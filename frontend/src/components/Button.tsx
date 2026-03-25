import React, { useState } from 'react';
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
  const [isHovered, setIsHovered] = useState(false);
  
  const variantStyles = getVariantStyles(variant, isHovered);
  
  return (
    <button 
      style={{ 
        ...styles.button, 
        ...variantStyles,
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

const getVariantStyles = (variant: string, isHovered: boolean): React.CSSProperties => {
  switch (variant) {
    case 'primary':
      return isHovered ? {
        background: theme.gradients.buttonHover,
        boxShadow: theme.shadows.buttonHover,
        transform: 'translateY(-2px) scale(1.02)',
      } : {
        background: theme.gradients.button,
        boxShadow: theme.shadows.button,
      };
    case 'outline':
      return isHovered ? {
        background: 'rgba(255,255,255,0.1)',
        borderColor: '#22d3ee',
        color: '#22d3ee',
        transform: 'translateY(-2px)',
      } : {
        background: 'transparent',
        border: `1px solid ${theme.colors.border}`,
        color: theme.colors.text,
      };
    case 'danger':
      return isHovered ? {
        background: '#dc2626',
        transform: 'translateY(-2px)',
      } : {
        background: theme.colors.error,
      };
    default:
      return {};
  }
};

const styles: Record<string, React.CSSProperties> = {
  button: {
    padding: '12px 24px',
    borderRadius: theme.borderRadius.md,
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    border: 'none',
    outline: 'none',
  },
};
