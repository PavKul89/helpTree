import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { chatApi } from '../api/chatApi';
import { theme } from '../theme';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unreadChats, setUnreadChats] = useState(0);

  useEffect(() => {
    if (user) {
      const loadUnread = () => {
        chatApi.getChats()
          .then(chats => {
            const total = chats.reduce((sum, chat) => sum + chat.unreadCount, 0);
            setUnreadChats(total);
          })
          .catch(console.error);
      };
      
      loadUnread();
      
      const interval = setInterval(loadUnread, 30000);
      
      const handleVisibility = () => {
        if (!document.hidden) loadUnread();
      };
      
      const handleChatsUpdated = () => {
        loadUnread();
      };
      
      document.addEventListener('visibilitychange', handleVisibility);
      document.addEventListener('chatsUpdated', handleChatsUpdated);
      
      return () => {
        clearInterval(interval);
        document.removeEventListener('visibilitychange', handleVisibility);
        document.removeEventListener('chatsUpdated', handleChatsUpdated);
      };
    }
  }, [user]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleLogoClick = () => {
    navigate('/');
  };

  return (
    <nav style={styles.navbar}>
      <div style={styles.container}>
        <div onClick={handleLogoClick} style={styles.logo}>
          <svg viewBox="0 0 100 120" width="40" height="48">
            <path d="M50 120 L50 70" stroke="#065F46" strokeWidth="4" fill="none"/>
            <circle cx="50" cy="40" r="25" fill="#059669" opacity="0.9"/>
            <circle cx="38" cy="48" r="16" fill="#10b981" opacity="0.8"/>
            <circle cx="62" cy="48" r="16" fill="#10b981" opacity="0.8"/>
            <circle cx="50" cy="28" r="16" fill="#34d399" opacity="0.7"/>
          </svg>
          <span style={styles.logoText}>Древо Помощи</span>
        </div>

        <div style={styles.links}>
          <div onClick={handleLogoClick} style={styles.link}>Главная</div>
          {user && (
            <>
              <Link to="/my-orders" style={styles.link}>Мои заказы</Link>
              <Link to="/chats" style={styles.linkContainer}>
                <span style={styles.link}>Чаты</span>
                {unreadChats > 0 && (
                  <span style={styles.badge}>{unreadChats}</span>
                )}
              </Link>
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
    cursor: 'pointer',
  } as React.CSSProperties,
  linkContainer: {
    position: 'relative',
    display: 'flex',
    alignItems: 'center',
    textDecoration: 'none',
  },
  badge: {
    position: 'absolute',
    top: '-8px',
    right: '-12px',
    background: '#ef4444',
    color: '#fff',
    borderRadius: '50%',
    width: '18px',
    height: '18px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '11px',
    fontWeight: 600,
  },
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
