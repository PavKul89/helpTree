import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import { theme } from '../theme';

export const Navbar: React.FC = () => {
  const { user, logout, newResponsesCount } = useAuth();
  const navigate = useNavigate();
  const [unreadChats, setUnreadChats] = useState(0);
  const [debtWarning, setDebtWarning] = useState<number | null>(null);
  const [isBlocked, setIsBlocked] = useState(false);
  const [daysUntilBlock, setDaysUntilBlock] = useState<number | null>(null);
  const [openDropdown, setOpenDropdown] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [showNotifications, setShowNotifications] = useState(false);

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
          if ('blockedAt' in userData && userData.blockedAt) {
            setIsBlocked(true);
            setDebtWarning(null);
            const hoursBlocked = (Date.now() - new Date(userData.blockedAt).getTime()) / (1000 * 60 * 60);
            const totalHoursLeft = Math.max(0, 7 * 24 - hoursBlocked);
            setDaysUntilBlock(totalHoursLeft);
          } else if ('debtCount' in userData && userData.debtCount !== undefined && userData.debtCount > 2) {
            setIsBlocked(false);
            setDebtWarning(userData.debtCount);
            const debtStartDate = 'debtStartedAt' in userData && userData.debtStartedAt 
              ? new Date(userData.debtStartedAt) 
              : new Date(userData.createdAt);
            const hoursSinceDebt = (Date.now() - debtStartDate.getTime()) / (1000 * 60 * 60);
            const totalHoursLeft = Math.max(0, 7 * 24 - hoursSinceDebt);
            setDaysUntilBlock(totalHoursLeft);
          } else {
            setIsBlocked(false);
            setDebtWarning(null);
            setDaysUntilBlock(null);
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

        <div style={styles.links} ref={dropdownRef}>
          <div onClick={handleLogoClick} style={styles.link}>Главная</div>
          
          <div style={styles.dropdownWrapper}>
            <button 
              style={styles.dropdownTrigger}
              onClick={() => toggleDropdown('community')}
              onMouseEnter={() => user && setOpenDropdown('community')}
            >
              Сообщество ▾
            </button>
            {(openDropdown === 'community' || !user) && (
              <div style={styles.dropdown}>
                <Link to="/map" style={styles.dropdownItem} onClick={closeDropdown}>
                  🗺️ Карта
                </Link>
                <Link to="/graph" style={styles.dropdownItem} onClick={closeDropdown}>
                  🌳 Граф помощи
                </Link>
                <Link to="/achievements" style={styles.dropdownItem} onClick={closeDropdown}>
                  🏆 Достижения
                </Link>
                {user && (
                  <Link to="/activity" style={styles.dropdownItem} onClick={closeDropdown}>
                    📋 Активность
                  </Link>
                )}
              </div>
            )}
          </div>

          {user && (
            <>
              <div style={styles.dropdownWrapper}>
                <button 
                  style={styles.dropdownTrigger}
                  onClick={() => toggleDropdown('profile')}
                  onMouseEnter={() => setOpenDropdown('profile')}
                >
                  👤 Профиль ▾
                </button>
                {openDropdown === 'profile' && (
                  <div style={styles.dropdown}>
                    <Link to="/profile" style={styles.dropdownItem} onClick={closeDropdown}>
                      👤 Мой профиль
                    </Link>
                    <Link to="/favorites" style={styles.dropdownItem} onClick={closeDropdown}>
                      ⭐ Избранное
                    </Link>
                    <Link to="/my-orders" style={styles.dropdownItem} onClick={closeDropdown}>
                      📦 Мои заказы
                    </Link>
                    <Link to="/chats" style={styles.dropdownItem} onClick={closeDropdown}>
                      💬 Чаты
                      {unreadChats > 0 && <span style={styles.dropdownBadge}>{unreadChats}</span>}
                    </Link>
                  </div>
                )}
              </div>

              <button 
                style={styles.notificationBtn}
                onClick={() => setShowNotifications(!showNotifications)}
                title="Уведомления"
              >
                🔔
                {newResponsesCount > 0 && (
                  <span style={styles.notificationBadge}>{newResponsesCount}</span>
                )}
              </button>

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
      {isBlocked && (
        <div style={styles.blockedBanner}>
          🚫 Ваш аккаунт заблокирован за долг. Помогите другим пользователям, чтобы разблокировать аккаунт.
          {daysUntilBlock !== null && daysUntilBlock > 0 && (
            daysUntilBlock >= 24 
              ? ` Осталось ${Math.floor(daysUntilBlock / 24)} дней и ${Math.floor(daysUntilBlock % 24)} часов.`
              : ` Осталось ${Math.floor(daysUntilBlock)} часов.`
          )}
          {daysUntilBlock !== null && daysUntilBlock <= 0 && ` Ожидайте разблокировки.`}
        </div>
      )}
      {debtWarning && debtWarning > 2 && !isBlocked && (
        <div style={styles.warningBanner}>
          ⚠️ Внимание! Ваш долг: {debtWarning}. Помогите {debtWarning - 2} людям, чтобы избежать блокировки.
          {daysUntilBlock !== null && daysUntilBlock > 0 && (
            daysUntilBlock >= 24 
              ? ` Осталось ${Math.floor(daysUntilBlock / 24)} дней и ${Math.floor(daysUntilBlock % 24)} часов.`
              : ` Осталось ${Math.floor(daysUntilBlock)} часов.`
          )}
        </div>
      )}
      {showNotifications && (
        <div style={styles.notificationPopup}>
          <div style={styles.notificationHeader}>Уведомления</div>
          {newResponsesCount > 0 ? (
            <div style={styles.notificationItem}>
              <span style={styles.notificationIcon}>💬</span>
              <div style={styles.notificationContent}>
                <div style={styles.notificationText}>Новые отклики на ваши посты</div>
                <div style={styles.notificationCount}>{newResponsesCount} новых откликов</div>
              </div>
            </div>
          ) : (
            <div style={styles.notificationEmpty}>Нет новых уведомлений</div>
          )}
        </div>
      )}
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
    gap: '16px',
  },
  link: {
    color: theme.colors.textSecondary,
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
    color: theme.colors.textSecondary,
    fontSize: '15px',
    fontWeight: 500,
    cursor: 'pointer',
    padding: '8px 12px',
    borderRadius: theme.borderRadius.md,
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
    background: 'rgba(6, 95, 70, 0.98)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
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
    color: theme.colors.textSecondary,
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
  warningBanner: {
    background: 'linear-gradient(90deg, #dc2626 0%, #f59e0b 100%)',
    color: '#fff',
    padding: '10px 24px',
    textAlign: 'center',
    fontSize: '14px',
    fontWeight: 500,
  },
  blockedBanner: {
    background: '#dc2626',
    color: '#fff',
    padding: '10px 24px',
    textAlign: 'center',
    fontSize: '14px',
    fontWeight: 500,
  },
  notificationBtn: {
    background: 'transparent',
    border: 'none',
    fontSize: '20px',
    cursor: 'pointer',
    padding: '8px',
    position: 'relative',
    borderRadius: theme.borderRadius.md,
    transition: 'all 0.2s',
  },
  notificationBadge: {
    position: 'absolute',
    top: '0',
    right: '0',
    background: '#ef4444',
    color: '#fff',
    borderRadius: '10px',
    padding: '2px 6px',
    fontSize: '10px',
    fontWeight: 600,
    minWidth: '16px',
    textAlign: 'center',
  },
  notificationPopup: {
    position: 'absolute',
    top: '60px',
    right: '24px',
    background: 'rgba(6, 95, 70, 0.98)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    minWidth: '280px',
    padding: '12px 0',
    boxShadow: '0 10px 40px rgba(0,0,0,0.3)',
    zIndex: 1002,
  },
  notificationHeader: {
    padding: '8px 16px',
    fontWeight: 600,
    fontSize: '14px',
    color: theme.colors.text,
    borderBottom: `1px solid ${theme.colors.border}`,
    marginBottom: '8px',
  },
  notificationItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '12px 16px',
    cursor: 'pointer',
    transition: 'background 0.2s',
  } as React.CSSProperties,
  notificationIcon: {
    fontSize: '20px',
  },
  notificationContent: {
    flex: 1,
  },
  notificationText: {
    fontSize: '13px',
    color: theme.colors.textSecondary,
  },
  notificationCount: {
    fontSize: '12px',
    color: theme.colors.textSecondary,
    marginTop: '2px',
    opacity: 0.8,
  },
  notificationEmpty: {
    padding: '16px',
    textAlign: 'center',
    color: theme.colors.textSecondary,
    fontSize: '13px',
  },
};
