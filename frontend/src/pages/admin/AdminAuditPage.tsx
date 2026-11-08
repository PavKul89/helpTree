import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  Download,
  FilterX,
  RefreshCw,
  Search,
  ShieldCheck,
} from 'lucide-react';
import {
  adminApi,
  type AdminAuditLog,
  type AdminAuditQuery,
  type AdminAuditStats,
} from '../../api/adminApi';
import type { PageResponse } from '../../api/walletApi';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { Pagination } from '../../components/Pagination';
import { useToast } from '../../components/Toast';
import { formatDateTime, formatNumber, getApiErrorMessage } from './adminUtils';

const PAGE_SIZE = 25;

const ACTION_LABELS: Record<string, string> = {
  USER_CREATED: 'Пользователь создан',
  USER_INVITED: 'Отправлено приглашение',
  USER_ROLE_CHANGED: 'Изменена роль',
  USER_DELETED: 'Аккаунт удалён',
  USER_RESTORED: 'Аккаунт восстановлен',
  USER_ANONYMIZED: 'Данные анонимизированы',
  USER_DATA_EXPORTED: 'Экспорт данных',
  POST_DELETED: 'Заявка удалена',
  POST_RESTORED: 'Заявка восстановлена',
  POST_STATUS_CHANGED: 'Изменён статус заявки',
  REVIEW_DELETED: 'Отзыв удалён',
  REPORT_ASSIGNED: 'Назначен модератор',
  REPORT_UPDATED: 'Жалоба обновлена',
  REPORT_STATUS_CHANGED: 'Изменён статус жалобы',
  MODERATION_ACTION: 'Модерационное решение',
  COINS_ADJUSTED: 'Начисление HelpCoins',
  RATING_CHANGED: 'Изменён рейтинг',
  SESSION_REVOKED: 'Сессия отозвана',
  SESSION_REVOKED_ALL: 'Отозваны все сессии',
  HELP_OVERRIDE: 'Административный override помощи',
  BULK_OPERATION: 'Массовая операция',
};

const ENTITY_LABELS: Record<string, string> = {
  USER: 'Пользователь',
  POST: 'Заявка',
  REVIEW: 'Отзыв',
  REPORT: 'Жалоба',
  WALLET: 'Кошелёк',
  SESSION: 'Сессия',
  HELP: 'Помощь',
  MODERATION: 'Модерация',
};

const ACTION_ORDER = Object.keys(ACTION_LABELS);
const ENTITY_ORDER = Object.keys(ENTITY_LABELS);

const toLocalDateTime = (value: string, endOfDay: boolean): string | undefined => {
  if (!value) return undefined;
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`;
};

interface DraftFilters {
  action: string;
  entityType: string;
  adminId: string;
  entityId: string;
  q: string;
  fromDate: string;
  toDate: string;
}

const EMPTY_FILTERS: DraftFilters = {
  action: '',
  entityType: '',
  adminId: '',
  entityId: '',
  q: '',
  fromDate: '',
  toDate: '',
};

const renderState = (state?: Record<string, unknown> | null): string => {
  if (!state || Object.keys(state).length === 0) return '—';
  return Object.entries(state)
    .map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`)
    .join(', ');
};

const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

