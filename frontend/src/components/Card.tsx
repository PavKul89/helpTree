import React from 'react';
import { theme } from '../theme';

interface CardProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  hoverable?: boolean;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, style, hoverable, className }) => {
  return (
    <div 
      className={`${className || ''} ${hoverable ? 'card-hoverable' : ''}`} 
      style={{ ...styles.card, ...(hoverable ? styles.hoverable : {}), ...style }}
    >
      {children}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  card: {
    background: theme.gradients.card,
    borderRadius: theme.borderRadius.lg,
    padding: theme.spacing.lg,
    border: `1px solid ${theme.colors.border}`,
    boxShadow: theme.shadows.card,
  },
  hoverable: {
    cursor: 'pointer',
    transition: 'transform 0.2s ease, box-shadow 0.2s ease',
  },
};
