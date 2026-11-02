import React from 'react';
import { Mail, TreeDeciduous, MessageCircle, Users, Search, AlertTriangle, Hourglass } from 'lucide-react';
import { theme } from '../theme';

type EmptyStateVariant = 'default' | 'posts' | 'chats' | 'users' | 'search' | 'error' | 'loading';

interface EmptyStateProps {
  variant?: EmptyStateVariant;
  title: string;
  description?: string;
  action?: React.ReactNode;
}

const VARIANT_CONFIG: Record<EmptyStateVariant, { IconComponent: React.ElementType; color: string; gradient: string }> = {
  default: { IconComponent: Mail, color: theme.colors.accent, gradient: 'linear-gradient(135deg, rgba(200, 245, 106, 0.1) 0%, transparent 100%)' },
  posts: { IconComponent: TreeDeciduous, color: theme.colors.primary, gradient: 'linear-gradient(135deg, rgba(140, 203, 85, 0.1) 0%, transparent 100%)' },
  chats: { IconComponent: MessageCircle, color: theme.colors.primary, gradient: 'linear-gradient(135deg, rgba(140, 203, 85, 0.1) 0%, transparent 100%)' },
  users: { IconComponent: Users, color: theme.colors.primary, gradient: 'linear-gradient(135deg, rgba(140, 203, 85, 0.1) 0%, transparent 100%)' },
  search: { IconComponent: Search, color: theme.colors.accent, gradient: 'linear-gradient(135deg, rgba(200, 245, 106, 0.1) 0%, transparent 100%)' },
  error: { IconComponent: AlertTriangle, color: theme.colors.error, gradient: 'linear-gradient(135deg, rgba(255, 119, 119, 0.1) 0%, transparent 100%)' },
  loading: { IconComponent: Hourglass, color: theme.colors.accent, gradient: 'linear-gradient(135deg, rgba(200, 245, 106, 0.1) 0%, transparent 100%)' },
};

export const EmptyState: React.FC<EmptyStateProps> = ({ 
  variant = 'default', 
  title, 
  description, 
  action 
}) => {
  const config = VARIANT_CONFIG[variant];
  const IconComponent = config.IconComponent;
  
  return (
    <div
      style={{ ...styles.container, background: config.gradient }}
      role={variant === 'error' ? 'alert' : 'status'}
    >
      <div style={styles.iconWrapper} aria-hidden="true">
        <IconComponent size={40} color={config.color} />
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
    padding: 'clamp(32px, 7vw, 56px) 20px',
    textAlign: 'center',
    borderRadius: theme.borderRadius.lg,
    margin: '16px 0',
    border: '1px solid var(--forest-border)',
  },
  iconWrapper: {
    width: '76px',
    height: '76px',
    borderRadius: '50%',
    background: 'rgba(255,255,255,0.05)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: '18px',
    boxShadow: '0 4px 20px rgba(0,0,0,0.14)',
    border: '1px solid var(--forest-border)',
  },
  title: {
    color: theme.colors.text,
    fontSize: 'clamp(18px, 3vw, 21px)',
    fontWeight: 600,
    margin: '0 0 12px 0',
    maxWidth: '400px',
  },
  description: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    margin: 0,
    maxWidth: '350px',
    lineHeight: 1.6,
  },
  action: {
    marginTop: '28px',
  },
};