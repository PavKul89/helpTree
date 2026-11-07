import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  CheckCircle2,
  FilterX,
  History,
  Link2,
  RefreshCw,
  ShieldAlert,
  UserRound,
} from 'lucide-react';
import {
  adminApi,
  type AdminModerationActionType,
  type AdminModerationStatus,
  type AdminReport,
  type AdminReportDetails,
  type AdminReportEventType,
  type AdminReportPriority,
  type AdminReportReason,
  type AdminReportStatus,
  type AdminReportTargetType,
  type AdminReportsQuery,
} from '../../api/adminApi';
import type { PageResponse } from '../../api/walletApi';
import { Pagination } from '../../components/Pagination';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { useToast } from '../../components/Toast';
import { useAuth } from '../../context/AuthContext';
import { formatDateTime, formatNumber, getApiErrorMessage } from './adminUtils';

const PAGE_SIZE = 20;

const TARGET_LABELS: Record<AdminReportTargetType, string> = {
  POST: 'Заявка',
  USER: 'Пользователь',
  COMMENT: 'Комментарий',
  REVIEW: 'Отзыв',
  MESSAGE: 'Сообщение',
  HELP: 'Помощь (архив)',
  CHAT: 'Чат (архив)',
  REPORT: 'Жалоба',
};

const REASON_LABELS: Record<AdminReportReason, string> = {
  SPAM: 'Спам',
  ABUSE: 'Оскорбление',
  HARASSMENT: 'Травля',
  INAPPROPRIATE: 'Неуместный контент',
  FRAUD: 'Мошенничество',
  IMPERSONATION: 'Выдача себя за другого',
  OTHER: 'Другое',
};

const STATUS_LABELS: Record<AdminReportStatus, string> = {
  OPEN: 'Открыта',
  IN_REVIEW: 'В работе',
  RESOLVED: 'Решена',
  REJECTED: 'Отклонена',
  DUPLICATE: 'Дубликат',
};

const PRIORITY_LABELS: Record<AdminReportPriority, string> = {
  LOW: 'Низкий',
  NORMAL: 'Обычный',
  HIGH: 'Высокий',
  URGENT: 'Срочный',
};

const EVENT_LABELS: Record<AdminReportEventType, string> = {
  CREATED: 'Жалоба создана',
  ASSIGNED: 'Назначена модератору',
  STATUS_CHANGED: 'Смена статуса',
  NOTE: 'Внутренняя заметка',
  ESCALATED: 'Эскалация',
  RESOLVED: 'Решение принято',
  REOPENED: 'Переоткрыта',
  LINKED_DUPLICATE: 'Связана с дубликатом',
  ACTION_APPLIED: 'Действие применено',
};

const ACTION_LABELS: Record<AdminModerationActionType, string> = {
  WARN: 'Предупреждение',
  MUTE: 'Мут',
  UNMUTE: 'Снять мут',
  BAN: 'Бан',
  UNBAN: 'Снять бан',
  DELETE_CONTENT: 'Удалить контент (устар.)',
  HIDE_CONTENT: 'Скрыть контент',
  REMOVE_CONTENT: 'Удалить контент',
  RESTORE_CONTENT: 'Восстановить контент',
  RESOLVE_REPORT: 'Решить жалобу',
  DISMISS_REPORT: 'Отклонить жалобу',
  LINK_DUPLICATE: 'Связать дубликат',
  ESCALATE: 'Эскалировать',
  OTHER: 'Другое',
};

const MODERATION_STATUS_LABELS: Record<AdminModerationStatus, string> = {
  ACTIVE: 'Активен',
  HIDDEN: 'Скрыт',
  REMOVED: 'Удалён модерацией',
};

const STATUS_BADGES: Record<AdminReportStatus, string> = {
  OPEN: 'admin-badge admin-badge--lime',
  IN_REVIEW: 'admin-badge admin-badge--vip',
  RESOLVED: 'admin-badge admin-badge--status-active',
  REJECTED: 'admin-badge admin-badge--neutral',
  DUPLICATE: 'admin-badge admin-badge--status-helper',
};

