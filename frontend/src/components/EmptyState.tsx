import React from 'react';
import { theme } from '../theme';

interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ icon = '📭', title, description, action }) => {
  return (
    <div style={styles.container}>
      <div style={styles.icon}>{icon}</div>
      <h3 style={styles.title}>{title}</h3>
      {description && <p style={styles.description}>{description}</p>}
      {action && <div style={styles.action}>{action}</div>}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '60px 24px',
    textAlign: 'center',
  },
  icon: {
    fontSize: '64px',
    marginBottom: '16px',
    opacity: 0.8,
  },
  title: {
    color: theme.colors.text,
    fontSize: '20px',
    fontWeight: 600,
    margin: '0 0 8px 0',
  },
  description: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    margin: 0,
    maxWidth: '300px',
  },
  action: {
    marginTop: '20px',
  },
};
