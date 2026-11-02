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
  type AdminUser,
} from '../../api/adminApi';
import { AdminErrorState, AdminLoadingBlock } from '../../components/admin/AdminFeedback';
import {
  formatMonthKey,
  formatNumber,
  isUserBlocked,
  POST_STATUS_LABELS,
  POST_STATUS_ORDER,
  USER_STATUS_LABELS,
  getApiErrorMessage,
} from './adminUtils';

interface DashboardData {
  stats: AdminStats;
  helpStats: AdminHelpStats;
  statusCounts: Record<AdminPostStatus, number>;
  users: AdminUser[];
  loadedAt: Date;
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
        const [stats, helpStats, statusCounts, users] = await Promise.all([
          adminApi.getStats(),
          adminApi.getHelpStats(),
          adminApi.getPostStatusCounts(),
          adminApi.getUsers(),
        ]);
        if (!cancelled) {
          setData({ stats, helpStats, statusCounts, users, loadedAt: new Date() });
        }
      } catch (loadError) {
        if (!cancelled) {
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

    const monthEntries = Object.entries(data.helpStats.byMonth);
    const maxMonth = Math.max(0, ...monthEntries.map(([, value]) => value));

    const categoryEntries = Object.entries(data.helpStats.byCategory).sort((a, b) => b[1] - a[1]);
    const maxCategory = Math.max(0, ...categoryEntries.map(([, value]) => value));

    const statusCounts: Record<string, number> = {
      NEWBIE: 0,
      HELPER: 0,
      ACTIVE: 0,
      DEBTOR: 0,
    };
    let admins = 0;
    let blocked = 0;

    data.users.forEach((user) => {
      statusCounts[user.status] = (statusCounts[user.status] ?? 0) + 1;
      if (user.role === 'ADMIN') admins += 1;
      if (isUserBlocked(user)) blocked += 1;
    });

    return {
      monthEntries,
      maxMonth,
      categoryEntries,
      maxCategory,
      statusCounts,
      admins,
      blocked,
      totalUsers: data.users.length,
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
      label: 'Активные пользователи',
      value: formatNumber(data.stats.totalUsers),
      hint: 'GET /api/stats · totalUsers',
      icon: Users,
    },
    {
      label: 'Подтверждённые помощи',
      value: formatNumber(data.stats.totalHelps),
      hint: 'GET /api/stats · totalHelps',
      icon: Handshake,
    },
    {
      label: 'Открытые заявки',
      value: formatNumber(data.stats.activePosts),
      hint: 'GET /api/stats · activePosts',
      icon: ClipboardList,
    },
    {
      label: 'Администраторы',
      value: formatNumber(derived.admins),
      hint: 'Роль ADMIN в GET /api/users',
      icon: ShieldCheck,
    },
  ];

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
            <h2 id="admin-post-status-title">Публикации по статусам</h2>
            <p className="admin-section-description">
              Количество получено через <code>totalElements</code> списка заявок; удалённые
              публикации backend в список не включает.
            </p>
          </div>
        </div>

        <div className="admin-status-grid">
          {POST_STATUS_ORDER.map((status) => (
            <Link
              key={status}
              to={`/admin/requests?status=${status}`}
              className={`admin-status-card admin-status-card--${status.toLowerCase()}`}
            >
              <span>{POST_STATUS_LABELS[status]}</span>
              <strong>{formatNumber(data.statusCounts[status])}</strong>
              <small>Открыть список →</small>
            </Link>
          ))}
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

          {derived.monthEntries.length === 0 ? (
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

          {derived.categoryEntries.length === 0 ? (
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

          {data.helpStats.topHelpers.length === 0 ? (
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
                Рассчитано на клиенте по полю <code>status</code> из <code>GET /api/users</code>.
              </p>
            </div>
          </div>

          <ul className="admin-distribution">
            {Object.entries(USER_STATUS_LABELS).map(([status, label]) => {
              const count = derived.statusCounts[status] ?? 0;
              const percent =
                derived.totalUsers > 0 ? Math.round((count / derived.totalUsers) * 100) : 0;
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
          </ul>

          <div className="admin-mini-stats">
            <span>
              <Users size={15} aria-hidden="true" />
              Всего активных: <strong>{formatNumber(derived.totalUsers)}</strong>
            </span>
            <span>
              <Ban size={15} aria-hidden="true" />
              Заблокированы: <strong>{formatNumber(derived.blocked)}</strong>
            </span>
          </div>
        </section>
      </div>
    </div>
  );
};