const PRIORITY_BADGES: Record<AdminReportPriority, string> = {
  LOW: 'admin-badge admin-badge--neutral',
  NORMAL: 'admin-badge admin-badge--neutral',
  HIGH: 'admin-badge admin-badge--vip',
  URGENT: 'admin-badge admin-badge--danger',
};

const REPORT_LEVEL_ACTIONS: AdminModerationActionType[] = [
  'RESOLVE_REPORT',
  'DISMISS_REPORT',
  'LINK_DUPLICATE',
  'ESCALATE',
  'OTHER',
];

/** Only the actions the backend actually supports for the target are offered. */
const actionsForTarget = (targetType?: AdminReportTargetType): AdminModerationActionType[] => {
  if (targetType === 'USER') {
    return ['WARN', 'MUTE', 'UNMUTE', 'BAN', 'UNBAN', ...REPORT_LEVEL_ACTIONS];
  }
  if (targetType === 'POST' || targetType === 'COMMENT' || targetType === 'REVIEW') {
    return ['HIDE_CONTENT', 'REMOVE_CONTENT', 'RESTORE_CONTENT', ...REPORT_LEVEL_ACTIONS];
  }
  // Messages and historical targets have no visibility switch exposed by the
  // backend, so only report-level decisions remain.
  return [...REPORT_LEVEL_ACTIONS];
};

const isClosed = (status: AdminReportStatus): boolean =>
  status === 'RESOLVED' || status === 'REJECTED' || status === 'DUPLICATE';

