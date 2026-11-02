import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  Camera,
  RefreshCw,
  Settings2,
  UserCog,
  UserRound,
} from 'lucide-react';
import { authApi } from '../api/authApi';
import type { User } from '../types';
import { getApiErrorInfo } from '../utils/apiErrors';
import { useAuth } from '../context/AuthContext';
import { Avatar } from '../components/Avatar';
import {
  SettingsNavigation,
  type SettingsNavigationItem,
  type SettingsSectionId,
} from '../components/settings/SettingsNavigation';
import { SettingsSkeleton } from '../components/settings/SettingsSkeleton';
import { ProfileSettings } from '../components/settings/ProfileSettings';
import { AvatarSettings } from '../components/settings/AvatarSettings';
import { AccountSettings } from '../components/settings/AccountSettings';
import { ConfirmDialog } from '../components/settings/ConfirmDialog';
import './SettingsPage.css';

const SECTION_ITEMS: SettingsNavigationItem[] = [
  {
    id: 'profile',
    label: 'Профиль',
    description: 'Имя, контакты и город',
    icon: <UserRound size={18} />,
  },
  {
    id: 'avatar',
    label: 'Аватар',
    description: 'Фотография профиля',
    icon: <Camera size={18} />,
  },
  {
    id: 'account',
    label: 'Аккаунт',
    description: 'Данные и удаление',
    icon: <UserCog size={18} />,
  },
];

const EMPTY_DIRTY_SECTIONS: Record<SettingsSectionId, boolean> = {
  profile: false,
  avatar: false,
  account: false,
};

type LoadState = 'loading' | 'error' | 'ready';

