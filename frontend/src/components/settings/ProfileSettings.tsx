import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Check,
  CircleDot,
  Loader2,
  RefreshCw,
  Save,
  UserRound,
} from 'lucide-react';
import { authApi } from '../../api/authApi';
import type { User } from '../../types';
import { useToast } from '../Toast';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import { SettingsSection } from './SettingsSection';

type ProfileField = 'name' | 'email' | 'phone' | 'city' | 'birthDate';
type ProfileForm = Record<ProfileField, string>;
type ProfileErrors = Partial<Record<ProfileField, string>>;

const FIELD_ORDER: ProfileField[] = ['name', 'email', 'phone', 'city', 'birthDate'];
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const VALIDATION_NOTE = 'Проверьте выделенные поля и повторите сохранение.';

const toProfileForm = (user: User): ProfileForm => ({
  name: user.name ?? '',
  email: user.email ?? '',
  phone: user.phone ?? '',
  city: user.city ?? '',
  birthDate: user.birthDate ? user.birthDate.split('T')[0] : '',
});

const normalizeForm = (form: ProfileForm): ProfileForm => ({
  name: form.name.trim(),
  email: form.email.trim(),
  phone: form.phone.trim(),
  city: form.city.trim(),
  birthDate: form.birthDate,
});

const formsAreEqual = (first: ProfileForm, second: ProfileForm): boolean =>
  FIELD_ORDER.every((field) => first[field] === second[field]);

