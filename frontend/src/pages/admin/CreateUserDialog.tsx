import React, { useEffect, useState } from 'react';
import { Loader2, Mail, UserPlus } from 'lucide-react';
import { adminApi, type AdminUserRole } from '../../api/adminApi';
import { AdminDialog } from '../../components/admin/AdminDialog';
import { useToast } from '../../components/Toast';
import { getApiErrorMessage, getApiFieldErrors } from './adminUtils';

interface CreateUserDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

type CreationMode = 'password' | 'invite';

interface FormState {
  name: string;
  email: string;
  password: string;
  phone: string;
  city: string;
  role: AdminUserRole;
  reason: string;
}

const EMPTY_FORM: FormState = {
  name: '',
  email: '',
  password: '',
  phone: '',
  city: '',
  role: 'USER',
  reason: '',
};

const validate = (form: FormState, mode: CreationMode): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (form.name.trim().length < 2 || form.name.trim().length > 100) {
    errors.name = 'Имя должно быть от 2 до 100 символов.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'Укажите корректный email.';
  }
  if (mode === 'password' && (form.password.length < 8 || form.password.length > 100)) {
    errors.password = 'Пароль должен быть от 8 до 100 символов.';
  }
  if (form.phone.trim().length < 10 || form.phone.trim().length > 20) {
    errors.phone = 'Телефон должен быть от 10 до 20 символов.';
  }
  if (!form.city.trim()) {
    errors.city = 'Город обязателен.';
  }
  if (!form.reason.trim()) {
    errors.reason = 'Причина обязательна: она сохраняется в журнале аудита.';
  }
  if (form.reason.trim().length > 1000) {
    errors.reason = 'Причина не должна превышать 1000 символов.';
  }
  return errors;
};

