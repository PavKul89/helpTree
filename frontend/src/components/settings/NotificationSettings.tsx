import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Bell, Check, Loader2, RefreshCw, Save } from 'lucide-react';
import {
  authApi,
  type DigestFrequency,
  type NotificationPreferences,
} from '../../api/authApi';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import { useToast } from '../Toast';
import { SettingsSection } from './SettingsSection';

interface NotificationSettingsProps {
  onDirtyChange: (dirty: boolean) => void;
}

interface FormState {
  emailEnabled: boolean;
  pushEnabled: boolean;
  inAppEnabled: boolean;
  digestFrequency: DigestFrequency;
  quietHoursStart: string;
  quietHoursEnd: string;
}

const EMPTY_FORM: FormState = {
  emailEnabled: true,
  pushEnabled: true,
  inAppEnabled: true,
  digestFrequency: 'INSTANT',
  quietHoursStart: '',
  quietHoursEnd: '',
};

const DIGEST_OPTIONS: Array<{ value: DigestFrequency; label: string; hint: string }> = [
  { value: 'INSTANT', label: 'Сразу', hint: 'Каждое уведомление приходит сразу.' },
  { value: 'DAILY', label: 'Раз в день', hint: 'Один дайджест раз в сутки.' },
  { value: 'WEEKLY', label: 'Раз в неделю', hint: 'Один дайджест в неделю.' },
  { value: 'OFF', label: 'Не присылать', hint: 'Дайджесты отключены.' },
];

const toForm = (preferences: NotificationPreferences): FormState => ({
  emailEnabled: preferences.emailEnabled,
  pushEnabled: preferences.pushEnabled,
  inAppEnabled: preferences.inAppEnabled,
  digestFrequency: preferences.digestFrequency,
  quietHoursStart: preferences.quietHoursStart ? preferences.quietHoursStart.slice(0, 5) : '',
  quietHoursEnd: preferences.quietHoursEnd ? preferences.quietHoursEnd.slice(0, 5) : '',
});

const toPayload = (form: FormState) => ({
  emailEnabled: form.emailEnabled,
  pushEnabled: form.pushEnabled,
  inAppEnabled: form.inAppEnabled,
  digestFrequency: form.digestFrequency,
  quietHoursStart: form.quietHoursStart || null,
  quietHoursEnd: form.quietHoursEnd || null,
});

const describeError = (error: unknown): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 401 || info.status === 403) {
    return 'Недостаточно прав для изменения уведомлений.';
  }
  if (!info.status) {
    return 'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.';
  }
  if (info.message && !isGenericValidationMessage(info.message)) return info.message;
  return 'Не удалось сохранить настройки уведомлений. Попробуйте ещё раз.';
};

export const NotificationSettings: React.FC<NotificationSettingsProps> = ({ onDirtyChange }) => {
  const { showToast } = useToast();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [baseline, setBaseline] = useState<FormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const isDirty = useMemo(
    () => JSON.stringify(toPayload(form)) !== JSON.stringify(toPayload(baseline)),
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
      const preferences = await authApi.getNotificationPreferences();
      const next = toForm(preferences);
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

    if (Boolean(form.quietHoursStart) !== Boolean(form.quietHoursEnd)) {
      setSaveError('Укажите начало и конец тихих часов или очистите оба поля.');
      return;
    }

    setSaving(true);
    setSaveError(null);
    try {
      const saved = await authApi.updateNotificationPreferences(toPayload(form));
      const next = toForm(saved);
      setForm(next);
      setBaseline(next);
      showToast('Настройки уведомлений сохранены', 'success');
    } catch (error) {
      setSaveError(describeError(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <SettingsSection
      id="notifications"
      eyebrow="Оповещения"
      title="Уведомления"
      description="Выберите каналы, частоту дайджестов и тихие часы. Настройки хранятся на сервере и применяются к новым уведомлениям."
      icon={<Bell size={22} />}
    >
      {loading ? (
        <div className="settings-inline-loading" role="status">
          <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
          Загружаем настройки уведомлений…
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
          <fieldset className="settings-fieldset">
            <legend>Каналы</legend>
            <div className="settings-options">
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.emailEnabled}
                  onChange={(event) => update('emailEnabled', event.target.checked)}
                />
                <span>
                  <strong>Email</strong>
                  <small>Письма о заявках, отзывах и важных событиях.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.pushEnabled}
                  onChange={(event) => update('pushEnabled', event.target.checked)}
                />
                <span>
                  <strong>Push</strong>
                  <small>Уведомления в браузере и на устройстве.</small>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.inAppEnabled}
                  onChange={(event) => update('inAppEnabled', event.target.checked)}
                />
                <span>
                  <strong>В приложении</strong>
                  <small>Счётчик и лента уведомлений внутри HelpTree.</small>
                </span>
              </label>
            </div>
          </fieldset>

          <div className="settings-form-grid">
            <div className="settings-field">
              <label htmlFor="settings-digest">Частота дайджеста</label>
              <select
                id="settings-digest"
                value={form.digestFrequency}
                onChange={(event) =>
                  update('digestFrequency', event.target.value as DigestFrequency)
                }
              >
                {DIGEST_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <p className="settings-field-hint">
                {DIGEST_OPTIONS.find((option) => option.value === form.digestFrequency)?.hint}
              </p>
            </div>

            <div className="settings-field">
              <label htmlFor="settings-quiet-start">Тихие часы: с</label>
              <input
                id="settings-quiet-start"
                type="time"
                value={form.quietHoursStart}
                onChange={(event) => update('quietHoursStart', event.target.value)}
              />
              <p className="settings-field-hint">Оставьте оба поля пустыми, чтобы отключить.</p>
            </div>

            <div className="settings-field">
              <label htmlFor="settings-quiet-end">Тихие часы: до</label>
              <input
                id="settings-quiet-end"
                type="time"
                value={form.quietHoursEnd}
                onChange={(event) => update('quietHoursEnd', event.target.value)}
              />
              <p className="settings-field-hint">
                В это время уведомления задерживаются до окончания периода.
              </p>
            </div>
          </div>

          {saveError && (
            <div className="settings-alert" role="alert">
              <AlertTriangle size={17} aria-hidden="true" />
              <span>{saveError}</span>
            </div>
          )}

          <div className="settings-form-footer">
            <div className={`settings-save-state ${isDirty ? 'is-dirty' : 'is-saved'}`} role="status">
              {isDirty ? <Bell size={15} aria-hidden="true" /> : <Check size={15} aria-hidden="true" />}
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
