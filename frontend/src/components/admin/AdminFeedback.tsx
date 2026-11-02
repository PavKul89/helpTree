import React from 'react';
import { AlertTriangle, Inbox, RefreshCw } from 'lucide-react';

export const AdminLoadingBlock: React.FC<{ rows?: number; className?: string }> = ({
  rows = 4,
  className = '',
}) => (
  <div className={`admin-loading-block ${className}`.trim()} role="status" aria-live="polite">
    <span className="admin-visually-hidden">Загрузка данных</span>
    {Array.from({ length: rows }).map((_, index) => (
      <div key={index} className="admin-skeleton-row" aria-hidden="true">
        <span className="admin-skeleton admin-skeleton--avatar" />
        <span className="admin-skeleton admin-skeleton--line" />
        <span className="admin-skeleton admin-skeleton--short" />
      </div>
    ))}
  </div>
);

interface AdminErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
}

export const AdminErrorState: React.FC<AdminErrorStateProps> = ({
  title = 'Не удалось загрузить данные',
  message = 'Сервер вернул ошибку. Повторите запрос или проверьте соединение.',
  onRetry,
  children,
}) => (
  <div className="admin-state admin-state--error" role="alert">
    <span className="admin-state-icon" aria-hidden="true">
      <AlertTriangle size={24} />
    </span>
    <h2>{title}</h2>
    <p>{message}</p>
    <div className="admin-state-actions">
      {onRetry && (
        <button type="button" className="admin-button admin-button--primary" onClick={onRetry}>
          <RefreshCw size={16} aria-hidden="true" />
          Повторить
        </button>
      )}
      {children}
    </div>
  </div>
);

interface AdminEmptyStateProps {
  title: string;
  description?: string;
  action?: React.ReactNode;
  icon?: React.ReactNode;
}

export const AdminEmptyState: React.FC<AdminEmptyStateProps> = ({
  title,
  description,
  action,
  icon,
}) => (
  <div className="admin-state admin-state--empty">
    <span className="admin-state-icon" aria-hidden="true">
      {icon ?? <Inbox size={24} />}
    </span>
    <h2>{title}</h2>
    {description && <p>{description}</p>}
    {action && <div className="admin-state-actions">{action}</div>}
  </div>
);
