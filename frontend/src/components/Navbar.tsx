import React, { useEffect, useState, useRef, useCallback } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import { WALLET_UPDATED_EVENT, walletApi } from '../api/walletApi';
import { useWebSocketChat } from '../hooks/useWebSocketChat';
import {
  Map,
  GitBranch,
  Trophy,
  ClipboardList,
  User,
  Star,
  Package,
  MessageCircle,
  Bell,
  AlertTriangle,
  Ban,
  Plus,
  Coins,
  ChevronDown,
  Leaf,
  LogOut,
  Menu,
  Settings2,
  ShieldCheck,
  X,
} from 'lucide-react';
import type { Chat } from '../types';
import './Navbar.css';

const getInitials = (name: string): string =>
  name
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [chats, setChats] = useState<Chat[]>([]);
  const [debtWarning, setDebtWarning] = useState<number | null>(null);
  const [isBlocked, setIsBlocked] = useState(false);
  const [daysUntilBlock, setDaysUntilBlock] = useState<number | null>(null);
  const [openDropdown, setOpenDropdown] = useState<string | null>(null);
  const [helpCoins, setHelpCoins] = useState<number>(0);
  const [isVip, setIsVip] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const handleChatUpdated = useCallback((updatedChat: Chat) => {
    setChats((prev) => {
      const existing = prev.find((c) => c.id === updatedChat.id);
      if (existing) {
        return prev.map((c) => (c.id === updatedChat.id ? updatedChat : c));
      }
      return [updatedChat, ...prev];
    });
  }, []);

  const handleChatDeleted = useCallback((chatId: number) => {
    setChats((prev) => prev.filter((c) => c.id !== chatId));
  }, []);

  useWebSocketChat({
    userId: user?.id ?? null,
    onNewMessage: () => {},
    onMessageDeleted: () => {},
    onMessagesRead: () => {},
    onChatDeleted: handleChatDeleted,
    onChatUpdated: handleChatUpdated,
  });

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
    setOpenDropdown(null);
    setMobileOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (user) {
      loadInitialChats();
      loadUserData();
      loadWallet();
    }
  }, [user]);

  useEffect(() => {
    const handleWalletUpdated = (event: Event) => {
      const balance = (event as CustomEvent<{ balance?: number }>).detail?.balance;
      if (typeof balance === 'number' && Number.isFinite(balance)) {
        setHelpCoins(balance);
      } else if (user) {
        walletApi
          .getWallet(user.id)
          .then((wallet) => setHelpCoins(wallet.balance))
          .catch(console.error);
      }
    };

    window.addEventListener(WALLET_UPDATED_EVENT, handleWalletUpdated);
    return () => window.removeEventListener(WALLET_UPDATED_EVENT, handleWalletUpdated);
  }, [user]);

  const loadInitialChats = async () => {
    try {
      const data = await chatApi.getChats();
      setChats(data);
    } catch (err) {
      console.error(err);
    }
  };

  const loadUserData = async () => {
    try {
      const userData = await authApi.getCurrentUser();
      if (userData.blockedUntil) {
        setIsBlocked(true);
        setDebtWarning(null);
        const hoursLeft = (new Date(userData.blockedUntil).getTime() - Date.now()) / (1000 * 60 * 60);
        setDaysUntilBlock(Math.max(0, hoursLeft));
      } else if ('blockedAt' in userData && userData.blockedAt) {
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

      if (userData.helpCoins !== undefined) {
        setHelpCoins(userData.helpCoins);
      }

      if (userData.vipUntil && new Date(userData.vipUntil) > new Date()) {
        setIsVip(true);
      } else {
        setIsVip(false);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const loadWallet = async () => {
    if (user) {
      try {
        const wallet = await walletApi.getWallet(user.id);
        setHelpCoins(wallet.balance);
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleLogout = () => {
    logout();
    setMobileOpen(false);
    navigate('/login');
  };

  const toggleDropdown = (name: string) => {
    if (openDropdown === name) {
      setOpenDropdown(null);
    } else {
      setOpenDropdown(name);
      if (name === 'profile' && user) {
        walletApi.getWallet(user.id)
          .then(wallet => setHelpCoins(wallet.balance))
          .catch(console.error);
      }
    }
  };

  const closeDropdown = () => {
    setOpenDropdown(null);
    setMobileOpen(false);
  };

  const isActive = (path: string): boolean => {
    if (path === '/') return location.pathname === '/';
    return location.pathname === path || location.pathname.startsWith(`${path}/`);
  };

  const unreadChats = chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  const communityLinks = (
    <>
      <Link to="/map" className="navbar-dropdown-item" onClick={closeDropdown}>
        <Map size={16} aria-hidden="true" /> Карта
      </Link>
      <Link to="/graph" className="navbar-dropdown-item" onClick={closeDropdown}>
        <GitBranch size={16} aria-hidden="true" /> Граф помощи
      </Link>
      <Link to="/achievements" className="navbar-dropdown-item" onClick={closeDropdown}>
        <Trophy size={16} aria-hidden="true" /> Достижения
      </Link>
      {user && (
        <Link to="/activity" className="navbar-dropdown-item" onClick={closeDropdown}>
          <ClipboardList size={16} aria-hidden="true" /> Активность
        </Link>
      )}
    </>
  );

  return (
    <nav className="navbar">
      <div className="navbar-container">
        <Link to="/" className="navbar-logo" onClick={closeDropdown} aria-label="HelpTree — на главную">
          <span className="navbar-logo-mark" aria-hidden="true">
            <Leaf size={20} strokeWidth={1.8} />
          </span>
          <span className="navbar-logo-text">
            help<span>Tree</span>
          </span>
        </Link>

        <div className="navbar-links" ref={dropdownRef}>
          <Link to="/" className={`navbar-link ${isActive('/') ? 'navbar-link--active' : ''}`}>
            Главная
          </Link>
          <Link to="/posts" className={`navbar-link ${isActive('/posts') ? 'navbar-link--active' : ''}`}>
            Заявки
          </Link>

          <div className="navbar-dropdown-wrapper">
            <button
              type="button"
              className={`navbar-link navbar-dropdown-trigger ${openDropdown === 'community' ? 'navbar-link--active' : ''}`}
              onClick={() => toggleDropdown('community')}
              aria-haspopup="true"
              aria-expanded={openDropdown === 'community'}
            >
              Сообщество
              <ChevronDown size={15} aria-hidden="true" />
            </button>
            {openDropdown === 'community' && (
              <div className="navbar-dropdown">{communityLinks}</div>
            )}
          </div>

          {user && (
            <div className="navbar-dropdown-wrapper">
              <button
                type="button"
                className={`navbar-link navbar-profile-trigger ${openDropdown === 'profile' ? 'navbar-link--active' : ''}`}
                onClick={() => toggleDropdown('profile')}
                aria-haspopup="true"
                aria-expanded={openDropdown === 'profile'}
              >
                <span
                  className="navbar-avatar"
                  style={{
                    background: user.avatarUrl
                      ? `url(${user.avatarUrl}) center / cover no-repeat`
                      : undefined,
                  }}
                >
                  {!user.avatarUrl && getInitials(user.name)}
                </span>
                <span className="navbar-profile-name">{user.name}</span>
                {isVip && <span className="navbar-vip" title="VIP-статус">★</span>}
                <ChevronDown size={15} aria-hidden="true" />
              </button>
              {openDropdown === 'profile' && (
                <div className="navbar-dropdown navbar-dropdown--profile">
                  <Link to="/profile" className="navbar-dropdown-item" onClick={closeDropdown}>
                    <User size={16} aria-hidden="true" /> Мой профиль
                  </Link>
                  <Link to="/settings" className="navbar-dropdown-item" onClick={closeDropdown}>
                    <Settings2 size={16} aria-hidden="true" /> Настройки
                  </Link>
                  {user.role === 'ADMIN' && (
                    <Link
                      to="/admin"
                      className="navbar-dropdown-item navbar-dropdown-item--admin"
                      onClick={closeDropdown}
                    >
                      <ShieldCheck size={16} aria-hidden="true" /> Админ-панель
                    </Link>
                  )}
                  <Link to="/favorites" className="navbar-dropdown-item" onClick={closeDropdown}>
                    <Star size={16} aria-hidden="true" /> Избранное
                  </Link>
                  <Link to="/my-orders" className="navbar-dropdown-item" onClick={closeDropdown}>
                    <Package size={16} aria-hidden="true" /> Мои заказы
                  </Link>
                  <Link to="/chats" className="navbar-dropdown-item" onClick={closeDropdown}>
                    <MessageCircle size={16} aria-hidden="true" /> Чаты
                    {unreadChats > 0 && <span className="navbar-badge">{unreadChats}</span>}
                  </Link>
                  <Link to="/wallet" className="navbar-dropdown-item navbar-dropdown-item--wallet" onClick={closeDropdown}>
                    <Coins size={16} aria-hidden="true" />
                    <span className="navbar-wallet-value">{helpCoins}</span>
                    <span className="navbar-wallet-unit">HC</span>
                    <span className="navbar-wallet-link">Кошелёк →</span>
                  </Link>
                  <button type="button" className="navbar-dropdown-item navbar-dropdown-item--logout" onClick={handleLogout}>
                    <LogOut size={16} aria-hidden="true" /> Выйти
                  </button>
                </div>
              )}
            </div>
          )}

          {user && (
            <Link
              to="/notifications"
              className={`navbar-notifications${isActive('/notifications') ? ' is-active' : ''}`}
              aria-label="Центр уведомлений"
              title="Уведомления"
            >
              <Bell size={18} aria-hidden="true" />
            </Link>
          )}

          <div className="navbar-actions">
            {user ? (
              <Link to="/posts/new" className="navbar-btn navbar-btn--primary">
                <Plus size={16} aria-hidden="true" />
                Создать заявку
              </Link>
            ) : (
              <>
                <Link to="/login" className="navbar-btn navbar-btn--ghost">
                  Войти
                </Link>
                <Link to="/register" className="navbar-btn navbar-btn--primary">
                  Регистрация
                </Link>
              </>
            )}
          </div>
        </div>

        <button
          type="button"
          className="navbar-mobile-toggle"
          onClick={() => setMobileOpen((open) => !open)}
          aria-label={mobileOpen ? 'Закрыть меню' : 'Открыть меню'}
          aria-expanded={mobileOpen}
        >
          {mobileOpen ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
        </button>
      </div>

      {mobileOpen && (
        <div className="navbar-mobile">
          <Link to="/" className={`navbar-mobile-link ${isActive('/') ? 'navbar-mobile-link--active' : ''}`} onClick={closeDropdown}>
            Главная
          </Link>
          <Link to="/posts" className={`navbar-mobile-link ${isActive('/posts') ? 'navbar-mobile-link--active' : ''}`} onClick={closeDropdown}>
            Заявки
          </Link>
          <Link to="/map" className="navbar-mobile-link" onClick={closeDropdown}>
            <Map size={16} aria-hidden="true" /> Карта
          </Link>
          <Link to="/graph" className="navbar-mobile-link" onClick={closeDropdown}>
            <GitBranch size={16} aria-hidden="true" /> Граф помощи
          </Link>
          <Link to="/achievements" className="navbar-mobile-link" onClick={closeDropdown}>
            <Trophy size={16} aria-hidden="true" /> Достижения
          </Link>
          {user && (
            <>
              <Link to="/activity" className="navbar-mobile-link" onClick={closeDropdown}>
                <ClipboardList size={16} aria-hidden="true" /> Активность
              </Link>
              <Link to="/notifications" className={`navbar-mobile-link ${isActive('/notifications') ? 'navbar-mobile-link--active' : ''}`} onClick={closeDropdown}>
                <Bell size={16} aria-hidden="true" /> Уведомления
              </Link>
              <Link to="/profile" className="navbar-mobile-link" onClick={closeDropdown}>
                <User size={16} aria-hidden="true" /> Мой профиль
              </Link>
              <Link to="/settings" className="navbar-mobile-link" onClick={closeDropdown}>
                <Settings2 size={16} aria-hidden="true" /> Настройки
              </Link>
              {user.role === 'ADMIN' && (
                <Link
                  to="/admin"
                  className="navbar-mobile-link navbar-mobile-link--admin"
                  onClick={closeDropdown}
                >
                  <ShieldCheck size={16} aria-hidden="true" /> Админ-панель
                </Link>
              )}
              <Link to="/favorites" className="navbar-mobile-link" onClick={closeDropdown}>
                <Star size={16} aria-hidden="true" /> Избранное
              </Link>
              <Link to="/my-orders" className="navbar-mobile-link" onClick={closeDropdown}>
                <Package size={16} aria-hidden="true" /> Мои заказы
              </Link>
              <Link to="/chats" className="navbar-mobile-link" onClick={closeDropdown}>
                <MessageCircle size={16} aria-hidden="true" /> Чаты
                {unreadChats > 0 && <span className="navbar-badge">{unreadChats}</span>}
              </Link>
              <Link to="/wallet" className="navbar-mobile-link" onClick={closeDropdown}>
                <Coins size={16} aria-hidden="true" /> Кошелёк · {helpCoins} HC
              </Link>
            </>
          )}
          <div className="navbar-mobile-actions">
            {user ? (
              <>
                <Link to="/posts/new" className="navbar-btn navbar-btn--primary" onClick={closeDropdown}>
                  <Plus size={16} aria-hidden="true" /> Создать заявку
                </Link>
                <button type="button" className="navbar-btn navbar-btn--ghost" onClick={handleLogout}>
                  Выйти
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="navbar-btn navbar-btn--ghost" onClick={closeDropdown}>
                  Войти
                </Link>
                <Link to="/register" className="navbar-btn navbar-btn--primary" onClick={closeDropdown}>
                  Регистрация
                </Link>
              </>
            )}
          </div>
        </div>
      )}

      {isBlocked && (
        <div className="navbar-banner navbar-banner--blocked">
          <Ban size={16} aria-hidden="true" />
          <span>
            Ваш аккаунт заблокирован за долг. Помогите другим или разблокируйте за 100 HC.
            {daysUntilBlock !== null && daysUntilBlock > 0 && (
              daysUntilBlock >= 24
                ? ` Осталось ${Math.floor(daysUntilBlock / 24)} дней и ${Math.floor(daysUntilBlock % 24)} часов.`
                : ` Осталось ${Math.floor(daysUntilBlock)} часов.`
            )}
            {daysUntilBlock !== null && daysUntilBlock <= 0 && ' Ожидайте разблокировки.'}
          </span>
        </div>
      )}
      {debtWarning && debtWarning > 2 && !isBlocked && (
        <div className="navbar-banner navbar-banner--warning">
          <AlertTriangle size={16} aria-hidden="true" />
          <span>
            Внимание! Ваш долг: {debtWarning}. Помогите {debtWarning - 2} людям, чтобы избежать блокировки.
          </span>
        </div>
      )}
    </nav>
  );
};