const toLocalDateTime = (value: string, endOfDay: boolean): string | undefined => {
  if (!value) return undefined;
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`;
};

const needsExpiry = (actionType: AdminModerationActionType): boolean =>
  actionType === 'HIDE_CONTENT' || actionType === 'MUTE' || actionType === 'BAN';

interface FilterDraft {
  q: string;
  status: '' | AdminReportStatus;
  targetType: '' | AdminReportTargetType;
  reason: '' | AdminReportReason;
  priority: '' | AdminReportPriority;
  from: string;
  to: string;
}

const EMPTY_DRAFT: FilterDraft = {
  q: '',
  status: '',
  targetType: '',
  reason: '',
  priority: '',
  from: '',
  to: '',
};

export const AdminReportsPage: React.FC = () => {
  const { showToast } = useToast();
  const { user } = useAuth();

  const [draft, setDraft] = useState<FilterDraft>(EMPTY_DRAFT);
  const [appliedFilters, setAppliedFilters] = useState<AdminReportsQuery>({});
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);

  const [data, setData] = useState<PageResponse<AdminReport> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [details, setDetails] = useState<AdminReportDetails | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState<string | null>(null);
  const [detailsReloadKey, setDetailsReloadKey] = useState(0);

  const [priorityDraft, setPriorityDraft] = useState<AdminReportPriority>('NORMAL');
  const [noteDraft, setNoteDraft] = useState('');
  const [metaBusy, setMetaBusy] = useState(false);
  const [metaError, setMetaError] = useState<string | null>(null);

  const [actionType, setActionType] = useState<AdminModerationActionType>('RESOLVE_REPORT');
  const [actionReason, setActionReason] = useState('');
  const [actionResolution, setActionResolution] = useState('');
  const [duplicateOfId, setDuplicateOfId] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [actionBusy, setActionBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [assignBusy, setAssignBusy] = useState(false);

  const query = useMemo<AdminReportsQuery>(
    () => ({ ...appliedFilters, page, size: PAGE_SIZE }),
    [appliedFilters, page]
  );

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    setLoading(true);
    setError(null);

    adminApi
      .getReports(query, controller.signal)
      .then((response) => {
        if (!cancelled) setData(response);
      })
      .catch((loadError) => {
        if (cancelled || controller.signal.aborted) return;
        setError(getApiErrorMessage(loadError, 'Не удалось загрузить очередь жалоб.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [query, reloadKey]);

  useEffect(() => {
    if (selectedId === null) {
      setDetails(null);
      setDetailsError(null);
      return;
    }

    const controller = new AbortController();
    let cancelled = false;

    setDetailsLoading(true);
    setDetailsError(null);

    adminApi
      .getReportDetails(selectedId)
      .then((response) => {
        if (cancelled) return;
        setDetails(response);
        setPriorityDraft(response.report.priority);
        setNoteDraft(response.report.internalNote ?? '');
        setActionType(
          actionsForTarget(response.report.targetType).includes('RESOLVE_REPORT')
            ? 'RESOLVE_REPORT'
            : actionsForTarget(response.report.targetType)[0]
        );
        setActionReason('');
        setActionResolution('');
        setDuplicateOfId('');
        setExpiresAt('');
        setActionError(null);
      })
      .catch((loadError) => {
        if (cancelled || controller.signal.aborted) return;
        setDetailsError(getApiErrorMessage(loadError, 'Не удалось загрузить жалобу.'));
      })
      .finally(() => {
        if (!cancelled) setDetailsLoading(false);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [selectedId, detailsReloadKey]);

  const applyFilters = useCallback(() => {
    setAppliedFilters({
      q: draft.q.trim() || undefined,
      status: draft.status || undefined,
      targetType: draft.targetType || undefined,
      reason: draft.reason || undefined,
      priority: draft.priority || undefined,
      from: toLocalDateTime(draft.from, false),
      to: toLocalDateTime(draft.to, true),
    });
    setPage(0);
    setReloadKey((value) => value + 1);
  }, [draft]);

  const resetFilters = () => {
    setDraft(EMPTY_DRAFT);
    setAppliedFilters({});
    setPage(0);
    setReloadKey((value) => value + 1);
  };

  const refresh = () => {
    setReloadKey((value) => value + 1);
    setDetailsReloadKey((value) => value + 1);
  };

  const saveMetadata = async () => {
    if (!details || metaBusy) return;
    setMetaBusy(true);
    setMetaError(null);
    try {
      await adminApi.updateReport(details.report.id, {
        priority: priorityDraft,
        internalNote: noteDraft,
      });
      showToast('Приоритет и внутренняя заметка сохранены.', 'success');
      refresh();
    } catch (failure) {
      setMetaError(getApiErrorMessage(failure, 'Не удалось сохранить метаданные жалобы.'));
    } finally {
      setMetaBusy(false);
    }
  };

  const takeIntoWork = async () => {
    if (!details || assignBusy) return;
    setAssignBusy(true);
    setActionError(null);
    try {
      await adminApi.assignReport(details.report.id);
      showToast('Жалоба взята в работу.', 'success');
      refresh();
    } catch (failure) {
      setActionError(getApiErrorMessage(failure, 'Не удалось назначить жалобу.'));
    } finally {
      setAssignBusy(false);
    }
  };

  const submitAction = async () => {
    if (!details || actionBusy) return;
    if (!actionReason.trim()) {
      setActionError('Опишите причину решения.');
      return;
    }
    if (actionType === 'LINK_DUPLICATE' && !duplicateOfId.trim()) {
      setActionError('Укажите ID оригинальной жалобы.');
      return;
    }
    setActionBusy(true);
    setActionError(null);
    try {
      await adminApi.applyReportAction(details.report.id, {
        actionType,
        reason: actionReason.trim(),
        resolution: actionResolution.trim() || undefined,
        duplicateOfId:
          actionType === 'LINK_DUPLICATE' ? Number(duplicateOfId) : undefined,
        expiresAt: needsExpiry(actionType) && expiresAt ? expiresAt : undefined,
      });
      showToast(`Действие «${ACTION_LABELS[actionType]}» применено.`, 'success');
      setActionReason('');
      setActionResolution('');
      setDuplicateOfId('');
      setExpiresAt('');
      refresh();
    } catch (failure) {
      setActionError(getApiErrorMessage(failure, 'Не удалось применить решение.'));
    } finally {
      setActionBusy(false);
    }
  };

  const reports = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const hasDraftFilters = Object.values(draft).some((value) => value !== '');
  const hasAppliedFilters = Object.values(appliedFilters).some((value) => value !== undefined);
  const availableActions = actionsForTarget(details?.report.targetType);
  const reportClosed = details ? isClosed(details.report.status) : false;
  const assignedToMe =
    details?.report.assignedModeratorId !== undefined &&
    details?.report.assignedModeratorId === user?.id;

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <ShieldAlert size={17} aria-hidden="true" />
        <p>
          Очередь жалоб <code>GET /api/admin/reports</code>. Статус меняется только через{' '}
          <code>assign</code> и <code>actions</code>: сервер пишет событие истории и действие
          модерации в одной транзакции. Внутренняя заметка не видна автору жалобы.
        </p>
      </div>

      <section className="admin-section" aria-labelledby="admin-report-filter-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Фильтры</span>
            <h2 id="admin-report-filter-title">Очередь жалоб</h2>
            <p className="admin-section-description">
              Фильтры и пагинация применяются на сервере.
            </p>
          </div>
          <span className="admin-count-badge">
            <ShieldAlert size={15} aria-hidden="true" />
            {formatNumber(totalElements)}
          </span>
        </div>

        <div className="admin-toolbar">
          <label className="admin-filter">
            <span>Поиск</span>
            <input
              type="search"
              value={draft.q}
              onChange={(event) => setDraft({ ...draft, q: event.target.value })}
              placeholder="Описание или автор жалобы"
              aria-label="Поиск по жалобам"
            />
          </label>

          <label className="admin-filter">
            <span>Статус</span>
            <select
              value={draft.status}
              onChange={(event) =>
                setDraft({ ...draft, status: event.target.value as FilterDraft['status'] })
              }
            >
              <option value="">Все</option>
              {(Object.keys(STATUS_LABELS) as AdminReportStatus[]).map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABELS[status]}
                </option>
              ))}
            </select>
          </label>

          <label className="admin-filter">
            <span>Объект</span>
            <select
              value={draft.targetType}
              onChange={(event) =>
                setDraft({ ...draft, targetType: event.target.value as FilterDraft['targetType'] })
              }
            >
              <option value="">Все</option>
              {(Object.keys(TARGET_LABELS) as AdminReportTargetType[])
                .filter((target) => target !== 'REPORT')
                .map((target) => (
                  <option key={target} value={target}>
                    {TARGET_LABELS[target]}
                  </option>
                ))}
            </select>
          </label>

          <label className="admin-filter">
            <span>Причина</span>
            <select
              value={draft.reason}
              onChange={(event) =>
                setDraft({ ...draft, reason: event.target.value as FilterDraft['reason'] })
              }
            >
              <option value="">Все</option>
              {(Object.keys(REASON_LABELS) as AdminReportReason[]).map((reason) => (
                <option key={reason} value={reason}>
                  {REASON_LABELS[reason]}
                </option>
              ))}
            </select>
          </label>

          <label className="admin-filter">
            <span>Приоритет</span>
            <select
              value={draft.priority}
              onChange={(event) =>
                setDraft({ ...draft, priority: event.target.value as FilterDraft['priority'] })
              }
            >
              <option value="">Все</option>
              {(Object.keys(PRIORITY_LABELS) as AdminReportPriority[]).map((priority) => (
                <option key={priority} value={priority}>
                  {PRIORITY_LABELS[priority]}
                </option>
              ))}
            </select>
          </label>

          <label className="admin-filter">
            <span>С даты</span>
            <input
              type="date"
              value={draft.from}
              max={draft.to || undefined}
              onChange={(event) => setDraft({ ...draft, from: event.target.value })}
            />
          </label>

          <label className="admin-filter">
            <span>По дату</span>
            <input
              type="date"
              value={draft.to}
              min={draft.from || undefined}
              onChange={(event) => setDraft({ ...draft, to: event.target.value })}
            />
          </label>

          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={resetFilters}
              disabled={!hasDraftFilters && !hasAppliedFilters}
            >
              <FilterX size={15} aria-hidden="true" />
              Сбросить
            </button>
            <button
              type="button"
              className="admin-button admin-button--secondary admin-button--compact"
              onClick={applyFilters}
              disabled={loading}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Применить
            </button>
          </div>
        </div>
      </section>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={5} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Жалобы недоступны"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : reports.length === 0 ? (
        <AdminEmptyState
          title="Жалоб нет"
          description="По выбранным фильтрам жалоб не найдено."
          action={
            <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
              <FilterX size={16} aria-hidden="true" />
              Сбросить фильтры
            </button>
          }
        />
      ) : (
        <section className="admin-section" aria-labelledby="admin-report-list-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Список</span>
              <h2 id="admin-report-list-title">Найдено жалоб</h2>
            </div>
            <span className="admin-count-badge">{formatNumber(totalElements)}</span>
          </div>

          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Жалоба</th>
                  <th>Объект</th>
                  <th>Причина</th>
                  <th>Статус</th>
                  <th>Приоритет</th>
                  <th>Назначена</th>
                  <th aria-label="Действия" />
                </tr>
              </thead>
              <tbody>
                {reports.map((report) => (
                  <tr key={report.id}>
                    <td>
                      <div className="admin-user-cell">
                        <div>
                          <strong>#{report.id}</strong>
                          <span>{formatDateTime(report.createdAt)}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="admin-badge admin-badge--neutral">
                        {TARGET_LABELS[report.targetType]} #{report.targetId}
                      </span>
                    </td>
                    <td>
                      <span>{REASON_LABELS[report.reason]}</span>
                      {report.description && (
                        <div className="admin-muted">{report.description}</div>
                      )}
                    </td>
                    <td>
                      <span className={STATUS_BADGES[report.status]}>
                        {STATUS_LABELS[report.status]}
                      </span>
                    </td>
                    <td>
                      <span className={PRIORITY_BADGES[report.priority]}>
                        {PRIORITY_LABELS[report.priority]}
                      </span>
                    </td>
                    <td>
                      {report.assignedModeratorName ? (
                        <span className="admin-muted">{report.assignedModeratorName}</span>
                      ) : (
                        <span className="admin-muted">—</span>
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="admin-button admin-button--ghost admin-button--compact"
                        onClick={() => setSelectedId(report.id)}
                      >
                        Открыть
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            page={page + 1}
            totalPages={totalPages}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            style={{ marginTop: '18px' }}
          />
        </section>
      )}

      {selectedId !== null && (
        <section className="admin-section" aria-labelledby="admin-report-detail-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Рассмотрение</span>
              <h2 id="admin-report-detail-title">Жалоба #{selectedId}</h2>
            </div>
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={() => setSelectedId(null)}
            >
              Закрыть
            </button>
          </div>

          {detailsLoading ? (
            <AdminLoadingBlock rows={4} />
          ) : detailsError ? (
            <AdminErrorState
              title="Жалоба недоступна"
              message={detailsError}
              onRetry={() => setDetailsReloadKey((value) => value + 1)}
            />
          ) : details ? (
            <div className="admin-stack">
              <div className="admin-badge-row">
                <span className={STATUS_BADGES[details.report.status]}>
                  {STATUS_LABELS[details.report.status]}
                </span>
                <span className={PRIORITY_BADGES[details.report.priority]}>
                  {PRIORITY_LABELS[details.report.priority]}
                </span>
                <span className="admin-badge admin-badge--neutral">
                  {TARGET_LABELS[details.report.targetType]} #{details.report.targetId}
                </span>
                <span className="admin-badge admin-badge--neutral">
                  {REASON_LABELS[details.report.reason]}
                </span>
              </div>

              <dl className="admin-result-grid">
                <div>
                  <dt>Автор жалобы</dt>
                  <dd>
                    {details.report.reporterName ?? '—'}
                    {details.report.reporterEmail && (
                      <small className="admin-muted"> · {details.report.reporterEmail}</small>
                    )}
                  </dd>
                </div>
                <div>
                  <dt>Создана</dt>
                  <dd>{formatDateTime(details.report.createdAt)}</dd>
                </div>
                <div>
                  <dt>Обновлена</dt>
                  <dd>{formatDateTime(details.report.updatedAt)}</dd>
                </div>
                <div>
                  <dt>Назначена</dt>
                  <dd>{details.report.assignedModeratorName ?? '—'}</dd>
                </div>
                <div>
                  <dt>Решение</dt>
                  <dd>{details.report.resolution ?? '—'}</dd>
                </div>
                <div>
                  <dt>Рассмотрел</dt>
                  <dd>
                    {details.report.resolvedByName ?? '—'}
                    {details.report.resolvedAt
                      ? ` · ${formatDateTime(details.report.resolvedAt)}`
                      : ''}
                  </dd>
                </div>
                {details.report.duplicateOfId && (
                  <div>
                    <dt>Дубликат</dt>
                    <dd>Жалоба #{details.report.duplicateOfId}</dd>
                  </div>
                )}
              </dl>

              {details.report.description && (
                <div className="admin-subsection">
                  <h3>Описание от автора</h3>
                  <p>{details.report.description}</p>
                </div>
              )}

              <div className="admin-subsection">
                <h3>Объект жалобы</h3>
                {details.target.exists ? (
                  <>
                    <div className="admin-badge-row">
                      <span className="admin-badge admin-badge--neutral">
                        {TARGET_LABELS[details.target.targetType]} #{details.target.targetId}
                      </span>
                      {details.target.moderationStatus && (
                        <span className="admin-badge admin-badge--vip">
                          {MODERATION_STATUS_LABELS[details.target.moderationStatus]}
                        </span>
                      )}
                      {details.target.deleted && (
                        <span className="admin-badge admin-badge--danger">Удалён автором</span>
                      )}
                      {details.target.ownerName && (
                        <span className="admin-muted">
                          владелец: {details.target.ownerName}
                          {details.target.ownerId
                            ? ` (ID ${details.target.ownerId})`
                            : ''}
                        </span>
                      )}
                    </div>
                    {details.target.title && <p>{details.target.title}</p>}
                    <p className="admin-muted">{details.target.content || 'Без содержимого.'}</p>
                    {(details.target.targetType === 'USER' ||
                      details.target.targetType === 'POST' ||
                      details.target.targetType === 'COMMENT' ||
                      details.target.targetType === 'REVIEW') &&
                      details.target.ownerId && (
                        <div className="admin-review-links">
                          <Link to={`/admin/users/${details.target.ownerId}`}>
                            <UserRound size={13} aria-hidden="true" />
                            Карточка владельца
                          </Link>
                          {details.target.targetType === 'POST' && (
                            <Link to={`/posts/${details.target.targetId}`}>
                              Открыть заявку
                            </Link>
                          )}
                        </div>
                      )}
                  </>
                ) : (
                  <div className="admin-inline-alert">
                    <AlertTriangle size={15} aria-hidden="true" />
                    Объект не найден. Жалоба сохраняется для аудита, но контент мог быть удалён.
                  </div>
                )}
              </div>

              {reportClosed ? (
                <div className="admin-notice">
                  <CheckCircle2 size={17} aria-hidden="true" />
                  <p>
                    Жалоба закрыта. Повторное решение недоступно; история и действия сохранены
                    ниже.
                  </p>
                </div>
              ) : (
                <div className="admin-form-grid">
                  <div className="admin-operation">
                    <h3>Метаданные</h3>
                    <label className="admin-field">
                      <span>Приоритет</span>
                      <select
                        value={priorityDraft}
                        onChange={(event) =>
                          setPriorityDraft(event.target.value as AdminReportPriority)
                        }
                      >
                        {(Object.keys(PRIORITY_LABELS) as AdminReportPriority[]).map((priority) => (
                          <option key={priority} value={priority}>
                            {PRIORITY_LABELS[priority]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="admin-field">
                      <span>Внутренняя заметка</span>
                      <textarea
                        rows={4}
                        maxLength={4000}
                        value={noteDraft}
                        onChange={(event) => setNoteDraft(event.target.value)}
                        placeholder="Видна только администраторам"
                      />
                    </label>
                    <p className="admin-field-hint">
                      Заметка не отображается автору жалобы и попадает в историю как событие NOTE.
                    </p>
                    {metaError && <div className="admin-inline-alert">{metaError}</div>}
                    <div className="admin-toolbar-actions">
                      {!assignedToMe && (
                        <button
                          type="button"
                          className="admin-button admin-button--secondary"
                          onClick={takeIntoWork}
                          disabled={assignBusy || metaBusy}
                        >
                          <UserRound size={15} aria-hidden="true" />
                          {assignBusy ? 'Назначаем…' : 'Взять в работу'}
                        </button>
                      )}
                      <button
                        type="button"
                        className="admin-button admin-button--primary"
                        onClick={saveMetadata}
                        disabled={metaBusy}
                      >
                        {metaBusy ? 'Сохраняем…' : 'Сохранить'}
                      </button>
                    </div>
                  </div>

                  <div className="admin-operation">
                    <h3>Решение и санкции</h3>
                    <label className="admin-field">
                      <span>Действие</span>
                      <select
                        value={actionType}
                        onChange={(event) =>
                          setActionType(event.target.value as AdminModerationActionType)
                        }
                      >
                        {availableActions.map((action) => (
                          <option key={action} value={action}>
                            {ACTION_LABELS[action]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="admin-field">
                      <span>Причина (обязательно)</span>
                      <textarea
                        rows={3}
                        maxLength={1000}
                        value={actionReason}
                        onChange={(event) => setActionReason(event.target.value)}
                        placeholder="Например: подтверждено оскорбление в комментарии"
                      />
                    </label>
                    <label className="admin-field">
                      <span>Резолюция (необязательно)</span>
                      <input
                        type="text"
                        maxLength={1000}
                        value={actionResolution}
                        onChange={(event) => setActionResolution(event.target.value)}
                        placeholder="Формулировка для истории"
                      />
                    </label>
                    {actionType === 'LINK_DUPLICATE' && (
                      <label className="admin-field">
                        <span>ID оригинальной жалобы</span>
                        <input
                          type="number"
                          min={1}
                          value={duplicateOfId}
                          onChange={(event) => setDuplicateOfId(event.target.value)}
                        />
                      </label>
                    )}
                    {needsExpiry(actionType) && (
                      <label className="admin-field">
                        <span>Действует до (необязательно)</span>
                        <input
                          type="datetime-local"
                          value={expiresAt}
                          onChange={(event) => setExpiresAt(event.target.value)}
                        />
                      </label>
                    )}
                    <p className="admin-field-hint">
                      Решение закроет жалобу. Действие и событие истории записываются атомарно.
                    </p>
                    {actionError && <div className="admin-inline-alert">{actionError}</div>}
                    <div className="admin-toolbar-actions">
                      <button
                        type="button"
                        className="admin-button admin-button--primary"
                        onClick={submitAction}
                        disabled={actionBusy}
                      >
                        {actionBusy ? 'Применяем…' : 'Применить решение'}
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {details.report.internalNote && (
                <div className="admin-subsection">
                  <h3>Текущая внутренняя заметка</h3>
                  <p>{details.report.internalNote}</p>
                </div>
              )}

              <div className="admin-subsection">
                <h3>
                  <History size={15} aria-hidden="true" /> История
                </h3>
                {details.events.length === 0 ? (
                  <p className="admin-muted">Событий пока нет.</p>
                ) : (
                  <ul className="admin-timeline">
                    {details.events.map((event) => (
                      <li key={event.id}>
                        <span className="admin-timeline-emoji" aria-hidden="true">
                          •
                        </span>
                        <div>
                          <div className="admin-timeline-head">
                            <strong>{EVENT_LABELS[event.eventType]}</strong>
                            <span>{formatDateTime(event.createdAt)}</span>
                          </div>
                          <p className="admin-muted">
                            {event.actorName ?? 'Система'}
                            {event.fromStatus || event.toStatus
                              ? ` · ${event.fromStatus ? STATUS_LABELS[event.fromStatus] : '—'} → ${
                                  event.toStatus ? STATUS_LABELS[event.toStatus] : '—'
                                }`
                              : ''}
                          </p>
                          {event.internalNote && <p>{event.internalNote}</p>}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="admin-subsection">
                <h3>
                  <Link2 size={15} aria-hidden="true" /> Применённые действия
                </h3>
                {details.actions.length === 0 ? (
                  <p className="admin-muted">Действий по жалобе ещё не было.</p>
                ) : (
                  <ul className="admin-record-list">
                    {details.actions.map((action) => (
                      <li key={action.id} className="admin-record">
                        <div className="admin-record-main">
                          <div className="admin-record-title">
                            {ACTION_LABELS[action.actionType]} ·{' '}
                            {TARGET_LABELS[action.targetType]} #{action.targetId}
                          </div>
                          <p>{action.reason}</p>
                          <div className="admin-record-meta">
                            <span>{action.moderatorName ?? 'Модератор'}</span>
                            <span>{formatDateTime(action.createdAt)}</span>
                            {action.expiresAt && (
                              <span>до {formatDateTime(action.expiresAt)}</span>
                            )}
                          </div>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          ) : null}
        </section>
      )}
    </div>
  );
};
