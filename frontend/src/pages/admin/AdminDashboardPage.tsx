import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Ban,
  BarChart3,
  ClipboardList,
  Handshake,
  RefreshCw,
  ShieldCheck,
  Trophy,
  Users,
} from 'lucide-react';
import {
  adminApi,
  type AdminHelpStats,
  type AdminPostStatus,
  type AdminStats,
  type AdminUserSummary,
} from '../../api/adminApi';
import { AdminErrorState, AdminLoadingBlock } from '../../components/admin/AdminFeedback';
import {
  formatMonthKey,
  formatNumber,
  POST_STATUS_LABELS,
  POST_STATUS_ORDER,
  USER_STATUS_LABELS,
  getApiErrorMessage,
} from './adminUtils';

interface DashboardData {
  stats: AdminStats | null;
  helpStats: AdminHelpStats | null;
  statusCounts: Record<AdminPostStatus, number> | null;
  userSummary: AdminUserSummary | null;
  loadedAt: Date;
  failures: string[];
}

export const AdminDashboardPage: React.FC = () => {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const results = await Promise.allSettled([
          adminApi.getStats(),
          adminApi.getHelpStats(),
          adminApi.getPostStatusCounts(),
          adminApi.getUserSummary(),
        ]);
        if (cancelled) return;

        const [statsResult, helpStatsResult, statusCountsResult, userSummaryResult] = results;
        const successful = results.filter((result) => result.status === 'fulfilled').length;
        if (successful === 0) {
          const firstFailure = results.find(
            (result): result is PromiseRejectedResult => result.status === 'rejected'
          );
          throw firstFailure?.reason ?? new Error('Показатели недоступны.');
        }

        const failures = results
          .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
          .map((result) => getApiErrorMessage(result.reason, 'Один из источников статистики недоступен.'));
        if (!cancelled) {
          setData({
            stats: statsResult.status === 'fulfilled' ? statsResult.value : null,
            helpStats: helpStatsResult.status === 'fulfilled' ? helpStatsResult.value : null,
            statusCounts: statusCountsResult.status === 'fulfilled' ? statusCountsResult.value : null,
            userSummary: userSummaryResult.status === 'fulfilled' ? userSummaryResult.value : null
              ?? null,
            loadedAt: new Date(),
            failures,
          });
        }
      } catch (loadError) {
        if (!cancelled) {
          setData(null);
          setError(getApiErrorMessage(loadError, 'Не удалось получить показатели сервиса.'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const derived = useMemo(() => {
    if (!data) return null;

    const monthEntries = data.helpStats ? Object.entries(data.helpStats.byMonth) : [];
    const maxMonth = Math.max(0, ...monthEntries.map(([, value]) => value));

    const categoryEntries = data.helpStats
      ? Object.entries(data.helpStats.byCategory).sort((a, b) => b[1] - a[1])
      : [];
    const maxCategory = Math.max(0, ...categoryEntries.map(([, value]) => value));

    const statusCounts: Record<string, number> = {
      NEWBIE: 0,
      HELPER: 0,
      ACTIVE: 0,
      DEBTOR: 0,
    };
    if (data.userSummary) {
      Object.assign(statusCounts, data.userSummary.statusCounts);
    }

    return {
      monthEntries,
      maxMonth,
      categoryEntries,
      maxCategory,
      statusCounts,
      admins: data.userSummary?.admins ?? null,
      blocked: data.userSummary?.blocked ?? null,
      totalUsers: data.userSummary?.total ?? null,
    };
  }, [data]);

  if (loading) {
    return (
      <div className="admin-stack">
        <div className="admin-notice">
          <BarChart3 size={17} aria-hidden="true" />
          <p>Загружаем показатели из существующих API статистики…</p>
        </div>
        <AdminLoadingBlock rows={3} />
        <AdminLoadingBlock rows={4} />
      </div>
    );
  }

  if (error || !data || !derived) {
    return (
      <AdminErrorState
        title="Показатели недоступны"
        message={error ?? 'Не удалось получить данные.'}
        onRetry={() => setReloadKey((value) => value + 1)}
      />
    );
  }

  const kpis = [
    {
      label: 'Пользователи',
      value: formatNumber(data.stats?.totalUsers),
      hint: 'Неудалённые профили · GET /api/stats',
      icon: Users,
    },
    {
      label: 'Подтверждённые акты',
      value: formatNumber(data.stats?.totalHelps),
      hint: 'CONFIRMED помощи — та же база, что у графа и достижений',
      icon: Handshake,
    },
    {
      label: 'Записи о помощи',
      value: formatNumber(data.stats?.helpRecords),
      hint: 'Все неудалённые записи, независимо от статуса',
      icon: Handshake,
    },
    {
      label: 'Активные / отменённые',
      value: `${formatNumber(data.stats?.activeHelps)} / ${formatNumber(data.stats?.cancelledHelps)}`,
      hint: 'ACCEPTED + COMPLETED / CANCELLED',
      icon: Handshake,
    },
    {
      label: 'Открытые заявки',
      value: formatNumber(data.stats?.activePosts),
      hint: 'GET /api/stats · activePosts',
      icon: ClipboardList,
    },
    {
      label: 'Администраторы',
      value: data.userSummary ? formatNumber(derived.admins) : '—',
      hint: 'Роль ADMIN · total из GET /api/users',
      icon: ShieldCheck,
    },
  ];
  const postStatusCounts = data.statusCounts;
  const totalListedUsers = derived.totalUsers ?? 0;

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <BarChart3 size={17} aria-hidden="true" />
        <p>
          Только реальные данные: <code>GET /api/stats</code>,{' '}
          <code>GET /api/helps/stats</code> и счётчики статусов из <code>GET /api/posts</code>.
          Показатели без серверного источника здесь не отображаются.
        </p>
        <span className="admin-notice-meta">
          Обновлено{' '}
          {data.loadedAt.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
      {data.failures.length > 0 && (
        <p className="admin-inline-alert" role="status">
          Часть статистики временно недоступна; остальные показатели загружены.
        </p>
      )}

      <section className="admin-section" aria-labelledby="admin-kpi-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Сводка</span>
            <h2 id="admin-kpi-title">Ключевые показатели</h2>
          </div>
          <button
            type="button"
            className="admin-button admin-button--ghost admin-button--compact"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            <RefreshCw size={15} aria-hidden="true" />
            Обновить
          </button>
        </div>

        <div className="admin-kpi-grid">
          {kpis.map((kpi) => {
            const Icon = kpi.icon;
            return (
              <article key={kpi.label} className="admin-kpi">
                <span className="admin-kpi-icon" aria-hidden="true">
                  <Icon size={19} />
                </span>
                <div>
                  <span className="admin-kpi-label">{kpi.label}</span>
                  <strong>{kpi.value}</strong>
                  <small>{kpi.hint}</small>
                </div>
              </article>
            );
          })}
        </div>
      </section>

      <section className="admin-section" aria-labelledby="admin-post-status-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Заявки</span>
            <h2 id="admin-post-status-title">Заявки по статусам</h2>
            <p className="admin-section-description">
              Количество получено через <code>totalElements</code> списка заявок; удалённые
              публикации backend в список не включает.
            </p>
          </div>
        </div>

        <div className="admin-status-grid">
          {postStatusCounts ? POST_STATUS_ORDER.map((status) => (
            <Link
              key={status}
              to={`/admin/requests?status=${status}`}
              className={`admin-status-card admin-status-card--${status.toLowerCase()}`}
            >
              <span>{POST_STATUS_LABELS[status]}</span>
              <strong>{formatNumber(postStatusCounts[status])}</strong>
              <small>Открыть список →</small>
            </Link>
          )) : <p className="admin-muted" role="status">Распределение статусов недоступно.</p>}
        </div>
      </section>

      <div className="admin-split">
        <section className="admin-section" aria-labelledby="admin-month-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Динамика</span>
              <h2 id="admin-month-title">Помощи по месяцам</h2>
              <p className="admin-section-description">
                12 месяцев из <code>GET /api/helps/stats</code>.
              </p>
            </div>
          </div>

          {!data.helpStats ? (
            <p className="admin-muted" role="status">Сводка по помощи недоступна.</p>
          ) : derived.monthEntries.length === 0 ? (
            <p className="admin-muted">Сервер не вернул временной ряд.</p>
          ) : (
            <ul className="admin-bars" aria-label="Подтверждённые помощи по месяцам">
              {derived.monthEntries.map(([key, value]) => {
                const width = derived.maxMonth > 0 ? Math.max(2, (value / derived.maxMonth) * 100) : 0;
                return (
                  <li key={key} className="admin-bar-row">
                    <span className="admin-bar-label">{formatMonthKey(key)}</span>
                    <span className="admin-bar-track" aria-hidden="true">
                      <span className="admin-bar-fill" style={{ width: `${width}%` }} />
                    </span>
                    <span className="admin-bar-value">{formatNumber(value)}</span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="admin-section" aria-labelledby="admin-category-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Категории</span>
              <h2 id="admin-category-title">Помощи по категориям</h2>
              <p className="admin-section-description">Агрегация из того же stats-эндпоинта.</p>
            </div>
          </div>

          {!data.helpStats ? (
            <p className="admin-muted" role="status">Сводка по категориям недоступна.</p>
          ) : derived.categoryEntries.length === 0 ? (
            <p className="admin-muted">Подтверждённых помощей по категориям пока нет.</p>
          ) : (
            <ul className="admin-bars admin-bars--category" aria-label="Помощи по категориям">
              {derived.categoryEntries.map(([category, value]) => {
                const width =
                  derived.maxCategory > 0 ? Math.max(2, (value / derived.maxCategory) * 100) : 0;
                return (
                  <li key={category} className="admin-bar-row">
                    <span className="admin-bar-label admin-bar-label--wide">{category}</span>
                    <span className="admin-bar-track" aria-hidden="true">
                      <span className="admin-bar-fill" style={{ width: `${width}%` }} />
                    </span>
                    <span className="admin-bar-value">{formatNumber(value)}</span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>

      <div className="admin-split">
        <section className="admin-section" aria-labelledby="admin-top-helpers-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Сообщество</span>
              <h2 id="admin-top-helpers-title">Топ помогающих</h2>
              <p className="admin-section-description">
                До 10 пользователей из <code>GET /api/helps/stats</code>.
              </p>
            </div>
            <Trophy size={20} className="admin-section-mark" aria-hidden="true" />
          </div>

          {!data.helpStats ? (
            <p className="admin-muted" role="status">Сводка по помощникам недоступна.</p>
          ) : data.helpStats.topHelpers.length === 0 ? (
            <p className="admin-muted">Пока нет подтверждённых помощей.</p>
          ) : (
            <ol className="admin-top-list">
              {data.helpStats.topHelpers.map((helper, index) => (
                <li key={helper.userId}>
                  <span className="admin-top-rank">{index + 1}</span>
                  <span className="admin-top-name">
                    <Link to={`/admin/users/${helper.userId}`}>
                      {helper.name || `Пользователь #${helper.userId}`}
                    </Link>
                  </span>
                  <span className="admin-top-value">
                    {formatNumber(helper.helpCount)} <small>помощей</small>
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>

        <section className="admin-section" aria-labelledby="admin-user-status-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Пользователи</span>
              <h2 id="admin-user-status-title">Репутационные статусы</h2>
              <p className="admin-section-description">
                Серверные счётчики по <code>status</code>, <code>role</code> и <code>blocked</code>
                {' '}из <code>GET /api/users</code>; полный список пользователей не выгружается.
              </p>
            </div>
          </div>

          {data.userSummary ? <ul className="admin-distribution">
            {Object.entries(USER_STATUS_LABELS).map(([status, label]) => {
              const count = derived.statusCounts[status] ?? 0;
              const percent =
                totalListedUsers > 0 ? Math.round((count / totalListedUsers) * 100) : 0;
              return (
                <li key={status}>
                  <span className="admin-distribution-label">{label}</span>
                  <span className="admin-distribution-track" aria-hidden="true">
                    <span
                      className={`admin-distribution-fill admin-distribution-fill--${status.toLowerCase()}`}
                      style={{ width: `${percent}%` }}
                    />
                  </span>
                  <span className="admin-distribution-value">
                    {formatNumber(count)} <small>{percent}%</small>
                  </span>
                </li>
              );
            })}
          </ul> : <p className="admin-muted" role="status">Распределение пользователей недоступно.</p>}

          {data.userSummary && <div className="admin-mini-stats">
            <span>
              <Users size={15} aria-hidden="true" />
              Всего аккаунтов: <strong>{formatNumber(derived.totalUsers)}</strong>
            </span>
            <span>
              <Ban size={15} aria-hidden="true" />
              Заблокированы: <strong>{formatNumber(derived.blocked)}</strong>
            </span>
          </div>}
        </section>
      </div>
    </div>
  );
};
