import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme, theme as defaultTheme } from '../theme';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const theme = defaultTheme;
  const navigate = useNavigate();
  const [unreadChats, setUnreadChats] = useState(0);
  const [debtWarning, setDebtWarning] = useState<number | null>(null);
  const [openDropdown, setOpenDropdown] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpenDropdown(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

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

      authApi.getCurrentUser()
        .then(userData => {
          if ('debtCount' in userData && userData.debtCount !== undefined && userData.debtCount > 2) {
            setDebtWarning(userData.debtCount);
          }
        })
        .catch(console.error);
      
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

  const toggleDropdown = (name: string) => {
    setOpenDropdown(openDropdown === name ? null : name);
  };

  const closeDropdown = () => {
    setOpenDropdown(null);
  };

  const navbarBg = isDark ? 'rgba(2, 44, 34, 0.95)' : 'rgba(240, 253, 250, 0.95)';
  const dropdownBg = isDark ? 'rgba(6, 95, 70, 0.98)' : 'rgba(255, 255, 255, 0.98)';

  return (
    <nav style={{ ...styles.navbar, background: navbarBg }}>
      <div style={styles.container}>
        <div onClick={handleLogoClick} style={styles.logo}>
          <svg viewBox="0 0 100 120" width="40" height="48">
            <path d="M50 120 L50 70" stroke="#065F46" strokeWidth="4" fill="none"/>
            <circle cx="50" cy="40" r="25" fill="#059669" opacity="0.9"/>
            <circle cx="38" cy="48" r="16" fill="#10b981" opacity="0.8"/>
            <circle cx="62" cy="48" r="16" fill="#10b981" opacity="0.8"/>
            <circle cx="50" cy="28" r="16" fill="#34d399" opacity="0.7"/>
          </svg>
          <span style={{ ...styles.logoText, color: theme.colors.text }}>Древо Помощи</span>
        </div>

        <div style={styles.links} ref={dropdownRef}>
          <div onClick={handleLogoClick} style={{ ...styles.link, color: theme.colors.textSecondary }}>Главная</div>
          
          <div style={styles.dropdownWrapper}>
            <button 
              style={{ ...styles.dropdownTrigger, color: theme.colors.textSecondary, borderRadius: theme.borderRadius.md }}
              onClick={() => toggleDropdown('community')}
              onMouseEnter={() => user && setOpenDropdown('community')}
            >
              Сообщество ▾
            </button>
            {(openDropdown === 'community' || !user) && (
              <div style={{ ...styles.dropdown, background: dropdownBg, border: `1px solid ${theme.colors.border}`, borderRadius: theme.borderRadius.md }}>
                <Link to="/map" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                  🗺️ Карта
                </Link>
                <Link to="/graph" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                  🌳 Граф помощи
                </Link>
                <Link to="/achievements" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                  🏆 Достижения
                </Link>
                {user && (
                  <Link to="/activity" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                    📋 Активность
                  </Link>
                )}
              </div>
            )}
          </div>

          <button 
            onClick={toggleTheme}
            style={{ ...styles.themeToggle, border: `1px solid ${theme.colors.border}`, color: theme.colors.text, borderRadius: theme.borderRadius.md }}
            title={isDark ? 'Светлая тема' : 'Тёмная тема'}
          >
            {isDark ? '☀️' : '🌙'}
          </button>

          {user && (
            <>
              <div style={styles.dropdownWrapper}>
                <button 
                  style={{ ...styles.dropdownTrigger, color: theme.colors.textSecondary, borderRadius: theme.borderRadius.md }}
                  onClick={() => toggleDropdown('profile')}
                  onMouseEnter={() => setOpenDropdown('profile')}
                >
                  👤 Профиль ▾
                </button>
                {openDropdown === 'profile' && (
                  <div style={{ ...styles.dropdown, background: dropdownBg, border: `1px solid ${theme.colors.border}`, borderRadius: theme.borderRadius.md }}>
                    <Link to="/profile" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                      👤 Мой профиль
                    </Link>
                    <Link to="/favorites" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                      ⭐ Избранное
                    </Link>
                    <Link to="/my-orders" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                      📦 Мои заказы
                    </Link>
                    <Link to="/chats" style={{ ...styles.dropdownItem, color: theme.colors.textSecondary }} onClick={closeDropdown}>
                      💬 Чаты
                      {unreadChats > 0 && <span style={styles.dropdownBadge}>{unreadChats}</span>}
                    </Link>
                  </div>
                )}
              </div>

              <button onClick={handleLogout} style={{ ...styles.logoutBtn, border: `1px solid ${theme.colors.border}`, color: theme.colors.textSecondary, borderRadius: theme.borderRadius.md }}>
                Выйти
              </button>
            </>
          )}
          {!user && (
            <Link to="/login" style={{ ...styles.loginBtn, background: theme.gradients.button, color: theme.colors.text, borderRadius: theme.borderRadius.md }}>Войти</Link>
          )}
        </div>
      </div>
      {debtWarning && debtWarning > 2 && (
        <div style={styles.warningBanner}>
          ⚠️ Внимание! Ваш долг: {debtWarning}. Помогите {debtWarning - 2} людям, чтобы избежать блокировки.
        </div>
      )}
    </nav>
  );
};

const styles: Record<string, React.CSSProperties> = {
  navbar: {
    backdropFilter: 'blur(10px)',
    borderBottom: `1px solid rgba(255, 255, 255, 0.2)`,
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
  },
  links: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  link: {
    textDecoration: 'none',
    fontSize: '15px',
    fontWeight: 500,
    transition: 'color 0.2s',
    cursor: 'pointer',
  } as React.CSSProperties,
  dropdownWrapper: {
    position: 'relative',
  },
  dropdownTrigger: {
    background: 'transparent',
    border: 'none',
    fontSize: '15px',
    fontWeight: 500,
    cursor: 'pointer',
    padding: '8px 12px',
    transition: 'all 0.2s',
    fontFamily: 'inherit',
  },
  dropdownWrapperHover: {
    background: 'rgba(255,255,255,0.05)',
  },
  dropdown: {
    position: 'absolute',
    top: '100%',
    right: 0,
    marginTop: '8px',
    minWidth: '180px',
    padding: '8px 0',
    boxShadow: '0 10px 40px rgba(0,0,0,0.3)',
    zIndex: 1001,
  },
  dropdownItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 16px',
    textDecoration: 'none',
    fontSize: '14px',
    transition: 'all 0.2s',
  } as React.CSSProperties,
  dropdownBadge: {
    background: '#ef4444',
    color: '#fff',
    borderRadius: '10px',
    padding: '2px 8px',
    fontSize: '11px',
    fontWeight: 600,
  },
  loginBtn: {
    textDecoration: 'none',
    padding: '10px 20px',
    fontSize: '14px',
    fontWeight: 600,
    transition: 'all 0.3s',
  },
  logoutBtn: {
    background: 'transparent',
    padding: '8px 16px',
    fontSize: '14px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  warningBanner: {
    background: 'linear-gradient(90deg, #dc2626 0%, #f59e0b 100%)',
    color: '#fff',
    padding: '10px 24px',
    textAlign: 'center',
    fontSize: '14px',
    fontWeight: 500,
  },
  themeToggle: {
    background: 'transparent',
    padding: '8px 12px',
    fontSize: '18px',
    cursor: 'pointer',
    transition: 'all 0.2s',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
};
