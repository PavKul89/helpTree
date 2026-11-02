import React, { useState } from 'react';
import {
  AlertTriangle,
  CalendarDays,
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

export const AccountSettings: React.FC<AccountSettingsProps> = ({ user, onDeleted }) => {
  const { showToast } = useToast();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleDelete = async () => {
    if (deleting) return;

    setDeleting(true);
    setDeleteError(null);

    try {
      await authApi.deleteAccount(user.id);
      showToast('Аккаунт удалён', 'success');
      onDeleted();
    } catch (error: unknown) {
      const info = getApiErrorInfo(error);
      setConfirmOpen(false);
      setDeleting(false);

      if (info.status === 403) {
        setDeleteError('Недостаточно прав для удаления этого аккаунта.');
      } else if (info.status === 404) {
        setDeleteError('Аккаунт уже удалён или недоступен.');
      } else if (!info.status) {
        setDeleteError('Не удалось связаться с сервером. Проверьте подключение и повторите попытку.');
      } else if (info.message && !isGenericValidationMessage(info.message)) {
        setDeleteError(info.message);
      } else {
        setDeleteError('Не удалось удалить аккаунт. Попробуйте ещё раз.');
      }
    }
  };

  return (
    <SettingsSection
      id="account"
      eyebrow="Управление аккаунтом"
      title="Аккаунт"
      description="Основные сведения об аккаунте и действия, которые влияют на доступ к HelpTree."
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

      <div className="settings-danger-zone">
        <div className="settings-danger-copy">
          <span className="settings-danger-icon" aria-hidden="true">
            <ShieldAlert size={20} />
          </span>
          <div>
            <h3>Удаление аккаунта</h3>
            <p>
              Аккаунт будет деактивирован, а вход в HelpTree — заблокирован. Самостоятельно
              восстановить доступ после удаления нельзя.
            </p>
          </div>
        </div>
        <button
          type="button"
          className="settings-button settings-button--danger"
          onClick={() => {
            setDeleteError(null);
            setConfirmOpen(true);
          }}
        >
          <Trash2 size={16} aria-hidden="true" />
          Удалить аккаунт
        </button>
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
        description="Аккаунт будет деактивирован, а вход — заблокирован. Это действие нельзя отменить самостоятельно."
        confirmLabel="Удалить аккаунт"
        busyLabel="Удаляем…"
        tone="danger"
        isBusy={deleting}
        onConfirm={handleDelete}
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
    </SettingsSection>
  );
};
