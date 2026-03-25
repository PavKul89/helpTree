import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { theme } from '../theme';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav style={styles.navbar}>
      <div style={styles.container}>
        <Link to="/" style={styles.logo}>
          <svg viewBox="0 0 100 120" width="40" height="48">
            <path d="M50 120 L50 70" stroke="#065F46" strokeWidth="4" fill="none"/>
            <circle cx="50" cy="40" r="25" fill="#059669" opacity="0.9"/>
            <circle cx="38" cy="48" r="16" fill="#10B981" opacity="0.8"/>
            <circle cx="62" cy="48" r="16" fill="#10B981" opacity="0.8"/>
            <circle cx="50" cy="28" r="16" fill="#34D399" opacity="0.7"/>
          </svg>
          <span style={styles.logoText}>Древо Помощи</span>
        </Link>

        <div style={styles.links}>
          <Link to="/" style={styles.link}>Главная</Link>
          {user && (
            <>
              <Link to="/my-orders" style={styles.link}>Мои заказы</Link>
              <Link to="/chats" style={styles.link}>Чаты</Link>
              <Link to="/profile" style={styles.link}>
                {user.name || user.email}
              </Link>
              <button onClick={handleLogout} style={styles.logoutBtn}>
                Выйти
              </button>
            </>
          )}
          {!user && (
            <Link to="/login" style={styles.loginBtn}>Войти</Link>
          )}
        </div>
      </div>
    </nav>
  );
};

const styles: Record<string, React.CSSProperties> = {
  navbar: {
    background: 'rgba(2, 44, 34, 0.95)',
    backdropFilter: 'blur(10px)',
    borderBottom: `1px solid ${theme.colors.border}`,
    position: 'sticky',
    top: 0,
    zIndex: 1000,
  },
  container: {
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '16px 24px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logo: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    textDecoration: 'none',
    cursor: 'pointer',
    transition: 'opacity 0.2s',
  } as React.CSSProperties,
  logoText: {
    fontSize: '20px',
    fontWeight: 700,
    color: theme.colors.text,
  },
  links: {
    display: 'flex',
    alignItems: 'center',
    gap: '24px',
  },
  link: {
    color: theme.colors.textSecondary,
    textDecoration: 'none',
    fontSize: '15px',
    fontWeight: 500,
    transition: 'color 0.2s',
  } as React.CSSProperties,
  loginBtn: {
    background: theme.gradients.button,
    color: theme.colors.text,
    textDecoration: 'none',
    padding: '10px 20px',
    borderRadius: theme.borderRadius.md,
    fontSize: '14px',
    fontWeight: 600,
    transition: 'all 0.3s',
  },
  logoutBtn: {
    background: 'transparent',
    border: `1px solid ${theme.colors.border}`,
    color: theme.colors.textSecondary,
    padding: '8px 16px',
    borderRadius: theme.borderRadius.md,
    fontSize: '14px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
};
