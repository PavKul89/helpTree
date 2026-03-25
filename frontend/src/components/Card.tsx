import React from 'react';
import { theme } from '../theme';

interface CardProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
  hoverable?: boolean;
}

export const Card: React.FC<CardProps> = ({ children, style, hoverable }) => {
  return (
    <div style={{ ...styles.card, ...(hoverable ? styles.hoverable : {}), ...style }}>
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
    transition: 'all 0.3s ease',
  },
};
