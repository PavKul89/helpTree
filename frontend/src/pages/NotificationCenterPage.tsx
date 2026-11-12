import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  BellRing,
  CheckCheck,
  Loader2,
  RefreshCw,
  Settings2,
  Sparkles,
} from 'lucide-react';
import { notificationApi, NOTIFICATION_COUNT_EVENT, NOTIFICATION_EVENT } from '../api/notificationApi';
import { getApiErrorInfo } from '../utils/apiErrors';
import { getRelativeTime } from '../utils/dateUtils';
import { useToast } from '../components/Toast';
import { Pagination } from '../components/Pagination';
import type {
  Notification,
  NotificationChannel,
  NotificationPreferencesResponse,
  NotificationType,
} from '../types';
import './NotificationCenterPage.css';

const PAGE_SIZE = 20;

const TYPE_META: Record<NotificationType, { label: string; emoji: string }> = {
  NEW_HELP_RESPONSE: { label: 'Новый отклик', emoji: '✋' },
  HELP_ACCEPTED: { label: 'Отклик принят', emoji: '🤝' },
  HELP_COMPLETED: { label: 'Помощь выполнена', emoji: '📦' },
  HELP_CONFIRMED: { label: 'Помощь подтверждена', emoji: '✅' },
  HELP_CANCELLED: { label: 'Помощь отменена', emoji: '↩️' },
  NEW_REVIEW: { label: 'Новый отзыв', emoji: '⭐' },
  REVIEW_REPLIED: { label: 'Ответ на отзыв', emoji: '💬' },
  REVIEW_UPDATED: { label: 'Отзыв изменён', emoji: '✏️' },
  HELP_SLA_REMINDER: { label: 'Помощь скоро просрочится', emoji: '⏰' },
  PAID_FEATURE_EXPIRING: { label: 'Платная функция истекает', emoji: '⌛' },
  NEW_REPORT: { label: 'Новая жалоба', emoji: '🚩' },
  REPORT_ACCEPTED: { label: 'Жалоба принята', emoji: '🛡️' },
  REPORT_REJECTED: { label: 'Жалоба отклонена', emoji: '📭' },
  MODERATION_ACTION: { label: 'Действие модерации', emoji: '⚖️' },
  ACCOUNT_BLOCKED: { label: 'Аккаунт заблокирован', emoji: '🚫' },
  ACCOUNT_UNBLOCKED: { label: 'Аккаунт разблокирован', emoji: '🔓' },
  COINS_EARNED: { label: 'Начисление монет', emoji: '🪙' },
  COINS_SPENT: { label: 'Списание монет', emoji: '💸' },
  ACHIEVEMENT_EARNED: { label: 'Достижение', emoji: '🏆' },
};

const CHANNEL_META: Record<NotificationChannel, { label: string; hint: string }> = {
  IN_APP: { label: 'В приложении', hint: 'Лента и счётчик внутри HelpTree' },
  EMAIL: { label: 'Email', hint: 'Письмо на адрес аккаунта' },
  TELEGRAM: { label: 'Telegram', hint: 'Сообщение в привязанный чат' },
};

const typeMeta = (type: NotificationType) =>
  TYPE_META[type] ?? { label: type, emoji: '🔔' };

const describeError = (error: unknown): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 401 || info.status === 403) {
    return 'Недостаточно прав для работы с уведомлениями.';
  }
  if (!info.status) {
    return 'Не удалось связаться с сервером. Проверьте подключение и повторите попытку.';
  }
  return info.message && !/validation/i.test(info.message)
    ? info.message
    : 'Не удалось загрузить уведомления. Попробуйте ещё раз.';
};

