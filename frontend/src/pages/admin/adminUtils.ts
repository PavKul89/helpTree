import { getApiErrorInfo } from '../../utils/apiErrors';
import type { AdminPostStatus, AdminUserStatus } from '../../api/adminApi';

export const USER_STATUS_LABELS: Record<AdminUserStatus, string> = {
  NEWBIE: 'Новичок',
  HELPER: 'Помощник',
  ACTIVE: 'Активный',
  DEBTOR: 'Должник',
};

export const POST_STATUS_LABELS: Record<AdminPostStatus, string> = {
  OPEN: 'Открыт',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Выполнен',
  CANCELLED: 'Отменён',
};

export const POST_STATUS_ORDER: AdminPostStatus[] = [
  'OPEN',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
];

const MONTHS_SHORT = [
  'янв',
  'фев',
  'мар',
  'апр',
  'май',
  'июн',
  'июл',
  'авг',
  'сен',
  'окт',
  'ноя',
  'дек',
];

export const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const formatDateOnly = (value?: string | null): string => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
};

export const formatDateInput = (value?: string | null): string => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString().slice(0, 10);
};

export const formatNumber = (value?: number | null): string =>
  typeof value === 'number' && Number.isFinite(value)
    ? value.toLocaleString('ru-RU')
    : '—';

export const formatRating = (value?: number | null): string =>
  typeof value === 'number' && Number.isFinite(value) ? value.toFixed(2) : '—';

/** HelpService returns month keys as `MM/YYYY`. */
export const formatMonthKey = (key: string): string => {
  const [month, year] = key.split('/');
  const index = Number(month) - 1;
  if (!year || Number.isNaN(index) || index < 0 || index > 11) return key;
  return `${MONTHS_SHORT[index]} ${year}`;
};

export const isUserBlocked = (user: { blockedAt?: string; blockedUntil?: string }): boolean => {
  if (user.blockedUntil) {
    const until = new Date(user.blockedUntil).getTime();
    if (!Number.isNaN(until) && until > Date.now()) return true;
  }
  return Boolean(user.blockedAt);
};

export const getApiErrorMessage = (error: unknown, fallback = 'Операция не выполнена.'): string => {
  const info = getApiErrorInfo(error);
  if (info.status === 403) {
    return 'Сервер отклонил доступ (403). Убедитесь, что вы вошли как администратор.';
  }
  if (info.status === 401) {
    return 'Сессия истекла (401). Войдите заново.';
  }
  if (info.message && !/^validation failed/i.test(info.message)) {
    return info.message;
  }
  const fieldErrors = Object.values(info.fields);
  if (fieldErrors.length > 0) {
    return fieldErrors.join(' ');
  }
  return fallback;
};

export const getApiFieldErrors = (error: unknown): Record<string, string> =>
  getApiErrorInfo(error).fields;
