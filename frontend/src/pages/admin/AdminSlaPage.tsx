import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlarmClock,
  Calendar,
  CheckCircle2,
  ClipboardList,
  FilterX,
  Flag,
  Inbox,
  RefreshCw,
  Timer,
  XCircle,
} from 'lucide-react';
import { adminApi, type ModerationSla } from '../../api/adminApi';
import {
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { formatDateTime, formatNumber, getApiErrorMessage } from './adminUtils';

const PRIORITY_LABELS: Record<string, string> = {
  LOW: 'Низкий',
  NORMAL: 'Обычный',
  HIGH: 'Высокий',
  URGENT: 'Срочный',
};

const toLocalDateTime = (value: string, endOfDay: boolean): string | undefined => {
  if (!value) return undefined;
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`;
};

const formatHours = (value?: number | null): string => {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—';
  if (value < 24) return `${value.toFixed(1)} ч`;
  return `${(value / 24).toFixed(1)} дн`;
};

export const AdminSlaPage: React.FC = () => {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  const [data, setData] = useState<ModerationSla | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError(null);

    adminApi
      .getModerationSla(
        toLocalDateTime(fromDate, false),
        toLocalDateTime(toDate, true)
      )
      .then((response) => {
        if (!cancelled) setData(response);
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить SLA модерации.'));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey, fromDate, toDate]);

  const resetPeriod = () => {
    setFromDate('');
    setToDate('');
  };

  const priorityEntries = Object.entries(data?.openByPriority ?? {});
  const totalOpen = (data?.openReports ?? 0) + (data?.inReviewReports ?? 0);

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Timer size={17} aria-hidden="true" />
        <p>
          Дашборд SLA считается на сервере из <code>reports</code> и{' '}
          <code>report_events</code>: среднее время первого ответа и разрешения, возраст старейшей
          открытой жалобы и распределение очереди по приоритетам.
        </p>
      </div>

      <div className="admin-toolbar-card">
        <div className="admin-toolbar">
          <div className="admin-toolbar-filters">
            <label className="admin-filter">
              <span>С даты</span>
              <input
                type="date"
                value={fromDate}
                max={toDate || undefined}
                onChange={(event) => setFromDate(event.target.value)}
              />
            </label>
            <label className="admin-filter">
              <span>По дату</span>
              <input
                type="date"
                value={toDate}
                min={fromDate || undefined}
                onChange={(event) => setToDate(event.target.value)}
              />
            </label>
          </div>
          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={resetPeriod}
              disabled={!fromDate && !toDate}
            >
              <FilterX size={15} aria-hidden="true" />
              Сбросить период
            </button>
            <button
              type="button"
              className="admin-button admin-button--secondary admin-button--compact"
              onClick={() => setReloadKey((value) => value + 1)}
              disabled={loading}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Обновить
            </button>
            <Link
              to="/admin/reports"
              className="admin-button admin-button--primary admin-button--compact"
            >
              <Flag size={15} aria-hidden="true" />
              Очередь жалоб
            </Link>
          </div>
        </div>
        {data && (
          <div className="admin-toolbar-meta">
            <span>
              Период: {formatDateTime(data.from)} — {formatDateTime(data.to)}
            </span>
            <span>
              Открытых сейчас: <strong>{formatNumber(totalOpen)}</strong>
            </span>
          </div>
        )}
      </div>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={5} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="SLA недоступен"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : !data ? (
        <AdminErrorState title="Нет данных" message="Сервер не вернул SLA." />
      ) : (
        <>
          <div className="admin-kpi-grid">
            <div className="admin-kpi">
              <span className="admin-kpi-icon">
                <Inbox size={18} aria-hidden="true" />
              </span>
              <div>
                <span className="admin-kpi-label">Открытые</span>
                <strong>{formatNumber(data.openReports)}</strong>
                <small>Ожидают первого ответа</small>
              </div>
            </div>
            <div className="admin-kpi">
              <span className="admin-kpi-icon">
                <AlarmClock size={18} aria-hidden="true" />
              </span>
              <div>
                <span className="admin-kpi-label">В работе</span>
                <strong>{formatNumber(data.inReviewReports)}</strong>
                <small>Взяты модератором</small>
              </div>
            </div>
            <div className="admin-kpi">
              <span className="admin-kpi-icon">
                <CheckCircle2 size={18} aria-hidden="true" />
              </span>
              <div>
                <span className="admin-kpi-label">Решено</span>
                <strong>{formatNumber(data.resolvedReports)}</strong>
                <small>Закрыты с решением</small>
              </div>
            </div>
            <div className="admin-kpi">
              <span className="admin-kpi-icon">
                <XCircle size={18} aria-hidden="true" />
              </span>
              <div>
                <span className="admin-kpi-label">Отклонено</span>
                <strong>{formatNumber(data.rejectedReports)}</strong>
                <small>Без нарушения</small>
              </div>
            </div>
          </div>

          <section className="admin-section" aria-labelledby="admin-sla-timing-title">
            <div className="admin-section-head">
              <div>
                <span className="admin-eyebrow">Скорость реакции</span>
                <h2 id="admin-sla-timing-title">Время обработки</h2>
              </div>
              <Timer size={20} className="admin-section-mark" aria-hidden="true" />
            </div>

            <dl className="admin-detail-list">
              <div>
                <dt>Среднее время первого ответа</dt>
                <dd>{formatHours(data.averageFirstResponseHours)}</dd>
              </div>
              <div>
                <dt>Среднее время разрешения</dt>
                <dd>{formatHours(data.averageResolutionHours)}</dd>
              </div>
              <div>
                <dt>Старейшая открытая жалоба</dt>
                <dd>
                  {data.oldestOpenCreatedAt ? (
                    <>
                      {formatDateTime(data.oldestOpenCreatedAt)} ·{' '}
                      {formatHours(data.oldestOpenAgeHours)}
                    </>
                  ) : (
                    '—'
                  )}
                </dd>
              </div>
              <div>
                <dt>Всего жалоб за период</dt>
                <dd>{formatNumber(data.totalReports)}</dd>
              </div>
            </dl>
          </section>

          <section className="admin-section" aria-labelledby="admin-sla-priority-title">
            <div className="admin-section-head">
              <div>
                <span className="admin-eyebrow">Очередь</span>
                <h2 id="admin-sla-priority-title">Открытые по приоритету</h2>
                <p className="admin-section-description">
                  Считаются только жалобы в статусах <code>OPEN</code> и <code>IN_REVIEW</code>.
                </p>
              </div>
              <ClipboardList size={20} className="admin-section-mark" aria-hidden="true" />
            </div>

            {priorityEntries.length === 0 ? (
              <p className="admin-muted">Открытых жалоб нет.</p>
            ) : (
              <div className="admin-status-grid">
                {priorityEntries.map(([priority, count]) => (
                  <div key={priority} className="admin-status-card">
                    <span className="admin-muted">
                      {PRIORITY_LABELS[priority] ?? priority}
                    </span>
                    <strong>{formatNumber(count)}</strong>
                  </div>
                ))}
              </div>
            )}
          </section>

          <p className="admin-field-hint">
            <Calendar size={14} aria-hidden="true" /> Без периода сервер считает SLA по всему
            журналу жалоб. Выбор дат ограничивает выборку по времени создания жалобы.
          </p>
        </>
      )}
    </div>
  );
};