export const SettingsPage: React.FC = () => {
  const { setUser, logout } = useAuth();
  const navigate = useNavigate();
  const logoutRef = useRef(logout);

  const [userData, setUserData] = useState<User | null>(null);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [loadError, setLoadError] = useState('');
  const [activeSection, setActiveSection] = useState<SettingsSectionId>('profile');
  const [dirtySections, setDirtySections] =
    useState<Record<SettingsSectionId, boolean>>(EMPTY_DIRTY_SECTIONS);
  const [pendingSection, setPendingSection] = useState<SettingsSectionId | null>(null);

  useEffect(() => {
    logoutRef.current = logout;
  }, [logout]);

  const loadSettings = useCallback(async () => {
    setLoadState('loading');
    setLoadError('');

    try {
      const currentUser = await authApi.getCurrentUser();
      setUserData(currentUser);
      setUser(currentUser);
      setLoadState('ready');
    } catch (error: unknown) {
      const info = getApiErrorInfo(error);

      if (info.status === 401 || info.status === 403) {
        logoutRef.current();
        navigate('/login', { replace: true });
        return;
      }

      if (!info.status) {
        setLoadError(
          'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.'
        );
      } else {
        setLoadError('Сервер не смог загрузить настройки. Попробуйте ещё раз чуть позже.');
      }

      setLoadState('error');
    }
  }, [navigate, setUser]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const setSectionDirty = useCallback((section: SettingsSectionId, dirty: boolean) => {
    setDirtySections((current) =>
      current[section] === dirty ? current : { ...current, [section]: dirty }
    );
  }, []);

  const handleProfileDirty = useCallback(
    (dirty: boolean) => setSectionDirty('profile', dirty),
    [setSectionDirty]
  );
  const handleAvatarDirty = useCallback(
    (dirty: boolean) => setSectionDirty('avatar', dirty),
    [setSectionDirty]
  );

  const handleUserUpdated = useCallback(
    (updatedUser: User) => {
      setUserData(updatedUser);
      setUser(updatedUser);
    },
    [setUser]
  );

  const handleDeleted = useCallback(() => {
    logoutRef.current();
    navigate('/login', { replace: true });
  }, [navigate]);

  const hasUnsavedChanges = useMemo(
    () => Object.values(dirtySections).some(Boolean),
    [dirtySections]
  );

  useEffect(() => {
    if (!hasUnsavedChanges) return;

    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [hasUnsavedChanges]);

  useEffect(() => {
    if (loadState !== 'ready') return;

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(`settings-${activeSection}-title`)
        ?.focus({ preventScroll: true });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [activeSection, loadState]);

  const handleSelectSection = (nextSection: SettingsSectionId) => {
    if (nextSection === activeSection) return;

    if (dirtySections[activeSection]) {
      setPendingSection(nextSection);
      return;
    }

    setActiveSection(nextSection);
  };

  const handleDiscardAndSwitch = () => {
    if (!pendingSection) return;

    setDirtySections((current) => ({ ...current, [activeSection]: false }));
    setActiveSection(pendingSection);
    setPendingSection(null);
  };

  if (loadState === 'loading') {
    return <SettingsSkeleton />;
  }

  if (loadState === 'error' || !userData) {
    return (
      <main className="settings-page">
        <div className="settings-error-state" role="alert">
          <span className="settings-error-icon" aria-hidden="true">
            <AlertTriangle size={26} />
          </span>
          <h1>Не удалось загрузить настройки</h1>
          <p>{loadError || 'Проверьте подключение и повторите попытку.'}</p>
          <div className="settings-error-actions">
            <button
              type="button"
              className="settings-button settings-button--primary"
              onClick={() => void loadSettings()}
            >
              <RefreshCw size={16} aria-hidden="true" />
              Повторить
            </button>
            <Link to="/profile" className="settings-button settings-button--secondary">
              Вернуться в профиль
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const activeSectionLabel =
    SECTION_ITEMS.find((item) => item.id === activeSection)?.label ?? 'настройках';

  return (
    <main className="settings-page">
      <nav className="settings-breadcrumb" aria-label="Навигационная цепочка">
        <Link to="/">Главная</Link>
        <span aria-hidden="true">/</span>
        <Link to="/profile">Мой профиль</Link>
        <span aria-hidden="true">/</span>
        <span>Настройки</span>
      </nav>

      <header className="settings-hero">
        <div className="settings-hero-copy">
          <span className="settings-eyebrow">
            <Settings2 size={13} aria-hidden="true" />
            HelpTree · Личный кабинет
          </span>
          <h1>Настройки</h1>
          <p>
            Управляйте личными данными, фотографией профиля и доступом к аккаунту.
            Изменения сохраняются на сервере и сразу синхронизируются с приложением.
          </p>
        </div>

        <div className="settings-hero-account" aria-label="Текущий аккаунт">
          <Avatar name={userData.name} avatarUrl={userData.avatarUrl} size="medium" />
          <div className="settings-hero-account-copy">
            <strong>{userData.name}</strong>
            <span>{userData.email}</span>
          </div>
          <span className="settings-hero-role">
            {userData.role === 'ADMIN' ? 'Администратор' : 'Участник'}
          </span>
        </div>
      </header>

      <div className="settings-layout">
        <SettingsNavigation
          items={SECTION_ITEMS}
          activeSection={activeSection}
          dirtySections={dirtySections}
          onSelect={handleSelectSection}
        />

        <div className="settings-content">
          {activeSection === 'profile' && (
            <ProfileSettings
              user={userData}
              onSaved={handleUserUpdated}
              onDirtyChange={handleProfileDirty}
            />
          )}

          {activeSection === 'avatar' && (
            <AvatarSettings
              user={userData}
              onUpdated={handleUserUpdated}
              onDirtyChange={handleAvatarDirty}
            />
          )}

          {activeSection === 'account' && (
            <AccountSettings user={userData} onDeleted={handleDeleted} />
          )}
        </div>
      </div>

      <ConfirmDialog
        isOpen={Boolean(pendingSection)}
        title="Несохранённые изменения"
        description={`Изменения в разделе «${activeSectionLabel}» не сохранены. Если перейти в другой раздел, они будут потеряны.`}
        confirmLabel="Перейти без сохранения"
        cancelLabel="Остаться"
        tone="warning"
        onConfirm={handleDiscardAndSwitch}
        onClose={() => setPendingSection(null)}
      />
    </main>
  );
};
