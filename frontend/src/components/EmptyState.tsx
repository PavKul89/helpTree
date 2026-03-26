import React from 'react';
import { theme } from '../theme';

type EmptyStateVariant = 'default' | 'posts' | 'chats' | 'users' | 'search' | 'error' | 'loading';

interface EmptyStateProps {
  variant?: EmptyStateVariant;
  title: string;
  description?: string;
  action?: React.ReactNode;
}

const variantConfig: Record<EmptyStateVariant, { icon: string; gradient: string }> = {
  default: { icon: '📭', gradient: 'linear-gradient(135deg, rgba(34, 211, 238, 0.1) 0%, transparent 100%)' },
  posts: { icon: '🌲', gradient: 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, transparent 100%)' },
  chats: { icon: '💬', gradient: 'linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, transparent 100%)' },
  users: { icon: '👥', gradient: 'linear-gradient(135deg, rgba(139, 92, 246, 0.1) 0%, transparent 100%)' },
  search: { icon: '🔍', gradient: 'linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, transparent 100%)' },
  error: { icon: '⚠️', gradient: 'linear-gradient(135deg, rgba(239, 68, 68, 0.1) 0%, transparent 100%)' },
  loading: { icon: '⏳', gradient: 'linear-gradient(135deg, rgba(34, 211, 238, 0.1) 0%, transparent 100%)' },
};

export const EmptyState: React.FC<EmptyStateProps> = ({ 
  variant = 'default', 
  title, 
  description, 
  action 
}) => {
  const config = variantConfig[variant];
  
  return (
    <div style={{ ...styles.container, background: config.gradient }}>
      <div style={styles.iconWrapper}>
        <span style={styles.icon}>{config.icon}</span>
      </div>
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
    padding: '80px 24px',
    textAlign: 'center',
    borderRadius: theme.borderRadius.lg,
    margin: '20px 0',
  },
  iconWrapper: {
    width: '100px',
    height: '100px',
    borderRadius: '50%',
    background: 'rgba(255,255,255,0.05)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: '24px',
    boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
  },
  icon: {
    fontSize: '48px',
    opacity: 0.9,
  },
  title: {
    color: theme.colors.text,
    fontSize: '22px',
    fontWeight: 600,
    margin: '0 0 12px 0',
    maxWidth: '400px',
  },
  description: {
    color: theme.colors.textSecondary,
    fontSize: '15px',
    margin: 0,
    maxWidth: '350px',
    lineHeight: 1.6,
  },
  action: {
    marginTop: '28px',
  },
};