import React, { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  KeyRound,
  Loader2,
  Mail,
  Monitor,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  LogOut,
} from 'lucide-react';
import { authApi, type AccountSession } from '../../api/authApi';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import type { User } from '../../types';
import { useToast } from '../Toast';
import { SettingsSection } from './SettingsSection';

interface SecuritySettingsProps {
  user: User;
}

const describeError = (error: unknown, fallback: string): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 401 || info.status === 403) {
    return 'Недостаточно прав или неверный текущий пароль.';
  }
  if (info.status === 409) {
    return 'Этот email уже используется другим аккаунтом.';
  }
  if (!info.status) {
    return 'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.';
  }
  if (info.message && !isGenericValidationMessage(info.message)) return info.message;
  return fallback;
};

const formatSessionMoment = (value?: string): string => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
};

const describeDevice = (session: AccountSession): string => {
  if (session.deviceName) return session.deviceName;
  const agent = session.userAgent ?? '';
  if (/mobile|android|iphone|ipad/i.test(agent)) return 'Мобильное устройство';
  if (agent) return 'Браузер';
  return 'Неизвестное устройство';
};

export const SecuritySettings: React.FC<SecuritySettingsProps> = ({ user }) => {
  const { showToast } = useToast();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const [newEmail, setNewEmail] = useState('');
  const [emailPassword, setEmailPassword] = useState('');
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [emailNotice, setEmailNotice] = useState<string | null>(null);
  const [verifyBusy, setVerifyBusy] = useState(false);

  const [sessions, setSessions] = useState<AccountSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [logoutAllBusy, setLogoutAllBusy] = useState(false);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    setSessionsError(null);
    try {
      setSessions(await authApi.getSessions());
    } catch (error) {
      setSessionsError(describeError(error, 'Не удалось загрузить активные сессии.'));
    } finally {
      setSessionsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  const handlePasswordSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (passwordBusy) return;

    if (newPassword.length < 8 || newPassword.length > 100) {
      setPasswordError('Новый пароль должен быть от 8 до 100 символов.');
      return;
    }
    if (!/[A-ZА-Я]/.test(newPassword) || !/[a-zа-я]/.test(newPassword) || !/\d/.test(newPassword)) {
      setPasswordError('Пароль должен содержать заглавную, строчную букву и цифру.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('Пароли не совпадают.');
      return;
    }

    setPasswordBusy(true);
    setPasswordError(null);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      showToast('Пароль изменён. Остальные сессии отозваны.', 'success');
      void loadSessions();
    } catch (error) {
      setPasswordError(describeError(error, 'Не удалось изменить пароль.'));
    } finally {
      setPasswordBusy(false);
    }
  };

  const handleEmailSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (emailBusy) return;

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newEmail.trim())) {
      setEmailError('Укажите корректный email.');
      return;
    }

    setEmailBusy(true);
    setEmailError(null);
    setEmailNotice(null);
    try {
      await authApi.requestEmailChange(newEmail.trim(), emailPassword);
      setEmailNotice(
        `Письмо для подтверждения отправлено на ${newEmail.trim()}. Адрес изменится после перехода по ссылке.`
      );
      setNewEmail('');
      setEmailPassword('');
      showToast('Запрос на смену email отправлен', 'success');
    } catch (error) {
      setEmailError(describeError(error, 'Не удалось запросить смену email.'));
    } finally {
      setEmailBusy(false);
    }
  };

  const handleVerifyEmail = async () => {
    if (verifyBusy) return;
    setVerifyBusy(true);
    setEmailError(null);
    try {
      await authApi.requestEmailVerification();
      setEmailNotice('Письмо с подтверждением email отправлено.');
      showToast('Письмо с подтверждением отправлено', 'success');
    } catch (error) {
      setEmailError(describeError(error, 'Не удалось отправить письмо подтверждения.'));
    } finally {
      setVerifyBusy(false);
    }
  };

  const handleRevoke = async (session: AccountSession) => {
    if (session.current || revokingId) return;
    setRevokingId(session.id);
    setSessionsError(null);
    try {
      await authApi.revokeSession(session.id);
      setSessions((current) => current.filter((item) => item.id !== session.id));
      showToast('Сессия завершена', 'success');
    } catch (error) {
      setSessionsError(describeError(error, 'Не удалось завершить сессию.'));
    } finally {
      setRevokingId(null);
    }
  };

  const handleLogoutAll = async () => {
    if (logoutAllBusy) return;
    setLogoutAllBusy(true);
    setSessionsError(null);
    try {
      await authApi.logoutAll();
      showToast('Все сессии завершены. Войдите снова.', 'success');
      window.location.assign('/login');
    } catch (error) {
      setSessionsError(describeError(error, 'Не удалось завершить все сессии.'));
      setLogoutAllBusy(false);
    }
  };

  return (
    <SettingsSection
      id="security"
      eyebrow="Вход и сессии"
      title="Безопасность"
      description="Пароль, подтверждение email и активные сессии. Пароль и токены никогда не отображаются в браузере повторно."
      icon={<ShieldCheck size={22} />}
    >
      <div className="settings-security-block">
        <h3 className="settings-security-title">
          <KeyRound size={17} aria-hidden="true" />
          Смена пароля
        </h3>
        <form className="settings-form" onSubmit={handlePasswordSubmit} noValidate>
          <div className="settings-form-grid">
            <div className="settings-field settings-field--wide">
              <label htmlFor="settings-current-password">Текущий пароль *</label>
              <input
                id="settings-current-password"
                type="password"
                value={currentPassword}
                onChange={(event) => {
                  setCurrentPassword(event.target.value);
                  setPasswordError(null);
                }}
                autoComplete="current-password"
                required
              />
            </div>
            <div className="settings-field">
              <label htmlFor="settings-new-password">Новый пароль *</label>
              <input
                id="settings-new-password"
                type="password"
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  setPasswordError(null);
                }}
                autoComplete="new-password"
                required
              />
              <p className="settings-field-hint">
                От 8 символов, заглавная и строчная буква, цифра.
              </p>
            </div>
            <div className="settings-field">
              <label htmlFor="settings-confirm-password">Повторите пароль *</label>
              <input
                id="settings-confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(event) => {
                  setConfirmPassword(event.target.value);
                  setPasswordError(null);
                }}
                autoComplete="new-password"
                required
              />
            </div>
          </div>

          {passwordError && (
            <div className="settings-alert" role="alert">
              <AlertTriangle size={17} aria-hidden="true" />
              <span>{passwordError}</span>
            </div>
          )}

          <div className="settings-form-footer">
            <span className="settings-field-hint">
              После смены пароля остальные сессии будут отозваны.
            </span>
            <div className="settings-form-actions">
              <button type="submit" className="settings-button settings-button--primary" disabled={passwordBusy}>
                {passwordBusy ? (
                  <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
                ) : (
                  <KeyRound size={16} aria-hidden="true" />
                )}
                {passwordBusy ? 'Меняем…' : 'Изменить пароль'}
              </button>
            </div>
          </div>
        </form>
      </div>

      <div className="settings-security-block">
        <h3 className="settings-security-title">
          <Mail size={17} aria-hidden="true" />
          Email для входа
        </h3>
        <dl className="settings-account-grid">
          <div className="settings-account-item">
            <dt>Текущий адрес</dt>
            <dd>{user.email}</dd>
          </div>
          <div className="settings-account-item">
            <dt>Подтверждение</dt>
            <dd>
              {user.emailVerified === undefined
                ? 'Статус не передан сервером'
                : user.emailVerified
                  ? 'Email подтверждён'
                  : 'Email не подтверждён'}
            </dd>
          </div>
        </dl>

        {!user.emailVerified && (
          <button
            type="button"
            className="settings-button settings-button--secondary"
            onClick={() => void handleVerifyEmail()}
            disabled={verifyBusy}
          >
            {verifyBusy ? (
              <Loader2 className="settings-spinner" size={15} aria-hidden="true" />
            ) : (
              <Mail size={15} aria-hidden="true" />
            )}
            Отправить письмо с подтверждением
          </button>
        )}

        <form className="settings-form" onSubmit={handleEmailSubmit} noValidate>
          <div className="settings-form-grid">
            <div className="settings-field">
              <label htmlFor="settings-new-email">Новый email *</label>
              <input
                id="settings-new-email"
                type="email"
                value={newEmail}
                onChange={(event) => {
                  setNewEmail(event.target.value);
                  setEmailError(null);
                  setEmailNotice(null);
                }}
                autoComplete="email"
                required
              />
            </div>
            <div className="settings-field">
              <label htmlFor="settings-email-password">Текущий пароль *</label>
              <input
                id="settings-email-password"
                type="password"
                value={emailPassword}
                onChange={(event) => {
                  setEmailPassword(event.target.value);
                  setEmailError(null);
                  setEmailNotice(null);
                }}
                autoComplete="current-password"
                required
              />
            </div>
          </div>

          {emailError && (
            <div className="settings-alert" role="alert">
              <AlertTriangle size={17} aria-hidden="true" />
              <span>{emailError}</span>
            </div>
          )}
          {emailNotice && (
            <div className="settings-success" role="status">
              <CheckCircle2 size={17} aria-hidden="true" />
              <span>{emailNotice}</span>
            </div>
          )}

          <div className="settings-form-footer">
            <span className="settings-field-hint">
              Новый адрес вступит в силу только после подтверждения по ссылке.
            </span>
            <div className="settings-form-actions">
              <button type="submit" className="settings-button settings-button--primary" disabled={emailBusy}>
                {emailBusy ? (
                  <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
                ) : (
                  <Mail size={16} aria-hidden="true" />
                )}
                {emailBusy ? 'Отправляем…' : 'Сменить email'}
              </button>
            </div>
          </div>
        </form>
      </div>

      <div className="settings-security-block">
        <h3 className="settings-security-title">
          <Monitor size={17} aria-hidden="true" />
          Активные сессии
        </h3>

        {sessionsLoading ? (
          <div className="settings-inline-loading" role="status">
            <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
            Загружаем сессии…
          </div>
        ) : sessionsError ? (
          <div className="settings-alert" role="alert">
            <AlertTriangle size={17} aria-hidden="true" />
            <span>{sessionsError}</span>
            <button
              type="button"
              className="settings-button settings-button--secondary"
              onClick={() => void loadSessions()}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Повторить
            </button>
          </div>
        ) : sessions.length === 0 ? (
          <p className="settings-field-hint">Активных сессий нет.</p>
        ) : (
          <ul className="settings-session-list">
            {sessions.map((session) => (
              <li key={session.id} className="settings-session">
                <span className="settings-session-icon" aria-hidden="true">
                  {/mobile|android|iphone|ipad/i.test(session.userAgent ?? '') ? (
                    <Smartphone size={18} />
                  ) : (
                    <Monitor size={18} />
                  )}
                </span>
                <div className="settings-session-copy">
                  <strong>
                    {describeDevice(session)}
                    {session.current && <span className="settings-session-current">эта сессия</span>}
                  </strong>
                  <small>
                    {session.ipAddress ? `${session.ipAddress} · ` : ''}
                    последняя активность {formatSessionMoment(session.lastUsedAt ?? session.createdAt)}
                  </small>
                  <small>истекает {formatSessionMoment(session.expiresAt)}</small>
                </div>
                <button
                  type="button"
                  className="settings-button settings-button--ghost"
                  onClick={() => void handleRevoke(session)}
                  disabled={session.current || revokingId === session.id}
                  title={session.current ? 'Текущую сессию нельзя отозвать здесь' : 'Завершить сессию'}
                >
                  {revokingId === session.id ? (
                    <Loader2 className="settings-spinner" size={15} aria-hidden="true" />
                  ) : (
                    <LogOut size={15} aria-hidden="true" />
                  )}
                  Завершить
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="settings-inline-actions">
          <button
            type="button"
            className="settings-button settings-button--secondary"
            onClick={() => void loadSessions()}
            disabled={sessionsLoading}
          >
            <RefreshCw size={15} aria-hidden="true" />
            Обновить список
          </button>
          <button
            type="button"
            className="settings-button settings-button--danger"
            onClick={() => void handleLogoutAll()}
            disabled={logoutAllBusy}
          >
            {logoutAllBusy ? (
              <Loader2 className="settings-spinner" size={15} aria-hidden="true" />
            ) : (
              <LogOut size={15} aria-hidden="true" />
            )}
            Завершить все сессии
          </button>
        </div>
      </div>
    </SettingsSection>
  );
};
