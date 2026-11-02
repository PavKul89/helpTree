import React, { useEffect, useState } from 'react';
import { Loader2, UserPlus } from 'lucide-react';
import { adminApi } from '../../api/adminApi';
import { AdminDialog } from '../../components/admin/AdminDialog';
import { useToast } from '../../components/Toast';
import { getApiErrorMessage, getApiFieldErrors } from './adminUtils';

interface CreateUserDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

interface FormState {
  name: string;
  email: string;
  password: string;
  phone: string;
  city: string;
}

const EMPTY_FORM: FormState = {
  name: '',
  email: '',
  password: '',
  phone: '',
  city: '',
};

const validate = (form: FormState): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (form.name.trim().length < 2 || form.name.trim().length > 100) {
    errors.name = 'Имя должно быть от 2 до 100 символов.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'Укажите корректный email.';
  }
  if (form.password.length < 8 || form.password.length > 100) {
    errors.password = 'Пароль должен быть от 8 до 100 символов.';
  }
  if (form.phone.trim().length < 10 || form.phone.trim().length > 20) {
    errors.phone = 'Телефон должен быть от 10 до 20 символов.';
  }
  if (!form.city.trim()) {
    errors.city = 'Город обязателен.';
  }
  return errors;
};

export const CreateUserDialog: React.FC<CreateUserDialogProps> = ({ open, onClose, onCreated }) => {
  const { showToast } = useToast();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
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

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy) return;

    const clientErrors = validate(form);
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      setGeneralError('Проверьте выделенные поля.');
      return;
    }

    setBusy(true);
    setGeneralError(null);
    setFieldErrors({});
    try {
      const created = await adminApi.createUser({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim(),
        city: form.city.trim(),
      });
      showToast(`Пользователь «${created.name}» создан с ролью USER.`, 'success');
      onCreated();
      onClose();
    } catch (error) {
      setFieldErrors(getApiFieldErrors(error));
      setGeneralError(getApiErrorMessage(error, 'Не удалось создать пользователя.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AdminDialog
      open={open}
      title="Новый пользователь"
      description="POST /api/users принимает только роль USER: повышение роли через API не предусмотрено."
      onClose={onClose}
      busy={busy}
    >
      <form className="admin-form" onSubmit={handleSubmit} noValidate>
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
                От 8 до 100 символов. Пароль отправляется только в запросе и не сохраняется в браузере.
              </span>
            )}
          </div>

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
        </div>

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
            ) : (
              <UserPlus size={16} aria-hidden="true" />
            )}
            {busy ? 'Создаём…' : 'Создать пользователя'}
          </button>
        </div>
      </form>
    </AdminDialog>
  );
};
