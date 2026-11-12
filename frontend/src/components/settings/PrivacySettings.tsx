import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Check, Eye, Loader2, RefreshCw, Save } from 'lucide-react';
import {
  authApi,
  type PrivacySettings as PrivacySettingsResponse,
  type ProfileVisibility,
} from '../../api/authApi';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import { useToast } from '../Toast';
import { SettingsSection } from './SettingsSection';

interface PrivacySettingsProps {
  onDirtyChange: (dirty: boolean) => void;
}

interface FormState {
  profileVisibility: ProfileVisibility;
  showCity: boolean;
  showEmail: boolean;
  showPhone: boolean;
  showOnlineStatus: boolean;
  allowDirectMessages: boolean;
  searchable: boolean;
}

const EMPTY_FORM: FormState = {
  profileVisibility: 'REGISTERED',
  showCity: true,
  showEmail: false,
  showPhone: false,
  showOnlineStatus: true,
  allowDirectMessages: true,
  searchable: true,
};

const VISIBILITY_OPTIONS: Array<{ value: ProfileVisibility; label: string; hint: string }> = [
  { value: 'PUBLIC', label: 'Все пользователи', hint: 'Профиль виден даже гостям.' },
  { value: 'REGISTERED', label: 'Только участники', hint: 'Профиль видят только авторизованные.' },
  { value: 'PRIVATE', label: 'Только я', hint: 'Публичный профиль скрыт от остальных.' },
];

const toForm = (settings: PrivacySettingsResponse): FormState => ({
  profileVisibility: settings.profileVisibility,
  showCity: settings.showCity,
  showEmail: settings.showEmail,
  showPhone: settings.showPhone,
  showOnlineStatus: settings.showOnlineStatus,
  allowDirectMessages: settings.allowDirectMessages,
  searchable: settings.searchable,
});

const describeError = (error: unknown): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 401 || info.status === 403) {
    return 'Недостаточно прав для изменения приватности.';
  }
  if (!info.status) {
    return 'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.';
  }
  if (info.message && !isGenericValidationMessage(info.message)) return info.message;
  return 'Не удалось сохранить настройки приватности. Попробуйте ещё раз.';
};

export const PrivacySettings: React.FC<PrivacySettingsProps> = ({ onDirtyChange }) => {
  const { showToast } = useToast();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [baseline, setBaseline] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const isDirty = useMemo(
    () => JSON.stringify(form) !== JSON.stringify(baseline),
    [form, baseline]
  );

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const settings = await authApi.getPrivacySettings();
      const next = toForm(settings);
      setForm(next);
      setBaseline(next);
    } catch (error) {
      setLoadError(describeError(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const update = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
    setSaveError(null);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (saving || !isDirty) return;

    setSaving(true);
    setSaveError(null);
    try {
      const saved = await authApi.updatePrivacySettings(form);
      const next = toForm(saved);
      setForm(next);
      setBaseline(next);
      showToast('Настройки приватности сохранены', 'success');
    } catch (error) {
      setSaveError(describeError(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <SettingsSection
      id="privacy"
      eyebrow="Видимость"
      title="Приватность"
      description="Управляйте тем, какие данные профиля видят другие участники, и разрешёнными способами связи."
      icon={<Eye size={22} />}
    >
      {loading ? (
        <div className="settings-inline-loading" role="status">
          <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
          Загружаем настройки приватности…
        </div>
      ) : loadError ? (
        <div className="settings-alert" role="alert">
          <AlertTriangle size={17} aria-hidden="true" />
          <span>{loadError}</span>
          <button type="button" className="settings-button settings-button--secondary" onClick={() => void load()}>
            <RefreshCw size={15} aria-hidden="true" />
            Повторить
          </button>
        </div>
      ) : (
        <form className="settings-form" onSubmit={handleSubmit} noValidate>
          <div className="settings-form-grid">
            <div className="settings-field">
              <label htmlFor="settings-visibility">Кто видит профиль</label>
              <select
                id="settings-visibility"
                value={form.profileVisibility}
                onChange={(event) =>
                  update('profileVisibility', event.target.value as ProfileVisibility)
                }
              >
                {VISIBILITY_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <p className="settings-field-hint">
                {VISIBILITY_OPTIONS.find((option) => option.value === form.profileVisibility)?.hint}
              </p>
            </div>
          </div>

          <fieldset className="settings-fieldset">
            <legend>Что показывать в профиле</legend>
            <div className="settings-options">
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.showCity}
                  onChange={(event) => update('showCity', event.target.checked)}
                />
                <span>
                  <strong>Город</strong>
                  <small>Отображается в публичном профиле и поиске.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.showPhone}
                  onChange={(event) => update('showPhone', event.target.checked)}
                />
                <span>
                  <strong>Телефон</strong>
                  <small>По умолчанию скрыт из публичного профиля.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.showOnlineStatus}
                  onChange={(event) => update('showOnlineStatus', event.target.checked)}
                />
                <span>
                  <strong>Статус «онлайн»</strong>
                  <small>Показывать другим участникам, что вы сейчас на сайте.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.showEmail}
                  onChange={(event) => update('showEmail', event.target.checked)}
                />
                <span>
                  <strong>Email</strong>
                  <small>Не рекомендуется: адрес может использоваться для спама.</small>
                </span>
              </label>
            </div>
          </fieldset>

          <fieldset className="settings-fieldset">
            <legend>Связь и поиск</legend>
            <div className="settings-options">
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.allowDirectMessages}
                  onChange={(event) => update('allowDirectMessages', event.target.checked)}
                />
                <span>
                  <strong>Личные сообщения</strong>
                  <small>Другие участники могут начать с вами переписку.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.searchable}
                  onChange={(event) => update('searchable', event.target.checked)}
                />
                <span>
                  <strong>Показывать в поиске</strong>
                  <small>Профиль появляется в результатах поиска участников.</small>
                </span>
              </label>
            </div>
          </fieldset>

          {saveError && (
            <div className="settings-alert" role="alert">
              <AlertTriangle size={17} aria-hidden="true" />
              <span>{saveError}</span>
            </div>
          )}

          <div className="settings-form-footer">
            <div className={`settings-save-state ${isDirty ? 'is-dirty' : 'is-saved'}`} role="status">
              {isDirty ? <Eye size={15} aria-hidden="true" /> : <Check size={15} aria-hidden="true" />}
              <span>{isDirty ? 'Есть несохранённые изменения' : 'Все изменения сохранены'}</span>
            </div>
            <div className="settings-form-actions">
              <button
                type="button"
                className="settings-button settings-button--ghost"
                onClick={() => {
                  setForm(baseline);
                  setSaveError(null);
                }}
                disabled={!isDirty || saving}
              >
                <RefreshCw size={15} aria-hidden="true" />
                Сбросить
              </button>
              <button
                type="submit"
                className="settings-button settings-button--primary"
                disabled={!isDirty || saving}
              >
                {saving ? (
                  <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
                ) : (
                  <Save size={16} aria-hidden="true" />
                )}
                {saving ? 'Сохраняем…' : 'Сохранить'}
              </button>
            </div>
          </div>
        </form>
      )}
    </SettingsSection>
  );
};