export const NotificationCenterPage: React.FC = () => {
  const { showToast } = useToast();
  const [tab, setTab] = useState<'feed' | 'settings'>('feed');

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [markingAll, setMarkingAll] = useState(false);
  const [markingId, setMarkingId] = useState<number | null>(null);

  const [preferences, setPreferences] = useState<NotificationPreferencesResponse | null>(null);
  const [preferencesLoading, setPreferencesLoading] = useState(false);
  const [preferencesError, setPreferencesError] = useState<string | null>(null);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const loadUnreadCount = useCallback(async () => {
    try {
      const response = await notificationApi.getUnreadCount();
      setUnreadCount(response.unreadCount);
    } catch (error) {
      console.error(error);
    }
  }, []);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const response = await notificationApi.getNotifications({
        page,
        size: PAGE_SIZE,
        unreadOnly,
      });
      setNotifications(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (error) {
      setLoadError(describeError(error));
    } finally {
      setLoading(false);
    }
  }, [page, unreadOnly]);

  const loadPreferences = useCallback(async () => {
    setPreferencesLoading(true);
    setPreferencesError(null);
    try {
      setPreferences(await notificationApi.getPreferences());
    } catch (error) {
      setPreferencesError(describeError(error));
    } finally {
      setPreferencesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadNotifications();
    void loadUnreadCount();
  }, [loadNotifications, loadUnreadCount]);

  useEffect(() => {
    if (tab === 'settings' && !preferences) {
      void loadPreferences();
    }
  }, [tab, preferences, loadPreferences]);

  /**
   * The Navbar owns the single STOMP connection. It forwards each push here, and
   * the list deduplicates by id, so a re-delivered event or a refresh cannot
   * show the same notification twice.
   */
  useEffect(() => {
    const handleNotification = (event: Event) => {
      const detail = (
        event as CustomEvent<{ notification?: Notification; unreadCount?: number }>
      ).detail;
      if (typeof detail?.unreadCount === 'number') {
        setUnreadCount(Math.max(0, detail.unreadCount));
      }
      const notification = detail?.notification;
      if (!notification || notification.read) return;

      setNotifications((current) => {
        if (current.some((item) => item.id === notification.id)) {
          return current;
        }
        if (page !== 0) return current;
        return [notification, ...current].slice(0, PAGE_SIZE);
      });
      if (page === 0) {
        setTotalElements((current) => current + 1);
      }
    };

    window.addEventListener(NOTIFICATION_EVENT, handleNotification);
    return () => window.removeEventListener(NOTIFICATION_EVENT, handleNotification);
  }, [page, unreadOnly]);

  const publishUnreadCount = (next: number) => {
    setUnreadCount(next);
    window.dispatchEvent(
      new CustomEvent(NOTIFICATION_COUNT_EVENT, { detail: { unreadCount: next } })
    );
  };

  const handleMarkRead = async (notification: Notification) => {
    if (notification.read || markingId !== null) return;
    setMarkingId(notification.id);
    setActionError(null);
    try {
      const response = await notificationApi.markRead(notification.id);
      setNotifications((current) =>
        current
          .map((item) => (item.id === notification.id ? { ...item, read: true } : item))
          .filter((item) => !(unreadOnly && item.read))
      );
      publishUnreadCount(response.unreadCount);
    } catch (error) {
      setActionError(describeError(error));
    } finally {
      setMarkingId(null);
    }
  };

  const handleMarkAllRead = async () => {
    if (markingAll || unreadCount === 0) return;
    setMarkingAll(true);
    setActionError(null);
    try {
      const response = await notificationApi.markAllRead();
      setNotifications((current) =>
        current
          .map((item) => ({ ...item, read: true }))
          .filter((item) => !unreadOnly)
      );
      publishUnreadCount(response.unreadCount);
      showToast('Все уведомления прочитаны', 'success');
    } catch (error) {
      setActionError(describeError(error));
    } finally {
      setMarkingAll(false);
    }
  };

  const handleTogglePreference = async (
    type: NotificationType,
    channel: NotificationChannel,
    enabled: boolean
  ) => {
    const key = `${type}:${channel}`;
    if (savingKey) return;
    setSavingKey(key);
    setPreferencesError(null);
    try {
      const updated = await notificationApi.updatePreferences([{ type, channel, enabled }]);
      setPreferences(updated);
    } catch (error) {
      setPreferencesError(describeError(error));
    } finally {
      setSavingKey(null);
    }
  };

  const preferenceMap = useMemo(() => {
    const map = new Map<string, boolean>();
    preferences?.preferences.forEach((preference) => {
      map.set(`${preference.type}:${preference.channel}`, preference.enabled);
    });
    return map;
  }, [preferences]);

  const activeTypes = preferences?.types ?? (Object.keys(TYPE_META) as NotificationType[]);
  const activeChannels = preferences?.channels ?? (Object.keys(CHANNEL_META) as NotificationChannel[]);

  return (
    <main className="notification-center">
      <header className="notification-center-heading">
        <span className="notification-center-mark" aria-hidden="true">
          <BellRing size={22} />
        </span>
        <div>
          <span className="notification-center-eyebrow">HelpTree · ваш центр событий</span>
          <h1>Уведомления</h1>
          <p>
            Здесь собраны важные события вашего сообщества. Настройте, какие каналы
            должны присылать каждое из них.
          </p>
        </div>
        <div className="notification-center-counter" aria-live="polite">
          <span>{unreadCount}</span>
          <small>непрочитанных</small>
        </div>
      </header>

      <div className="notification-tabs" role="tablist" aria-label="Разделы уведомлений">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'feed'}
          className={`notification-tab ${tab === 'feed' ? 'is-active' : ''}`}
          onClick={() => setTab('feed')}
        >
          <BellRing size={15} aria-hidden="true" /> Лента
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'settings'}
          className={`notification-tab ${tab === 'settings' ? 'is-active' : ''}`}
          onClick={() => setTab('settings')}
        >
          <Settings2 size={15} aria-hidden="true" /> Настройки каналов
        </button>
      </div>

      {tab === 'feed' ? (
        <section className="notification-feed" aria-label="Лента уведомлений">
          <div className="notification-toolbar">
            <label className="notification-switch">
              <input
                type="checkbox"
                checked={unreadOnly}
                onChange={(event) => {
                  setUnreadOnly(event.target.checked);
                  setPage(0);
                }}
              />
              <span>Только непрочитанные</span>
            </label>
            <div className="notification-toolbar-actions">
              <button
                type="button"
                className="notification-button notification-button--ghost"
                onClick={() => void loadNotifications()}
                disabled={loading}
              >
                <RefreshCw size={15} aria-hidden="true" /> Обновить
              </button>
              <button
                type="button"
                className="notification-button notification-button--primary"
                onClick={() => void handleMarkAllRead()}
                disabled={markingAll || unreadCount === 0}
              >
                {markingAll ? (
                  <Loader2 className="notification-spinner" size={15} aria-hidden="true" />
                ) : (
                  <CheckCheck size={15} aria-hidden="true" />
                )}
                Прочитать все
              </button>
            </div>
          </div>

          {actionError && (
            <div className="notification-alert" role="alert">
              <AlertTriangle size={16} aria-hidden="true" />
              <span>{actionError}</span>
            </div>
          )}

          {loading ? (
            <div className="notification-state" role="status">
              <Loader2 className="notification-spinner" size={22} aria-hidden="true" />
              <p>Загружаем уведомления…</p>
            </div>
          ) : loadError ? (
            <div className="notification-state" role="alert">
              <AlertTriangle size={22} aria-hidden="true" />
              <h2>Не удалось загрузить уведомления</h2>
              <p>{loadError}</p>
              <button
                type="button"
                className="notification-button notification-button--ghost"
                onClick={() => void loadNotifications()}
              >
                <RefreshCw size={15} aria-hidden="true" /> Повторить
              </button>
            </div>
          ) : notifications.length === 0 ? (
            <div className="notification-state">
              <Sparkles size={22} aria-hidden="true" />
              <h2>{unreadOnly ? 'Непрочитанных уведомлений нет' : 'Уведомлений пока нет'}</h2>
              <p>
                {unreadOnly
                  ? 'Вы прочитали всё. Новые события появятся здесь.'
                  : 'Как только произойдёт важное событие, оно появится в этой ленте.'}
              </p>
            </div>
          ) : (
            <>
              <ul className="notification-list">
                {notifications.map((notification) => {
                  const meta = typeMeta(notification.type);
                  return (
                    <li
                      key={notification.id}
                      className={`notification-item ${notification.read ? '' : 'is-unread'}`}
                    >
                      <span className="notification-item-icon" aria-hidden="true">
                        {meta.emoji}
                      </span>
                      <div className="notification-item-body">
                        <div className="notification-item-head">
                          <span className="notification-item-type">{meta.label}</span>
                          <time dateTime={notification.createdAt}>
                            {getRelativeTime(notification.createdAt)}
                          </time>
                        </div>
                        <h3>{notification.title}</h3>
                        <p>{notification.body}</p>
                      </div>
                      {!notification.read && (
                        <button
                          type="button"
                          className="notification-mark-read"
                          onClick={() => void handleMarkRead(notification)}
                          disabled={markingId === notification.id}
                          aria-label="Отметить прочитанным"
                          title="Отметить прочитанным"
                        >
                          {markingId === notification.id ? (
                            <Loader2 className="notification-spinner" size={15} aria-hidden="true" />
                          ) : (
                            <CheckCheck size={15} aria-hidden="true" />
                          )}
                        </button>
                      )}
                    </li>
                  );
                })}
              </ul>

              <div className="notification-foot">
                <span>Всего: {totalElements}</span>
                <Pagination
                  page={page + 1}
                  totalPages={totalPages}
                  onPageChange={(next) => setPage(Math.max(0, next - 1))}
                />
              </div>
            </>
          )}
        </section>
      ) : (
        <section className="notification-preferences" aria-label="Настройки каналов">
          <div className="notification-preferences-head">
            <div>
              <h2>Какие каналы использовать</h2>
              <p>
                Настройка действует на новые события. Лента в приложении всегда
                хранит историю, даже если все внешние каналы выключены.
              </p>
            </div>
          </div>

          {preferencesLoading ? (
            <div className="notification-state" role="status">
              <Loader2 className="notification-spinner" size={22} aria-hidden="true" />
              <p>Загружаем настройки…</p>
            </div>
          ) : preferencesError ? (
            <div className="notification-state" role="alert">
              <AlertTriangle size={22} aria-hidden="true" />
              <h2>Не удалось загрузить настройки</h2>
              <p>{preferencesError}</p>
              <button
                type="button"
                className="notification-button notification-button--ghost"
                onClick={() => void loadPreferences()}
              >
                <RefreshCw size={15} aria-hidden="true" /> Повторить
              </button>
            </div>
          ) : (
            <div className="notification-preference-table" role="table" aria-label="Матрица каналов">
              <div className="notification-preference-row notification-preference-row--head" role="row">
                <span role="columnheader">Событие</span>
                {activeChannels.map((channel) => (
                  <span key={channel} role="columnheader" title={CHANNEL_META[channel].hint}>
                    {CHANNEL_META[channel].label}
                  </span>
                ))}
              </div>
              {activeTypes.map((type) => {
                const meta = typeMeta(type);
                return (
                  <div className="notification-preference-row" role="row" key={type}>
                    <span className="notification-preference-type" role="cell">
                      <span aria-hidden="true">{meta.emoji}</span> {meta.label}
                    </span>
                    {activeChannels.map((channel) => {
                      const key = `${type}:${channel}`;
                      const enabled = preferenceMap.get(key) ?? channel === 'IN_APP';
                      return (
                        <label className="notification-preference-cell" role="cell" key={channel}>
                          <input
                            type="checkbox"
                            checked={enabled}
                            disabled={savingKey === key}
                            onChange={(event) =>
                              void handleTogglePreference(type, channel, event.target.checked)
                            }
                            aria-label={`${meta.label}: ${CHANNEL_META[channel].label}`}
                          />
                          <span aria-hidden="true" />
                        </label>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}
    </main>
  );
};

export default NotificationCenterPage;
