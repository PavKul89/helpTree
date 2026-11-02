import React, { useEffect, useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  ClipboardList,
  LayoutDashboard,
  Leaf,
  LogOut,
  Menu,
  ShieldCheck,
  Star,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { AdminAvatar } from './AdminAvatar';
import '../../pages/admin/admin.css';

interface AdminNavItem {
  to: string;
  label: string;
  description: string;
  icon: React.ElementType;
  end?: boolean;
}

const NAV_ITEMS: AdminNavItem[] = [
  {
    to: '/admin',
    label: 'Обзор',
    description: 'Сводные показатели',
    icon: LayoutDashboard,
    end: true,
  },
  {
    to: '/admin/users',
    label: 'Пользователи',
    description: 'Профили и операции',
    icon: Users,
  },
  {
    to: '/admin/requests',
    label: 'Заявки',
    description: 'Публикации помощи',
    icon: ClipboardList,
  },
  {
    to: '/admin/reviews',
    label: 'Отзывы',
    description: 'Отзывы получателей',
    icon: Star,
  },
];

const getSectionMeta = (pathname: string): { title: string; description: string } => {
  if (pathname === '/admin') {
    return {
      title: 'Обзор',
      description: 'Показатели, которые возвращают существующие API статистики.',
    };
  }
  if (pathname.startsWith('/admin/users/')) {
    return {
      title: 'Карточка пользователя',
      description: 'Профиль, рейтинг, кошелёк и связанные данные.',
    };
  }
  if (pathname.startsWith('/admin/users')) {
    return {
      title: 'Пользователи',
      description: 'Активные аккаунты, роли, статусы и административные операции.',
    };
  }
  if (pathname.startsWith('/admin/requests')) {
    return {
      title: 'Заявки',
      description: 'Публикации помощи с серверной пагинацией и фильтрами.',
    };
  }
  if (pathname.startsWith('/admin/reviews')) {
    return {
      title: 'Отзывы',
      description: 'Отзывы, полученные выбранным пользователем.',
    };
  }
  return {
    title: 'Админ-панель',
    description: 'Управление сервисом HelpTree.',
  };
};

export const AdminLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const section = useMemo(() => getSectionMeta(location.pathname), [location.pathname]);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    document.title = `HelpTree · Админ-панель — ${section.title}`;
    return () => {
      document.title = 'HelpTree';
    };
  }, [section.title]);

  useEffect(() => {
    if (!mobileOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileOpen(false);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [mobileOpen]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="admin-shell">
      <a className="admin-skip-link" href="#admin-main">
        Перейти к содержанию
      </a>

      {mobileOpen && (
        <button
          type="button"
          className="admin-sidebar-overlay"
          aria-label="Закрыть меню"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside
        id="admin-sidebar"
        className={`admin-sidebar${mobileOpen ? ' is-open' : ''}`}
        aria-label="Административная навигация"
      >
        <div className="admin-sidebar-head">
          <Link to="/admin" className="admin-brand" aria-label="HelpTree Admin — обзор">
            <span className="admin-brand-mark" aria-hidden="true">
              <Leaf size={20} strokeWidth={1.8} />
            </span>
            <span className="admin-brand-copy">
              <strong>
                help<span>Tree</span>
              </strong>
              <small>Admin</small>
            </span>
          </Link>
          <button
            type="button"
            className="admin-icon-btn admin-sidebar-close"
            onClick={() => setMobileOpen(false)}
            aria-label="Закрыть меню"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <nav className="admin-nav" aria-label="Разделы админ-панели">
          <span className="admin-nav-title">Управление</span>
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `admin-nav-item${isActive ? ' is-active' : ''}`}
              >
                <span className="admin-nav-icon" aria-hidden="true">
                  <Icon size={18} />
                </span>
                <span className="admin-nav-copy">
                  <strong>{item.label}</strong>
                  <small>{item.description}</small>
                </span>
              </NavLink>
            );
          })}
        </nav>

        <div className="admin-sidebar-note" role="note">
          <ShieldCheck size={16} aria-hidden="true" />
          <p>
            Доступен только бэкенд-роль <strong>ADMIN</strong>. Роли модератора, жалоб, журнала
            действий и глобальных списков комментариев/отзывов в API нет, поэтому такие разделы не
            создавались.
          </p>
        </div>

        {user && (
          <div className="admin-sidebar-account">
            <AdminAvatar name={user.name} avatarUrl={user.avatarUrl} size="small" />
            <div>
              <strong>{user.name}</strong>
              <span>{user.email}</span>
            </div>
            <span className="admin-role-badge">ADMIN</span>
          </div>
        )}
      </aside>

      <div className="admin-main-column">
        <header className="admin-header">
          <div className="admin-header-left">
            <button
              type="button"
              className="admin-icon-btn admin-menu-toggle"
              onClick={() => setMobileOpen(true)}
              aria-label="Открыть меню"
              aria-controls="admin-sidebar"
              aria-expanded={mobileOpen}
            >
              <Menu size={20} aria-hidden="true" />
            </button>
            <div className="admin-header-copy">
              <span className="admin-eyebrow">Админ-панель</span>
              <h1>{section.title}</h1>
              <p>{section.description}</p>
            </div>
          </div>

          <div className="admin-header-actions">
            <Link to="/" className="admin-button admin-button--ghost admin-header-site-link">
              <ArrowLeft size={16} aria-hidden="true" />
              На сайт
            </Link>
            {user && (
              <span className="admin-admin-chip" title={`${user.name} · ${user.email}`}>
                <AdminAvatar name={user.name} avatarUrl={user.avatarUrl} size="small" />
                <span>
                  <strong>{user.name}</strong>
                  <small>администратор</small>
                </span>
              </span>
            )}
            <button
              type="button"
              className="admin-icon-btn admin-header-logout"
              onClick={handleLogout}
              aria-label="Выйти из аккаунта"
              title="Выйти"
            >
              <LogOut size={18} aria-hidden="true" />
            </button>
          </div>
        </header>

        <main id="admin-main" className="admin-main" tabIndex={-1}>
          <div className="admin-page">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};
