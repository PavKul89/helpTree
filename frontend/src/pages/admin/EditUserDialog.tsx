import React, { useEffect, useState } from 'react';
import { Loader2, Save } from 'lucide-react';
import { adminApi, type AdminUser } from '../../api/adminApi';
import { AdminDialog } from '../../components/admin/AdminDialog';
import { useToast } from '../../components/Toast';
import { formatDateInput, getApiErrorMessage, getApiFieldErrors } from './adminUtils';

interface EditUserDialogProps {
  open: boolean;
  user: AdminUser;
  onClose: () => void;
  onSaved: (user: AdminUser) => void;
}

interface FormState {
  name: string;
  email: string;
  phone: string;
  city: string;
  birthDate: string;
}

const getForm = (user: AdminUser): FormState => ({
  name: user.name ?? '',
  email: user.email ?? '',
  phone: user.phone ?? '',
  city: user.city ?? '',
  birthDate: formatDateInput(user.birthDate),
});

const validate = (form: FormState): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (form.name.trim().length < 2 || form.name.trim().length > 100) {
    errors.name = 'Имя должно быть от 2 до 100 символов.';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = 'Укажите корректный email.';
  }
  if (form.phone.trim().length < 10 || form.phone.trim().length > 20) {
    errors.phone = 'Телефон должен быть от 10 до 20 символов.';
  }
  if (!form.city.trim()) {
    errors.city = 'Город обязателен.';
  }
  if (form.birthDate) {
    const date = new Date(`${form.birthDate}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
      errors.birthDate = 'Укажите корректную дату.';
    } else if (date.getTime() > Date.now()) {
      errors.birthDate = 'Дата рождения не может быть в будущем.';
    }
  }
  return errors;
};

export const EditUserDialog: React.FC<EditUserDialogProps> = ({ open, user, onClose, onSaved }) => {
  const { showToast } = useToast();
  const [form, setForm] = useState<FormState>(() => getForm(user));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(getForm(user));
      setFieldErrors({});
      setGeneralError(null);
      setBusy(false);
    }
  }, [open, user]);

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
      const updated = await adminApi.updateUser(user.id, {
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        city: form.city.trim(),
        birthDate: form.birthDate ? `${form.birthDate}T00:00:00` : undefined,
      });
      showToast('Профиль пользователя обновлён.', 'success');
      onSaved(updated);
      onClose();
    } catch (error) {
      setFieldErrors(getApiFieldErrors(error));
      setGeneralError(getApiErrorMessage(error, 'Не удалось обновить профиль.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AdminDialog
      open={open}
      title="Редактировать профиль"
      description="PUT /api/users/{id}. Роль и репутационный статус через этот запрос не меняются."
      onClose={onClose}
      busy={busy}
    >
      <form className="admin-form" onSubmit={handleSubmit} noValidate>
        <div className="admin-form-grid">
          <div className="admin-field">
            <label htmlFor="admin-edit-name">Имя *</label>
            <input
              id="admin-edit-name"
              value={form.name}
              onChange={(event) => update('name', event.target.value)}
              aria-invalid={Boolean(fieldErrors.name)}
            />
            {fieldErrors.name && <span className="admin-field-error">{fieldErrors.name}</span>}
          </div>

          <div className="admin-field">
            <label htmlFor="admin-edit-email">Email *</label>
            <input
              id="admin-edit-email"
              type="email"
              value={form.email}
              onChange={(event) => update('email', event.target.value)}
              aria-invalid={Boolean(fieldErrors.email)}
            />
            {fieldErrors.email && <span className="admin-field-error">{fieldErrors.email}</span>}
          </div>

          <div className="admin-field">
            <label htmlFor="admin-edit-phone">Телефон *</label>
            <input
              id="admin-edit-phone"
              value={form.phone}
              onChange={(event) => update('phone', event.target.value)}
              aria-invalid={Boolean(fieldErrors.phone)}
            />
            {fieldErrors.phone && <span className="admin-field-error">{fieldErrors.phone}</span>}
          </div>

          <div className="admin-field">
            <label htmlFor="admin-edit-city">Город *</label>
            <input
              id="admin-edit-city"
              value={form.city}
              onChange={(event) => update('city', event.target.value)}
              aria-invalid={Boolean(fieldErrors.city)}
            />
            {fieldErrors.city && <span className="admin-field-error">{fieldErrors.city}</span>}
          </div>

          <div className="admin-field admin-field--wide">
            <label htmlFor="admin-edit-birth">Дата рождения</label>
            <input
              id="admin-edit-birth"
              type="date"
              value={form.birthDate}
              onChange={(event) => update('birthDate', event.target.value)}
              aria-invalid={Boolean(fieldErrors.birthDate)}
            />
            {fieldErrors.birthDate ? (
              <span className="admin-field-error">{fieldErrors.birthDate}</span>
            ) : (
              <span className="admin-field-hint">
                API принимает дату в формате ISO; уже сохранённую дату можно изменить, но не удалить.
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
              <Save size={16} aria-hidden="true" />
            )}
            {busy ? 'Сохраняем…' : 'Сохранить'}
          </button>
        </div>
      </form>
    </AdminDialog>
  );
};