export const CreateUserDialog: React.FC<CreateUserDialogProps> = ({ open, onClose, onCreated }) => {
  const { showToast } = useToast();
  const [mode, setMode] = useState<CreationMode>('password');
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setMode('password');
      setForm(EMPTY_FORM);
      setFieldErrors({});
      setGeneralError(null);
      setBusy(false);
    }
  }, [open]);

  const update = (field: keyof FormState, value: string) => {
    setForm((previous) => ({ ...previous, [field]: value }));
    setFieldErrors((previous) => {
      if (!previous[field]) return previous;
      const next = { ...previous };
      delete next[field];
      return next;
    });
  };

  const switchMode = (nextMode: CreationMode) => {
    setMode(nextMode);
    setGeneralError(null);
    setFieldErrors((previous) => {
      if (!previous.password) return previous;
      const next = { ...previous };
      delete next.password;
      return next;
    });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy) return;

    const clientErrors = validate(form, mode);
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      setGeneralError('Проверьте выделенные поля.');
      return;
    }

    setBusy(true);
    setGeneralError(null);
    setFieldErrors({});
    try {
      const basePayload = {
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        city: form.city.trim(),
        role: form.role,
        reason: form.reason.trim(),
      };
      const created =
        mode === 'invite'
          ? await adminApi.inviteUser(basePayload)
          : await adminApi.createUser({ ...basePayload, password: form.password });
      showToast(
        mode === 'invite'
          ? `Приглашение отправлено на ${created.email}. Ссылка действует 72 часа.`
          : `Пользователь «${created.name}» создан с ролью ${created.role}.`,
        'success'
      );
      onCreated();
      onClose();
    } catch (error) {
      setFieldErrors(getApiFieldErrors(error));
      setGeneralError(
        getApiErrorMessage(
          error,
          mode === 'invite'
            ? 'Не удалось отправить приглашение. Проверьте настройки почты.'
            : 'Не удалось создать пользователя.'
        )
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <AdminDialog
      open={open}
      title="Новый пользователь"
      description="Защищённая административная операция. Причина обязательна и попадает в журнал аудита."
      onClose={onClose}
      busy={busy}
    >
      <form className="admin-form" onSubmit={handleSubmit} noValidate>
        <div className="admin-scope-tabs" role="tablist" aria-label="Способ создания">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'password'}
            className={`admin-scope-tab${mode === 'password' ? ' is-active' : ''}`}
            onClick={() => switchMode('password')}
            disabled={busy}
          >
            <UserPlus size={15} aria-hidden="true" />
            С паролем
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'invite'}
            className={`admin-scope-tab${mode === 'invite' ? ' is-active' : ''}`}
            onClick={() => switchMode('invite')}
            disabled={busy}
          >
            <Mail size={15} aria-hidden="true" />
            Приглашение
          </button>
        </div>

        <div className="admin-form-grid">
          <div className="admin-field admin-field--wide">
            <label htmlFor="admin-create-name">Имя *</label>
            <input
              id="admin-create-name"
              value={form.name}
              onChange={(event) => update('name', event.target.value)}
              autoComplete="off"
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'admin-create-name-error' : undefined}
            />
            {fieldErrors.name && (
              <span id="admin-create-name-error" className="admin-field-error">
                {fieldErrors.name}
              </span>
            )}
          </div>

          <div className="admin-field admin-field--wide">
            <label htmlFor="admin-create-email">Email *</label>
            <input
              id="admin-create-email"
              type="email"
              value={form.email}
              onChange={(event) => update('email', event.target.value)}
              autoComplete="off"
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'admin-create-email-error' : undefined}
            />
            {fieldErrors.email && (
              <span id="admin-create-email-error" className="admin-field-error">
                {fieldErrors.email}
              </span>
            )}
          </div>

          {mode === 'password' && (
            <div className="admin-field admin-field--wide">
              <label htmlFor="admin-create-password">Пароль *</label>
              <input
                id="admin-create-password"
                type="password"
                value={form.password}
                onChange={(event) => update('password', event.target.value)}
                autoComplete="new-password"
                aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={
                  fieldErrors.password ? 'admin-create-password-error' : 'admin-create-password-hint'
                }
              />
              {fieldErrors.password ? (
                <span id="admin-create-password-error" className="admin-field-error">
                  {fieldErrors.password}
                </span>
              ) : (
                <span id="admin-create-password-hint" className="admin-field-hint">
                  От 8 до 100 символов, минимум одна заглавная, строчная и цифра. Пароль не
                  сохраняется в браузере.
                </span>
              )}
            </div>
          )}

          <div className="admin-field">
            <label htmlFor="admin-create-phone">Телефон *</label>
            <input
              id="admin-create-phone"
              value={form.phone}
              onChange={(event) => update('phone', event.target.value)}
              autoComplete="off"
              aria-invalid={Boolean(fieldErrors.phone)}
              aria-describedby={fieldErrors.phone ? 'admin-create-phone-error' : undefined}
            />
            {fieldErrors.phone && (
              <span id="admin-create-phone-error" className="admin-field-error">
                {fieldErrors.phone}
              </span>
            )}
          </div>

          <div className="admin-field">
            <label htmlFor="admin-create-city">Город *</label>
            <input
              id="admin-create-city"
              value={form.city}
              onChange={(event) => update('city', event.target.value)}
              autoComplete="off"
              aria-invalid={Boolean(fieldErrors.city)}
              aria-describedby={fieldErrors.city ? 'admin-create-city-error' : undefined}
            />
            {fieldErrors.city && (
              <span id="admin-create-city-error" className="admin-field-error">
                {fieldErrors.city}
              </span>
            )}
          </div>

          <div className="admin-field">
            <label htmlFor="admin-create-role">Роль *</label>
            <select
              id="admin-create-role"
              value={form.role}
              onChange={(event) => update('role', event.target.value as AdminUserRole)}
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <span className="admin-field-hint">
              Роль ADMIN даёт доступ ко всем административным endpoint-ам.
            </span>
          </div>

          <div className="admin-field admin-field--wide">
            <label htmlFor="admin-create-reason">Причина *</label>
            <textarea
              id="admin-create-reason"
              value={form.reason}
              onChange={(event) => update('reason', event.target.value)}
              rows={2}
              maxLength={1000}
              required
              aria-invalid={Boolean(fieldErrors.reason)}
              aria-describedby={fieldErrors.reason ? 'admin-create-reason-error' : 'admin-create-reason-hint'}
            />
            {fieldErrors.reason ? (
              <span id="admin-create-reason-error" className="admin-field-error">
                {fieldErrors.reason}
              </span>
            ) : (
              <span id="admin-create-reason-hint" className="admin-field-hint">
                Сохраняется в admin_audit_log вместе с ID администратора.
              </span>
            )}
          </div>
        </div>

        {mode === 'invite' && (
          <div className="admin-inline-alert admin-inline-alert--info" role="note">
            <Mail size={16} aria-hidden="true" />
            <span>
              Пароль не задаётся: аккаунт создаётся заблокированным для входа, а на email
              отправляется одноразовая ссылка установки пароля (72 часа). Требуется настроенный SMTP.
            </span>
          </div>
        )}

        {generalError && (
          <div className="admin-inline-alert" role="alert">
            {generalError}
          </div>
        )}

        <div className="admin-dialog-actions">
          <button type="button" className="admin-button admin-button--ghost" onClick={onClose} disabled={busy}>
            Отмена
          </button>
          <button type="submit" className="admin-button admin-button--primary" disabled={busy}>
            {busy ? (
              <Loader2 size={16} className="admin-spinner" aria-hidden="true" />
            ) : mode === 'invite' ? (
              <Mail size={16} aria-hidden="true" />
            ) : (
              <UserPlus size={16} aria-hidden="true" />
            )}
            {busy
              ? mode === 'invite'
                ? 'Отправляем…'
                : 'Создаём…'
              : mode === 'invite'
                ? 'Отправить приглашение'
                : 'Создать пользователя'}
          </button>
        </div>
      </form>
    </AdminDialog>
  );
};