export const AdminAuditPage: React.FC = () => {
  const { showToast } = useToast();

  const [draft, setDraft] = useState<DraftFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<DraftFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);

  const [data, setData] = useState<PageResponse<AdminAuditLog> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [stats, setStats] = useState<AdminAuditStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState<string | null>(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const query = useMemo<AdminAuditQuery>(() => {
    const adminId = Number(applied.adminId);
    const entityId = Number(applied.entityId);
    return {
      page,
      size: PAGE_SIZE,
      q: applied.q.trim() || undefined,
      action: applied.action || undefined,
      entityType: applied.entityType || undefined,
      adminId: Number.isFinite(adminId) && adminId > 0 ? adminId : undefined,
      entityId: Number.isFinite(entityId) && entityId > 0 ? entityId : undefined,
      from: toLocalDateTime(applied.fromDate, false),
      to: toLocalDateTime(applied.toDate, true),
      sort: 'createdAt',
      direction: 'desc',
    };
  }, [applied, page]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    setLoading(true);
    setError(null);

    adminApi
      .getAudit(query, controller.signal)
      .then((response) => {
        if (!cancelled) setData(response);
      })
      .catch((loadError) => {
        if (cancelled || controller.signal.aborted) return;
        setError(getApiErrorMessage(loadError, 'Не удалось загрузить журнал аудита.'));
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
    let cancelled = false;
    setStatsLoading(true);
    setStatsError(null);

    adminApi
      .getAuditStats(
        toLocalDateTime(applied.fromDate, false),
        toLocalDateTime(applied.toDate, true)
      )
      .then((response) => {
        if (!cancelled) setStats(response);
      })
      .catch((loadError) => {
        if (!cancelled) {
          setStatsError(getApiErrorMessage(loadError, 'Статистика аудита недоступна.'));
        }
      })
      .finally(() => {
        if (!cancelled) setStatsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey, applied.fromDate, applied.toDate]);

  const applyFilters = useCallback(
    (event?: React.FormEvent) => {
      event?.preventDefault();
      setApplied({ ...draft });
      setPage(0);
      setReloadKey((value) => value + 1);
    },
    [draft]
  );

  const resetFilters = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setPage(0);
    setReloadKey((value) => value + 1);
  };

  const handleExport = async () => {
    if (exporting) return;
    setExporting(true);
    setExportError(null);
    try {
      const adminId = Number(applied.adminId);
      const entityId = Number(applied.entityId);
      const blob = await adminApi.downloadAuditCsv({
        q: applied.q.trim() || undefined,
        action: applied.action || undefined,
        entityType: applied.entityType || undefined,
        adminId: Number.isFinite(adminId) && adminId > 0 ? adminId : undefined,
        entityId: Number.isFinite(entityId) && entityId > 0 ? entityId : undefined,
        from: toLocalDateTime(applied.fromDate, false),
        to: toLocalDateTime(applied.toDate, true),
      });
      downloadBlob(blob, 'admin-audit.csv');
      showToast('CSV-экспорт журнала подготовлен.', 'success');
    } catch (exportFailure) {
      setExportError(getApiErrorMessage(exportFailure, 'Не удалось экспортировать журнал.'));
    } finally {
      setExporting(false);
    }
  };

  const filtersActive =
    applied.action !== '' ||
    applied.entityType !== '' ||
    applied.adminId !== '' ||
    applied.entityId !== '' ||
    applied.q !== '' ||
    applied.fromDate !== '' ||
    applied.toDate !== '';

  const draftActive =
    draft.action !== '' ||
    draft.entityType !== '' ||
    draft.adminId !== '' ||
    draft.entityId !== '' ||
    draft.q !== '' ||
    draft.fromDate !== '' ||
    draft.toDate !== '';

  const records = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  const topEntries = (record?: Record<string, number> | null, limit = 6) =>
    Object.entries(record ?? {})
      .sort((a, b) => b[1] - a[1])
      .slice(0, limit);

  const actionEntries = topEntries(stats?.byAction);
  const entityEntries = topEntries(stats?.byEntityType);
  const adminEntries = topEntries(stats?.byAdmin);

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <ShieldCheck size={17} aria-hidden="true" />
        <p>
          Журнал <code>admin_audit_log</code> только дописывается. Каждое критичное действие
          администратора хранит автора, время, объект, предыдущее и новое состояние, причину и IP.
          Экспорт ограничен сервером 50 000 строк.
        </p>
      </div>

      <section className="admin-section" aria-labelledby="admin-audit-stats-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Сводка</span>
            <h2 id="admin-audit-stats-title">Статистика административных действий</h2>
            <p className="admin-section-description">
              {stats
                ? `${formatDateTime(stats.from)} — ${formatDateTime(stats.to)}`
                : 'Окно по умолчанию — последние 30 дней.'}
            </p>
          </div>
          <button
            type="button"
            className="admin-button admin-button--ghost admin-button--compact"
            onClick={() => setReloadKey((value) => value + 1)}
            disabled={statsLoading}
          >
            <RefreshCw size={15} aria-hidden="true" />
            Обновить
          </button>
        </div>

        {statsLoading ? (
          <AdminLoadingBlock rows={2} />
        ) : statsError ? (
          <div className="admin-inline-alert" role="alert">
            {statsError}
          </div>
        ) : (
          <>
            <div className="admin-stat-grid">
              <div className="admin-stat-card">
                <span>Всего записей</span>
                <strong>{formatNumber(stats?.total ?? 0)}</strong>
              </div>
              <div className="admin-stat-card">
                <span>Действий</span>
                <strong>{formatNumber(Object.keys(stats?.byAction ?? {}).length)}</strong>
              </div>
              <div className="admin-stat-card">
                <span>Типов объектов</span>
                <strong>{formatNumber(Object.keys(stats?.byEntityType ?? {}).length)}</strong>
              </div>
              <div className="admin-stat-card">
                <span>Администраторов</span>
                <strong>{formatNumber(Object.keys(stats?.byAdmin ?? {}).length)}</strong>
              </div>
            </div>

            <div className="admin-stat-breakdowns">
              <div>
                <h3>По действиям</h3>
                {actionEntries.length === 0 ? (
                  <p className="admin-muted">Нет данных за период.</p>
                ) : (
                  <ul className="admin-stat-list">
                    {actionEntries.map(([key, count]) => (
                      <li key={key}>
                        <span>{ACTION_LABELS[key] ?? key}</span>
                        <strong>{formatNumber(count)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <h3>По объектам</h3>
                {entityEntries.length === 0 ? (
                  <p className="admin-muted">Нет данных за период.</p>
                ) : (
                  <ul className="admin-stat-list">
                    {entityEntries.map(([key, count]) => (
                      <li key={key}>
                        <span>{ENTITY_LABELS[key] ?? key}</span>
                        <strong>{formatNumber(count)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <h3>По администраторам</h3>
                {adminEntries.length === 0 ? (
                  <p className="admin-muted">Нет данных за период.</p>
                ) : (
                  <ul className="admin-stat-list">
                    {adminEntries.map(([key, count]) => (
                      <li key={key}>
                        <span>ID {key}</span>
                        <strong>{formatNumber(count)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </>
        )}
      </section>

      <form className="admin-toolbar-card" onSubmit={applyFilters}>
        <div className="admin-filter-grid">
          <label className="admin-field">
            <span>Поиск</span>
            <input
              value={draft.q}
              onChange={(event) => setDraft((previous) => ({ ...previous, q: event.target.value }))}
              placeholder="Причина, действие, тип объекта"
            />
          </label>
          <label className="admin-field">
            <span>Действие</span>
            <select
              value={draft.action}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, action: event.target.value }))
              }
            >
              <option value="">Все</option>
              {ACTION_ORDER.map((action) => (
                <option key={action} value={action}>
                  {ACTION_LABELS[action]}
                </option>
              ))}
            </select>
          </label>
          <label className="admin-field">
            <span>Тип объекта</span>
            <select
              value={draft.entityType}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, entityType: event.target.value }))
              }
            >
              <option value="">Все</option>
              {ENTITY_ORDER.map((entity) => (
                <option key={entity} value={entity}>
                  {ENTITY_LABELS[entity]}
                </option>
              ))}
            </select>
          </label>
          <label className="admin-field">
            <span>ID администратора</span>
            <input
              type="number"
              min={1}
              value={draft.adminId}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, adminId: event.target.value }))
              }
              placeholder="Например, 1"
            />
          </label>
          <label className="admin-field">
            <span>ID объекта</span>
            <input
              type="number"
              min={1}
              value={draft.entityId}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, entityId: event.target.value }))
              }
              placeholder="Например, 42"
            />
          </label>
          <label className="admin-field">
            <span>С даты</span>
            <input
              type="date"
              value={draft.fromDate}
              max={draft.toDate || undefined}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, fromDate: event.target.value }))
              }
            />
          </label>
          <label className="admin-field">
            <span>По дату</span>
            <input
              type="date"
              value={draft.toDate}
              min={draft.fromDate || undefined}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, toDate: event.target.value }))
              }
            />
          </label>
        </div>

        <div className="admin-toolbar">
          <div className="admin-toolbar-filters">
            <span className="admin-toolbar-meta">
              Найдено <strong>{formatNumber(data?.totalElements ?? 0)}</strong> записей
            </span>
          </div>
          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={resetFilters}
              disabled={!filtersActive && !draftActive}
            >
              <FilterX size={15} aria-hidden="true" />
              Сбросить
            </button>
            <button
              type="button"
              className="admin-button admin-button--secondary admin-button--compact"
              onClick={handleExport}
              disabled={exporting}
            >
              <Download size={15} aria-hidden="true" />
              {exporting ? 'Экспорт…' : 'CSV'}
            </button>
            <button type="submit" className="admin-button admin-button--primary admin-button--compact">
              <Search size={15} aria-hidden="true" />
              Применить
            </button>
          </div>
        </div>
        {exportError && (
          <div className="admin-inline-alert" role="alert">
            {exportError}
          </div>
        )}
      </form>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={6} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Журнал аудита недоступен"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : records.length === 0 ? (
        <AdminEmptyState
          title="Записей нет"
          description={
            filtersActive
              ? 'По выбранным фильтрам сервер не вернул ни одной записи.'
              : 'Журнал пока пуст.'
          }
          action={
            filtersActive ? (
              <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
                <FilterX size={16} aria-hidden="true" />
                Сбросить фильтры
              </button>
            ) : undefined
          }
        />
      ) : (
        <>
          <section
            className="admin-section admin-section--flush"
            aria-labelledby="admin-audit-table-title"
          >
            <div className="admin-table-heading">
              <h2 id="admin-audit-table-title" className="admin-visually-hidden">
                Записи аудита
              </h2>
              <span className="admin-table-hint">
                Страница {data ? data.number + 1 : 1} из {Math.max(1, totalPages)}
              </span>
            </div>

            <div className="admin-table-wrap">
              <table className="admin-table admin-table--compact">
                <thead>
                  <tr>
                    <th scope="col">Время</th>
                    <th scope="col">Админ</th>
                    <th scope="col">Действие</th>
                    <th scope="col">Объект</th>
                    <th scope="col">Было → стало</th>
                    <th scope="col">Причина</th>
                    <th scope="col">IP</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((record) => (
                    <tr key={record.id}>
                      <td>{formatDateTime(record.createdAt)}</td>
                      <td>{record.adminId ?? '—'}</td>
                      <td>
                        <span className="admin-badge admin-badge--neutral">
                          {ACTION_LABELS[record.action] ?? record.action}
                        </span>
                      </td>
                      <td>
                        <div className="admin-audit-object">
                          <span>{ENTITY_LABELS[record.entityType] ?? record.entityType}</span>
                          {record.entityId != null && <code>#{record.entityId}</code>}
                        </div>
                      </td>
                      <td>
                        <div className="admin-audit-state">
                          <span>{renderState(record.beforeState)}</span>
                          <span aria-hidden="true">→</span>
                          <span>{renderState(record.afterState)}</span>
                        </div>
                      </td>
                      <td className="admin-audit-reason">{record.reason || '—'}</td>
                      <td>{record.ipAddress || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <Pagination
            page={(data?.number ?? 0) + 1}
            totalPages={Math.max(1, totalPages)}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            style={{ marginTop: '18px' }}
          />
        </>
      )}

      <p className="admin-field-hint">
        <Activity size={14} aria-hidden="true" /> Журнал доступен только для чтения: записи создаются
        сервисами в той же транзакции, что и само административное действие.
      </p>
    </div>
  );
};
