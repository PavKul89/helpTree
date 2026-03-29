import React from 'react';
import { useTheme } from '../theme';

interface CardProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  hoverable?: boolean;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, style, hoverable, className }) => {
  const { theme } = useTheme();
  
  return (
    <div 
      className={`${className || ''} ${hoverable ? 'card-hoverable' : ''}`} 
      style={{ 
        background: theme.gradients.card,
        borderRadius: theme.borderRadius.lg,
        padding: theme.spacing.lg,
        border: `1px solid ${theme.colors.border}`,
        boxShadow: theme.shadows.card,
        ...(hoverable ? { cursor: 'pointer', transition: 'transform 0.2s ease, box-shadow 0.2s ease' } : {}),
        ...style 
      }}
    >
      {children}
    </div>
  );
};
