import React, { useState } from 'react';
import { theme } from '../theme';
import './Button.css';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'outline' | 'danger';
  disableRipple?: boolean;
  children: React.ReactNode;
}

interface Ripple {
  x: number;
  y: number;
  id: number;
}

export const Button: React.FC<ButtonProps> = ({ 
  variant = 'primary', 
  disableRipple = false,
  children, 
  style,
  onClick,
  ...props 
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const [ripples, setRipples] = useState<Ripple[]>([]);
  
  const variantStyles = getVariantStyles(variant, isHovered);
  
  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (!disableRipple) {
      const button = e.currentTarget;
      const rect = button.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const id = Date.now();

      setRipples((current) => [...current, { x, y, id }]);

      setTimeout(() => {
        setRipples((current) => current.filter((ripple) => ripple.id !== id));
      }, 600);
    }
    
    if (onClick) {
      onClick(e);
    }
  };
  
  return (
    <button 
      className={`ripple-button ripple-${variant}`}
      style={{ 
        ...styles.button, 
        ...variantStyles,
        ...style,
      }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={handleClick}
      {...props}
    >
      {children}
      {ripples.map(ripple => (
        <span 
          key={ripple.id}
          className="ripple-effect"
          style={{ left: ripple.x, top: ripple.y }}
        />
      ))}
    </button>
  );
};

const getVariantStyles = (variant: string, isHovered: boolean): React.CSSProperties => {
  switch (variant) {
    case 'primary':
      return isHovered ? {
        background: theme.gradients.buttonHover,
        boxShadow: theme.shadows.buttonHover,
        color: '#10230f',
      } : {
        background: theme.gradients.button,
        boxShadow: theme.shadows.button,
        color: '#10230f',
      };
    case 'outline':
      return isHovered ? {
        background: 'rgba(200, 245, 106, 0.1)',
        border: `1px solid ${theme.colors.border}`,
        borderColor: 'var(--forest-lime)',
        color: 'var(--forest-lime)',
      } : {
        background: 'transparent',
        border: `1px solid ${theme.colors.border}`,
        color: theme.colors.text,
      };
    case 'danger':
      return isHovered ? {
        background: '#dc2626',
        color: '#ffffff',
      } : {
        background: theme.colors.error,
        color: '#ffffff',
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
    position: 'relative',
    overflow: 'hidden',
    color: '#10230f',
  },
};