const getTodayInputValue = (): string => {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${today.getFullYear()}-${month}-${day}`;
};

const validateProfile = (form: ProfileForm, baseline: ProfileForm): ProfileErrors => {
  const errors: ProfileErrors = {};
  const name = form.name.trim();
  const email = form.email.trim();
  const phone = form.phone.trim();
  const city = form.city.trim();
  const baselinePhone = baseline.phone.trim();
  const baselineCity = baseline.city.trim();

  if (!name) {
    errors.name = 'Укажите имя.';
  } else if (name.length < 2 || name.length > 100) {
    errors.name = 'Имя должно содержать от 2 до 100 символов.';
  }

  if (!email) {
    errors.email = 'Укажите email.';
  } else if (email.length > 200) {
    errors.email = 'Email должен содержать не более 200 символов.';
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Введите корректный email.';
  }

  // Only changed contact fields are validated and sent. This keeps legacy
  // profiles with sparse contacts editable without touching those values.
  if (phone !== baselinePhone) {
    if (!phone) {
      errors.phone = 'Телефон можно изменить, но нельзя удалить.';
    } else if (phone.length < 10 || phone.length > 20) {
      errors.phone = 'Телефон должен содержать от 10 до 20 символов.';
    }
  }

  if (city !== baselineCity) {
    if (!city) {
      errors.city = 'Город можно изменить, но нельзя удалить.';
    } else if (city.length > 100) {
      errors.city = 'Город должен содержать не более 100 символов.';
    }
  }

  if (!form.birthDate && baseline.birthDate) {
    errors.birthDate = 'Дату рождения можно изменить, но нельзя удалить.';
  } else if (form.birthDate) {
    const birthDate = new Date(`${form.birthDate}T00:00:00`);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (Number.isNaN(birthDate.getTime())) {
      errors.birthDate = 'Укажите корректную дату рождения.';
    } else if (birthDate.getTime() > today.getTime()) {
      errors.birthDate = 'Дата рождения не может быть в будущем.';
    }
  }

  return errors;
};

interface ProfileFieldControlProps {
  id: string;
  label: string;
  value: string;
  hint: string;
  onChange: (value: string) => void;
  error?: string;
  type?: 'text' | 'email' | 'tel' | 'date';
  autoComplete?: string;
  inputMode?: 'text' | 'email' | 'tel' | 'numeric';
  maxLength?: number;
  max?: string;
  required?: boolean;
  wide?: boolean;
  readOnly?: boolean;
}

const ProfileFieldControl: React.FC<ProfileFieldControlProps> = ({
  id,
  label,
  value,
  hint,
  onChange,
  error,
  type = 'text',
  autoComplete,
  inputMode,
  maxLength,
  max,
  required = false,
  wide = false,
  readOnly = false,
}) => {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = error ? `${hintId} ${errorId}` : hintId;

  return (
    <div className={`settings-field${wide ? ' settings-field--wide' : ''}`}>
      <label htmlFor={id}>
        {label}
        {required && (
          <span className="settings-required" aria-hidden="true">
            *
          </span>
        )}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        max={max}
        required={required}
        readOnly={readOnly}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={error ? 'has-error' : undefined}
      />
      <p id={hintId} className="settings-field-hint">
        {hint}
      </p>
      {error && (
        <p id={errorId} className="settings-field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
};

interface ProfileSettingsProps {
  user: User;
  onSaved: (user: User) => void;
  onDirtyChange: (dirty: boolean) => void;
}

export const ProfileSettings: React.FC<ProfileSettingsProps> = ({
  user,
  onSaved,
  onDirtyChange,
}) => {
  const { showToast } = useToast();
  const [form, setForm] = useState<ProfileForm>(() => toProfileForm(user));
  const [baseline, setBaseline] = useState<ProfileForm>(() => toProfileForm(user));
  const [errors, setErrors] = useState<ProfileErrors>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const normalizedForm = normalizeForm(form);
  const normalizedBaseline = normalizeForm(baseline);
  const isDirty = !formsAreEqual(normalizedForm, normalizedBaseline);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const updateField = (field: ProfileField, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    if (saveError) setSaveError(null);
  };

  const handleReset = () => {
    if (saving) return;
    setForm(baseline);
    setErrors({});
    setSaveError(null);
    onDirtyChange(false);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (saving) return;

    const validationErrors = validateProfile(form, baseline);
    setErrors(validationErrors);
    setSaveError(null);

    const firstInvalidField = FIELD_ORDER.find((field) => validationErrors[field]);
    if (firstInvalidField) {
      document.getElementById(`settings-${firstInvalidField}`)?.focus();
      return;
    }

    if (!isDirty) return;

    setSaving(true);

    try {
      const payload: {
        name: string;
        phone?: string;
        city?: string;
        birthDate?: string;
      } = {
        name: normalizedForm.name,
      };

      if (normalizedForm.phone !== normalizedBaseline.phone) {
        payload.phone = normalizedForm.phone;
      }
      if (normalizedForm.city !== normalizedBaseline.city) {
        payload.city = normalizedForm.city;
      }
      if (normalizedForm.birthDate !== normalizedBaseline.birthDate) {
        payload.birthDate = `${normalizedForm.birthDate}T00:00:00`;
      }

      const updatedUser = await authApi.updateProfile(user.id, payload);

      const nextForm = toProfileForm(updatedUser);
      setForm(nextForm);
      setBaseline(nextForm);
      setErrors({});
      onSaved(updatedUser);
      showToast('Профиль обновлён', 'success');
    } catch (error: unknown) {
      const info = getApiErrorInfo(error);
      const fieldErrors: ProfileErrors = {};

      Object.entries(info.fields).forEach(([field, message]) => {
        if (FIELD_ORDER.includes(field as ProfileField)) {
          fieldErrors[field as ProfileField] = message;
        }
      });

      if (info.status === 409) {
        fieldErrors.email = 'Этот email уже используется.';
      }

      if (Object.keys(fieldErrors).length > 0) {
        setErrors(fieldErrors);
      }

      if (info.status === 409) {
        setSaveError('Укажите другой email: этот адрес уже занят.');
      } else if (info.status === 403) {
        setSaveError('Недостаточно прав для изменения профиля.');
      } else if (!info.status) {
        setSaveError('Не удалось связаться с сервером. Проверьте подключение и повторите попытку.');
      } else if (
        Object.keys(fieldErrors).length === 0 &&
        info.message &&
        !isGenericValidationMessage(info.message)
      ) {
        setSaveError(info.message);
      } else {
        setSaveError('Не удалось сохранить профиль. Проверьте данные и повторите попытку.');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <SettingsSection
      id="profile"
      eyebrow="Личные данные"
      title="Профиль"
      description="Имя и фото видны участникам сообщества. Email используется для входа и не отображается в публичном профиле."
      icon={<UserRound size={22} />}
    >
      <form className="settings-form" onSubmit={handleSubmit} noValidate aria-busy={saving}>
        <div className="settings-form-grid">
          <ProfileFieldControl
            id="settings-name"
            label="Имя"
            value={form.name}
            onChange={(value) => updateField('name', value)}
            error={errors.name}
            hint="От 2 до 100 символов. Отображается в сообществе."
            autoComplete="name"
            maxLength={100}
            required
            wide
          />
          <ProfileFieldControl
            id="settings-email"
            label="Email"
            type="email"
            value={form.email}
            onChange={(value) => updateField('email', value)}
            error={errors.email}
            hint="Используется для входа. Сменить email можно только отдельной процедурой подтверждения, поэтому здесь поле недоступно."
            autoComplete="email"
            inputMode="email"
            maxLength={200}
            required
            readOnly
          />
          <ProfileFieldControl
            id="settings-phone"
            label="Телефон"
            type="tel"
            value={form.phone}
            onChange={(value) => updateField('phone', value)}
            error={errors.phone}
            hint="Необязательно. Если указываете — от 10 до 20 символов. Не отображается в публичном профиле."
            autoComplete="tel"
            inputMode="tel"
            maxLength={20}
          />
          <ProfileFieldControl
            id="settings-city"
            label="Город"
            value={form.city}
            onChange={(value) => updateField('city', value)}
            error={errors.city}
            hint="Необязательно. Помогает находить заявки и участников рядом."
            autoComplete="address-level2"
            maxLength={100}
          />
          <ProfileFieldControl
            id="settings-birthDate"
            label="Дата рождения"
            type="date"
            value={form.birthDate}
            onChange={(value) => updateField('birthDate', value)}
            error={errors.birthDate}
            hint="Необязательно. Дату можно изменить, но нельзя удалить."
            autoComplete="bday"
            max={getTodayInputValue()}
          />
        </div>

        {saveError && (
          <div className="settings-alert" role="alert">
            <AlertTriangle size={17} aria-hidden="true" />
            <span>{saveError}</span>
          </div>
        )}

        <div className="settings-form-footer">
          <div
            className={`settings-save-state ${
              saving ? 'is-saving' : isDirty ? 'is-dirty' : 'is-saved'
            }`}
            role="status"
            aria-live="polite"
          >
            {saving ? (
              <Loader2 className="settings-spinner" size={15} aria-hidden="true" />
            ) : isDirty ? (
              <CircleDot size={15} aria-hidden="true" />
            ) : (
              <Check size={15} aria-hidden="true" />
            )}
            <span>
              {saving
                ? 'Сохраняем изменения…'
                : isDirty
                  ? 'Есть несохранённые изменения'
                  : 'Все изменения сохранены'}
            </span>
          </div>

          <div className="settings-form-actions">
            <button
              type="button"
              className="settings-button settings-button--ghost"
              onClick={handleReset}
              disabled={!isDirty || saving}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Сбросить
            </button>
            <button
              type="submit"
              className="settings-button settings-button--primary"
              disabled={!isDirty || saving}
              aria-busy={saving}
            >
              {saving ? (
                <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
              ) : (
                <Save size={16} aria-hidden="true" />
              )}
              {saving ? 'Сохраняем…' : 'Сохранить изменения'}
            </button>
          </div>
        </div>

        {Object.keys(errors).length > 0 && (
          <p className="settings-form-note" role="status">
            {VALIDATION_NOTE}
          </p>
        )}
      </form>
    </SettingsSection>
  );
};
