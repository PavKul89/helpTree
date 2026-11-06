import React, { useState } from 'react';
import {
  AlertTriangle,
  CalendarDays,
  Download,
  KeyRound,
  Loader2,
  Mail,
  ShieldAlert,
  Trash2,
  UserRound,
} from 'lucide-react';
import { authApi } from '../../api/authApi';
import type { User } from '../../types';
import { formatDate } from '../../utils/dateUtils';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import { useToast } from '../Toast';
import { ConfirmDialog } from './ConfirmDialog';
import { SettingsSection } from './SettingsSection';

interface AccountSettingsProps {
  user: User;
  onDeleted: () => void;
}

const describeError = (error: unknown, fallback: string): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 401 || info.status === 403) {
    return 'Недостаточно прав или неверный пароль.';
  }
  if (info.status === 404) {
    return 'Аккаунт уже удалён или недоступен.';
  }
  if (!info.status) {
    return 'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.';
  }
  if (info.message && !isGenericValidationMessage(info.message)) return info.message;
  return fallback;
};

export const AccountSettings: React.FC<AccountSettingsProps> = ({ user, onDeleted }) => {
  const { showToast } = useToast();

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [anonymizeOpen, setAnonymizeOpen] = useState(false);
  const [anonymizePassword, setAnonymizePassword] = useState('');
  const [anonymizeReason, setAnonymizeReason] = useState('');
  const [anonymizing, setAnonymizing] = useState(false);
  const [anonymizeError, setAnonymizeError] = useState<string | null>(null);

  const handleExport = async () => {
    if (exporting) return;
    setExporting(true);
    setExportError(null);

    try {
      const payload = await authApi.exportAccountData();
      const blob = new Blob([JSON.stringify(payload, null, 2)], {
        type: 'application/json;charset=utf-8',
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `helptree-account-${user.id}-export.json`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      showToast('Экспорт данных скачан', 'success');
    } catch (error: unknown) {
      setExportError(
        describeError(error, 'Не удалось подготовить экспорт данных. Попробуйте ещё раз.')
      );
    } finally {
      setExporting(false);
    }
  };

  const handleDelete = async () => {
    if (deleting) return;

    setDeleting(true);
    setDeleteError(null);

    try {
      await authApi.deleteAccount(user.id);
      showToast('Аккаунт удалён', 'success');
      onDeleted();
    } catch (error: unknown) {
      setDeleteError(describeError(error, 'Не удалось удалить аккаунт. Попробуйте ещё раз.'));
      setDeleting(false);
    }
  };

  const handleAnonymize = async () => {
    if (anonymizing) return;
    if (!anonymizePassword) {
      setAnonymizeError('Введите текущий пароль, чтобы подтвердить удаление данных.');
      return;
    }

    setAnonymizing(true);
    setAnonymizeError(null);

    try {
      await authApi.anonymizeAccount(anonymizePassword, anonymizeReason.trim() || undefined);
      showToast('Данные анонимизированы, все сессии завершены', 'success');
      onDeleted();
    } catch (error: unknown) {
      setAnonymizeError(
        describeError(error, 'Не удалось анонимизировать аккаунт. Проверьте пароль.')
      );
      setAnonymizing(false);
    }
  };

  return (
    <SettingsSection
      id="account"
      eyebrow="Управление аккаунтом"
      title="Аккаунт"
      description="Основные сведения об аккаунте, экспорт персональных данных и действия, которые влияют на доступ к HelpTree."
      icon={<UserRound size={22} />}
    >
      <dl className="settings-account-grid">
        <div className="settings-account-item">
          <dt>
            <Mail size={14} aria-hidden="true" />
            Email для входа
          </dt>
          <dd>{user.email}</dd>
        </div>
        <div className="settings-account-item">
          <dt>
            <UserRound size={14} aria-hidden="true" />
            Роль
          </dt>
          <dd>{user.role === 'ADMIN' ? 'Администратор' : 'Участник сообщества'}</dd>
        </div>
        <div className="settings-account-item">
          <dt>
            <CalendarDays size={14} aria-hidden="true" />
            Аккаунт создан
          </dt>
          <dd>{formatDate(user.createdAt)}</dd>
        </div>
        <div className="settings-account-item">
          <dt>
            <ShieldAlert size={14} aria-hidden="true" />
            Идентификатор
          </dt>
          <dd>#{user.id}</dd>
        </div>
      </dl>

      <div className="settings-export-zone">
        <div className="settings-danger-copy">
          <span className="settings-danger-icon" aria-hidden="true">
            <Download size={20} />
          </span>
          <div>
            <h3>Экспорт данных</h3>
            <p>
              Скачайте JSON с профилем, настройками уведомлений и приватности. Пароли, токены и
              данные сессий в файл не попадают.
            </p>
          </div>
        </div>
        <button
          type="button"
          className="settings-button settings-button--secondary"
          onClick={() => void handleExport()}
          disabled={exporting}
        >
          {exporting ? (
            <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
          ) : (
            <Download size={16} aria-hidden="true" />
          )}
          {exporting ? 'Готовим…' : 'Скачать JSON'}
        </button>
      </div>

      {exportError && (
        <div className="settings-alert" role="alert">
          <AlertTriangle size={17} aria-hidden="true" />
          <span>{exportError}</span>
        </div>
      )}

      <div className="settings-danger-zone">
        <div className="settings-danger-copy">
          <span className="settings-danger-icon" aria-hidden="true">
            <ShieldAlert size={20} />
          </span>
          <div>
            <h3>Удаление аккаунта</h3>
            <p>
              Аккаунт будет деактивирован, а вход в HelpTree — заблокирован. Администратор может
              восстановить мягко удалённый аккаунт. Безвозвратное удаление персональных данных
              выполняется отдельным действием с подтверждением пароля.
            </p>
          </div>
        </div>
        <div className="settings-danger-actions">
          <button
            type="button"
            className="settings-button settings-button--secondary"
            onClick={() => {
              setDeleteError(null);
              setConfirmOpen(true);
            }}
          >
            <Trash2 size={16} aria-hidden="true" />
            Удалить аккаунт
          </button>
          <button
            type="button"
            className="settings-button settings-button--danger"
            onClick={() => {
              setAnonymizeError(null);
              setAnonymizePassword('');
              setAnonymizeReason('');
              setAnonymizeOpen(true);
            }}
          >
            <KeyRound size={16} aria-hidden="true" />
            Удалить с анонимизацией
          </button>
        </div>
      </div>

      {deleteError && (
        <div className="settings-alert" role="alert">
          <AlertTriangle size={17} aria-hidden="true" />
          <span>{deleteError}</span>
        </div>
      )}

      <ConfirmDialog
        isOpen={confirmOpen}
        title="Удалить аккаунт?"
        description="Аккаунт будет деактивирован, а вход — заблокирован. Самостоятельно восстановить доступ нельзя."
        confirmLabel="Удалить аккаунт"
        busyLabel="Удаляем…"
        tone="danger"
        isBusy={deleting}
        onConfirm={() => void handleDelete()}
        onClose={() => {
          if (!deleting) setConfirmOpen(false);
        }}
      >
        <ul className="settings-dialog-list">
          <li>Вы выйдете из текущей сессии.</li>
          <li>Профиль перестанет отображаться в сообществе.</li>
          <li>Восстановить доступ сможет только администратор HelpTree.</li>
        </ul>
      </ConfirmDialog>

      <ConfirmDialog
        isOpen={anonymizeOpen}
        title="Удалить аккаунт с анонимизацией?"
        description="Персональные данные будут удалены безвозвратно: имя, email, телефон, город и координаты. Восстановление станет невозможным."
        confirmLabel="Анонимизировать и удалить"
        busyLabel="Удаляем данные…"
        tone="danger"
        isBusy={anonymizing}
        onConfirm={() => void handleAnonymize()}
        onClose={() => {
          if (!anonymizing) {
            setAnonymizeOpen(false);
            setAnonymizeError(null);
          }
        }}
      >
        <ul className="settings-dialog-list">
          <li>Все активные сессии будут завершены.</li>
          <li>Останется обезличенная запись без персональных данных.</li>
          <li>Действие необратимо; причина сохраняется в журнале аудита.</li>
        </ul>

        <div className="settings-field settings-dialog-field">
          <label htmlFor="settings-anonymize-password">
            Текущий пароль <span className="settings-required">*</span>
          </label>
          <input
            id="settings-anonymize-password"
            type="password"
            value={anonymizePassword}
            onChange={(event) => {
              setAnonymizePassword(event.target.value);
              setAnonymizeError(null);
            }}
            autoComplete="current-password"
            aria-invalid={Boolean(anonymizeError)}
            required
          />
          <p className="settings-field-hint">
            Пароль подтверждает, что действие выполняет владелец аккаунта.
          </p>
        </div>

        <div className="settings-field settings-dialog-field">
          <label htmlFor="settings-anonymize-reason">Причина (необязательно)</label>
          <input
            id="settings-anonymize-reason"
            value={anonymizeReason}
            onChange={(event) => setAnonymizeReason(event.target.value)}
            maxLength={500}
          />
          <p className="settings-field-hint">
            Например: «Не хочу больше пользоваться сервисом». Помогает поддержке разобраться в
            обращении.
          </p>
        </div>

        {anonymizeError && (
          <div className="settings-alert" role="alert">
            <AlertTriangle size={17} aria-hidden="true" />
            <span>{anonymizeError}</span>
          </div>
        )}
      </ConfirmDialog>
    </SettingsSection>
  );
};
