import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import { theme } from '../theme';
import { Map, GitBranch, Trophy, ClipboardList, User, Star, Package, MessageCircle, AlertTriangle, Ban } from 'lucide-react';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unreadChats, setUnreadChats] = useState(0);
  const [debtWarning, setDebtWarning] = useState<number | null>(null);
  const [isBlocked, setIsBlocked] = useState(false);
  const [daysUntilBlock, setDaysUntilBlock] = useState<number | null>(null);
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
      
      const interval = setInterval(() => {
        loadUnread();
      }, 30000);
      
      const handleVisibility = () => {
        if (!document.hidden) {
          loadUnread();
        }
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
            setDaysUntilBlock(null);
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
          <span style={styles.logoText}>helpTree</span>
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
                  <Map size={16} style={{marginRight: 8}} /> Карта
                </Link>
                <Link to="/graph" style={styles.dropdownItem} onClick={closeDropdown}>
                  <GitBranch size={16} style={{marginRight: 8}} /> Граф помощи
                </Link>
                <Link to="/achievements" style={styles.dropdownItem} onClick={closeDropdown}>
                  <Trophy size={16} style={{marginRight: 8}} /> Достижения
                </Link>
                {user && (
                  <Link to="/activity" style={styles.dropdownItem} onClick={closeDropdown}>
                    <ClipboardList size={16} style={{marginRight: 8}} /> Активность
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
                  <User size={16} style={{ marginRight: 6 }} /> Профиль ▾
                </button>
                {openDropdown === 'profile' && (
                  <div style={styles.dropdown}>
                    <Link to="/profile" style={styles.dropdownItem} onClick={closeDropdown}>
                      <User size={16} style={{marginRight: 8}} /> Мой профиль
                    </Link>
                    <Link to="/favorites" style={styles.dropdownItem} onClick={closeDropdown}>
                      <Star size={16} style={{marginRight: 8}} /> Избранное
                    </Link>
                    <Link to="/my-orders" style={styles.dropdownItem} onClick={closeDropdown}>
                      <Package size={16} style={{marginRight: 8}} /> Мои заказы
                    </Link>
                    <Link to="/chats" style={styles.dropdownItem} onClick={closeDropdown}>
                      <MessageCircle size={16} style={{marginRight: 8}} /> Чаты
                      {unreadChats > 0 && <span style={styles.dropdownBadge}>{unreadChats}</span>}
                    </Link>
                  </div>
                )}
              </div>

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
          <div style={styles.bannerContent}>
            <div style={styles.bannerIconWrapper}>
              <Ban size={20} />
            </div>
            <div style={styles.bannerText}>
              <div style={styles.bannerTitle}>Аккаунт заблокирован</div>
              <div style={styles.bannerSubtitle}>
                Помогите другим пользователям, чтобы разблокировать аккаунт.
                {daysUntilBlock !== null && daysUntilBlock > 0 && (
                  <span style={styles.bannerTimer}>
                    {daysUntilBlock >= 24 
                      ? ` Осталось ${Math.floor(daysUntilBlock / 24)} дн. ${Math.floor(daysUntilBlock % 24)} ч.`
                      : ` Осталось ${Math.floor(daysUntilBlock)} ч.`}
                  </span>
                )}
                {daysUntilBlock !== null && daysUntilBlock <= 0 && ' Ожидайте разблокировки.'}
              </div>
            </div>
          </div>
        </div>
      )}
      {debtWarning && debtWarning > 2 && !isBlocked && (
        <div style={styles.warningBanner}>
          <div style={styles.bannerContent}>
            <div style={styles.bannerIconWrapper}>
              <AlertTriangle size={20} />
            </div>
            <div style={styles.bannerText}>
              <div style={styles.bannerTitle}>Внимание! Ваш долг: {debtWarning}</div>
              <div style={styles.bannerSubtitle}>
                Помогите {debtWarning - 2} {debtWarning - 2 === 1 ? 'человеку' : 'людям'}, чтобы избежать блокировки
              </div>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
};

const styles: Record<string, React.CSSProperties> = {
  navbar: {
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.98) 0%, rgba(2, 44, 34, 0.95) 100%)',
    backdropFilter: 'blur(12px)',
    borderBottom: `1px solid rgba(6, 182, 212, 0.15)`,
    position: 'sticky',
    top: 0,
    zIndex: 1000,
    boxShadow: '0 2px 20px rgba(0, 0, 0, 0.15)',
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
    background: 'linear-gradient(135deg, rgba(6, 95, 70, 0.98) 0%, rgba(2, 44, 34, 0.98) 100%)',
    border: `1px solid rgba(6, 182, 212, 0.2)`,
    borderRadius: theme.borderRadius.lg,
    minWidth: '200px',
    padding: '8px',
    boxShadow: '0 10px 40px rgba(0, 0, 0, 0.4), 0 0 20px rgba(6, 182, 212, 0.1)',
    zIndex: 1001,
  },
  dropdownItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 14px',
    color: theme.colors.textSecondary,
    textDecoration: 'none',
    fontSize: '14px',
    transition: 'all 0.2s',
    borderRadius: theme.borderRadius.md,
  } as React.CSSProperties,
  dropdownBadge: {
    background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
    color: '#fff',
    borderRadius: '12px',
    padding: '3px 10px',
    fontSize: '11px',
    fontWeight: 600,
    boxShadow: '0 2px 8px rgba(239, 68, 68, 0.4)',
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
    background: 'rgba(255, 255, 255, 0.05)',
    border: `1px solid rgba(255, 255, 255, 0.15)`,
    color: theme.colors.textSecondary,
    padding: '8px 16px',
    borderRadius: theme.borderRadius.lg,
    fontSize: '14px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  warningBanner: {
    background: 'linear-gradient(135deg, rgba(220, 38, 38, 0.95) 0%, rgba(245, 158, 11, 0.9) 100%)',
    color: '#fff',
    padding: '10px 20px',
    borderRadius: '0 0 16px 16px',
    boxShadow: '0 4px 20px rgba(220, 38, 38, 0.4)',
  },
  blockedBanner: {
    background: 'linear-gradient(135deg, rgba(185, 28, 28, 0.98) 0%, rgba(220, 38, 38, 0.95) 100%)',
    color: '#fff',
    padding: '10px 20px',
    borderRadius: '0 0 16px 16px',
    boxShadow: '0 4px 20px rgba(185, 28, 28, 0.5)',
  },
  bannerContent: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '16px',
    maxWidth: '1200px',
    margin: '0 auto',
  },
  bannerIconWrapper: {
    background: 'rgba(255, 255, 255, 0.2)',
    borderRadius: '12px',
    padding: '10px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bannerText: {
    textAlign: 'left' as const,
  },
  bannerTitle: {
    fontSize: '14px',
    fontWeight: 700,
    marginBottom: '2px',
  },
  bannerSubtitle: {
    fontSize: '13px',
    opacity: 0.9,
  },
  bannerTimer: {
    background: 'rgba(255, 255, 255, 0.2)',
    padding: '2px 8px',
    borderRadius: '8px',
    marginLeft: '8px',
    fontSize: '12px',
    fontWeight: 600,
  },
};
