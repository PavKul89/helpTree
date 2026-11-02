import React from 'react';
import { Link, Navigate } from 'react-router-dom';
import { LogIn, ShieldAlert, ShieldCheck } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import '../../pages/admin/admin.css';

interface AdminRouteProps {
  children: React.ReactNode;
}

export const AdminRoute: React.FC<AdminRouteProps> = ({ children }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="admin-guard" role="status" aria-live="polite">
        <span className="admin-guard-spinner" aria-hidden="true" />
        <p>Проверяем права администратора…</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.role !== 'ADMIN') {
    return (
      <div className="admin-guard">
        <div className="admin-guard-card" role="alert">
          <span className="admin-guard-icon" aria-hidden="true">
            <ShieldAlert size={28} />
          </span>
          <h1>Доступ только для администратора</h1>
          <p>
            Ваша роль — <strong>{user.role}</strong>. Раздел доступен только пользователям с ролью{' '}
            <strong>ADMIN</strong>; сервер также проверяет права и вернёт 403 на каждый
            административный запрос.
          </p>
          <div className="admin-guard-actions">
            <Link to="/" className="admin-button admin-button--primary">
              <ShieldCheck size={16} aria-hidden="true" />
              Вернуться на главную
            </Link>
            <Link to="/login" className="admin-button admin-button--ghost">
              <LogIn size={16} aria-hidden="true" />
              Сменить аккаунт
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
