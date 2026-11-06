import React, { useDeferredValue, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Archive,
  ArchiveRestore,
  Ban,
  ChevronRight,
  FilterX,
  RefreshCw,
  Search,
  Trash2,
  UserPlus,
  Users,
} from 'lucide-react';
import { adminApi, type AdminUser, type AdminUserStatus } from '../../api/adminApi';
import { AdminAvatar } from '../../components/admin/AdminAvatar';
import { AdminConfirmDialog } from '../../components/admin/AdminConfirmDialog';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { Pagination } from '../../components/Pagination';
import { useToast } from '../../components/Toast';
import { CreateUserDialog } from './CreateUserDialog';
import {
  USER_STATUS_LABELS,
  formatDateTime,
  formatNumber,
  formatRating,
  getApiErrorMessage,
  isUserBlocked,
} from './adminUtils';

type RoleFilter = 'ALL' | 'USER' | 'ADMIN';
type StatusFilter = 'ALL' | AdminUserStatus;
type SortOption = 'created-desc' | 'rating-desc' | 'helped-desc' | 'name-asc';
type Scope = 'active' | 'deleted';

const PAGE_SIZE = 10;

const SORT_PARAMS: Record<
  SortOption,
  { sort: 'createdAt' | 'rating' | 'helpedCount' | 'name'; direction: 'asc' | 'desc' }
> = {
  'created-desc': { sort: 'createdAt', direction: 'desc' },
  'rating-desc': { sort: 'rating', direction: 'desc' },
  'helped-desc': { sort: 'helpedCount', direction: 'desc' },
  'name-asc': { sort: 'name', direction: 'asc' },
};

export const AdminUsersPage: React.FC = () => {
  const { showToast } = useToast();

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [scope, setScope] = useState<Scope>('active');
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [sort, setSort] = useState<SortOption>('created-desc');
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null);
  const [deleteReason, setDeleteReason] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [restoreTarget, setRestoreTarget] = useState<AdminUser | null>(null);
  const [restoreReason, setRestoreReason] = useState('');
  const [restoring, setRestoring] = useState(false);
  const [restoreError, setRestoreError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const { sort: sortField, direction } = SORT_PARAMS[sort];
        const query = {
          page,
          size: PAGE_SIZE,
          q: deferredSearch.trim() || undefined,
          role: roleFilter === 'ALL' ? undefined : roleFilter,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          sort: scope === 'deleted' ? ('deletedAt' as const) : sortField,
          direction,
        };
        const data =
          scope === 'deleted'
            ? await adminApi.getDeletedUsers(query)
            : await adminApi.getUsers(query);
        if (cancelled) return;
        setUsers(data.content);
        setTotal(data.totalElements);
        setTotalPages(Math.max(1, data.totalPages));
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить список пользователей.'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [reloadKey, page, deferredSearch, roleFilter, statusFilter, sort, scope]);

  useEffect(() => {
    setPage(0);
  }, [deferredSearch, roleFilter, statusFilter, sort, scope]);

  useEffect(() => {
    if (page > totalPages - 1) setPage(totalPages - 1);
  }, [page, totalPages]);

  const resetFilters = () => {
    setSearch('');
    setRoleFilter('ALL');
    setStatusFilter('ALL');
    setSort('created-desc');
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting || !deleteReason.trim()) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deleteUser(deleteTarget.id, deleteReason.trim());
      setUsers((previous) => previous.filter((user) => user.id !== deleteTarget.id));
      setTotal((previous) => Math.max(0, previous - 1));
      showToast(`Пользователь «${deleteTarget.name}» удалён.`, 'success');
      setDeleteTarget(null);
      setDeleteReason('');
      setReloadKey((value) => value + 1);
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить пользователя.'));
    } finally {
      setDeleting(false);
    }
  };

  const handleRestore = async () => {
    if (!restoreTarget || restoring || !restoreReason.trim()) return;
    setRestoring(true);
    setRestoreError(null);
    try {
      await adminApi.restoreUser(restoreTarget.id, restoreReason.trim());
      setUsers((previous) => previous.filter((user) => user.id !== restoreTarget.id));
      setTotal((previous) => Math.max(0, previous - 1));
      showToast(`Пользователь «${restoreTarget.name}» восстановлен.`, 'success');
      setRestoreTarget(null);
      setRestoreReason('');
      setReloadKey((value) => value + 1);
    } catch (restoreFailure) {
      setRestoreError(getApiErrorMessage(restoreFailure, 'Не удалось восстановить пользователя.'));
    } finally {
      setRestoring(false);
    }
  };

  const filtersActive =
    search.trim().length > 0 || roleFilter !== 'ALL' || statusFilter !== 'ALL' || sort !== 'created-desc';

  const isDeletedScope = scope === 'deleted';

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Users size={17} aria-hidden="true" />
        <p>
          <code>GET /api/admin/users</code> фильтрует, сортирует и разбивает на страницы на сервере.
          Поиск <code>q</code> охватывает имя, email, телефон, город и числовой ID. Вкладка
          «Удалённые» открывает soft-deleted аккаунты; анонимизированные записи видны, но
          восстановить их уже нельзя.
        </p>
      </div>

      <div className="admin-scope-tabs" role="tablist" aria-label="Тип аккаунтов">
        <button
          type="button"
          role="tab"
          aria-selected={!isDeletedScope}
          className={`admin-scope-tab${!isDeletedScope ? ' is-active' : ''}`}
          onClick={() => setScope('active')}
        >
          <Users size={15} aria-hidden="true" />
          Активные
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={isDeletedScope}
          className={`admin-scope-tab${isDeletedScope ? ' is-active' : ''}`}
          onClick={() => setScope('deleted')}
        >
          <Archive size={15} aria-hidden="true" />
          Удалённые
        </button>
      </div>

      <div className="admin-toolbar-card">
        <div className="admin-toolbar">
          <div className="admin-search">
            <Search size={16} aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Имя, email, телефон, город или ID"
              aria-label="Поиск пользователей"
            />
          </div>

          <div className="admin-toolbar-filters">
            <label className="admin-filter">
              <span>Роль</span>
              <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value as RoleFilter)}>
                <option value="ALL">Все</option>
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </label>

            <label className="admin-filter">
              <span>Статус</span>
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
              >
                <option value="ALL">Все</option>
                {Object.entries(USER_STATUS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            <label className="admin-filter">
              <span>Сортировка</span>
              <select
                value={sort}
                onChange={(event) => setSort(event.target.value as SortOption)}
                disabled={isDeletedScope}
              >
                <option value="created-desc">Сначала новые</option>
                <option value="rating-desc">Рейтинг ↓</option>
                <option value="helped-desc">Помощь ↓</option>
                <option value="name-asc">Имя А–Я</option>
              </select>
            </label>
          </div>

          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={resetFilters}
              disabled={!filtersActive}
            >
              <FilterX size={15} aria-hidden="true" />
              Сбросить
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
            <button
              type="button"
              className="admin-button admin-button--primary admin-button--compact"
              onClick={() => setCreateOpen(true)}
            >
              <UserPlus size={15} aria-hidden="true" />
              Создать
            </button>
          </div>
        </div>

        <div className="admin-toolbar-meta">
          <span>
            {isDeletedScope ? 'Удалённых' : 'Всего'} <strong>{formatNumber(total)}</strong>
          </span>
          {users.length > 0 && (
            <span>
              Показано {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + users.length}
            </span>
          )}
        </div>
      </div>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={6} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Список пользователей недоступен"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : users.length === 0 ? (
        <AdminEmptyState
          title={
            filtersActive
              ? 'Ничего не найдено'
              : isDeletedScope
                ? 'Удалённых аккаунтов нет'
                : 'Пользователей нет'
          }
          description={
            filtersActive
              ? 'Измените поисковый запрос или сбросьте фильтры.'
              : isDeletedScope
                ? 'В корзине пусто: ни один аккаунт не был мягко удалён.'
                : 'Сервер вернул пустой список активных пользователей.'
          }
          action={
            filtersActive ? (
              <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
                <FilterX size={16} aria-hidden="true" />
                Сбросить фильтры
              </button>
            ) : !isDeletedScope ? (
              <button
                type="button"
                className="admin-button admin-button--primary"
                onClick={() => setCreateOpen(true)}
              >
                <UserPlus size={16} aria-hidden="true" />
                Создать пользователя
              </button>
            ) : undefined
          }
        />
      ) : (
        <section className="admin-section admin-section--flush" aria-labelledby="admin-users-table-title">
          <div className="admin-table-heading">
            <h2 id="admin-users-table-title" className="admin-visually-hidden">
              Пользователи
            </h2>
            <span className="admin-table-hint">Страница {page + 1} из {totalPages}</span>
          </div>

          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th scope="col">Пользователь</th>
                  <th scope="col">Роль</th>
                  <th scope="col">Статус</th>
                  <th scope="col" className="admin-col-optional">Рейтинг</th>
                  <th scope="col" className="admin-col-optional">Помощь / долг</th>
                  <th scope="col" className="admin-col-optional">HC</th>
                  <th scope="col" className="admin-col-optional">Город</th>
                  <th scope="col" className="admin-col-wide">
                    {isDeletedScope ? 'Удалён' : 'Создан'}
                  </th>
                  <th scope="col">
                    <span className="admin-visually-hidden">Действия</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const blocked = isUserBlocked(user);
                  const anonymized = Boolean(user.anonymizedAt);
                  return (
                    <tr key={user.id}>
                      <td>
                        <div className="admin-user-cell">
                          <AdminAvatar name={user.name} avatarUrl={user.avatarUrl} size="small" />
                          <div>
                            <Link to={`/admin/users/${user.id}`} className="admin-user-link">
                              {user.name}
                            </Link>
                            <span>{user.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className={`admin-badge ${user.role === 'ADMIN' ? 'admin-badge--lime' : 'admin-badge--neutral'}`}>
                          {user.role}
                        </span>
                      </td>
                      <td>
                        <div className="admin-badge-row">
                          <span className={`admin-badge admin-badge--status-${user.status.toLowerCase()}`}>
                            {USER_STATUS_LABELS[user.status] ?? user.status}
                          </span>
                          {blocked && (
                            <span className="admin-badge admin-badge--danger" title="Аккаунт заблокирован">
                              <Ban size={11} aria-hidden="true" />
                              блок
                            </span>
                          )}
                          {user.deleted && (
                            <span className="admin-badge admin-badge--danger" title="Аккаунт мягко удалён">
                              <Archive size={11} aria-hidden="true" />
                              удалён
                            </span>
                          )}
                          {anonymized && (
                            <span className="admin-badge admin-badge--neutral" title="Персональные данные анонимизированы">
                              анонимизирован
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="admin-col-optional">{formatRating(user.rating)}</td>
                      <td className="admin-col-optional">
                        {formatNumber(user.helpedCount)} / {formatNumber(user.debtCount)}
                      </td>
                      <td className="admin-col-optional">{formatNumber(user.helpCoins)}</td>
                      <td className="admin-col-optional">{user.city || '—'}</td>
                      <td className="admin-col-wide">
                        {formatDateTime(isDeletedScope ? user.deletedAt ?? user.createdAt : user.createdAt)}
                      </td>
                      <td>
                        <div className="admin-row-actions">
                          <Link
                            to={`/admin/users/${user.id}`}
                            className="admin-icon-btn"
                            aria-label={`Открыть пользователя ${user.name}`}
                            title="Открыть"
                          >
                            <ChevronRight size={17} aria-hidden="true" />
                          </Link>
                          {isDeletedScope ? (
                            <button
                              type="button"
                              className="admin-icon-btn"
                              onClick={() => {
                                setRestoreError(null);
                                setRestoreReason('');
                                setRestoreTarget(user);
                              }}
                              disabled={anonymized}
                              aria-label={`Восстановить пользователя ${user.name}`}
                              title={
                                anonymized
                                  ? 'Анонимизированный аккаунт нельзя восстановить'
                                  : 'Восстановить'
                              }
                            >
                              <ArchiveRestore size={16} aria-hidden="true" />
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="admin-icon-btn admin-icon-btn--danger"
                              onClick={() => {
                                setDeleteError(null);
                                setDeleteReason('');
                                setDeleteTarget(user);
                              }}
                              aria-label={`Удалить пользователя ${user.name}`}
                              title="Удалить"
                            >
                              <Trash2 size={16} aria-hidden="true" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
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

      <CreateUserDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => setReloadKey((value) => value + 1)}
      />

      <AdminConfirmDialog
        open={Boolean(deleteTarget)}
        title="Удалить пользователя?"
        description={
          deleteTarget
            ? `Аккаунт «${deleteTarget.name}» (${deleteTarget.email}) будет мягко удалён.`
            : undefined
        }
        consequences={[
          'Сервер выполнит soft-delete: запись останется в базе, но пользователь исчезнет из активного списка.',
          'Пользователь не сможет пользоваться аккаунтом, а все его сессии будут отозваны.',
          'Восстановить аккаунт можно на вкладке «Удалённые»; операция и причина попадут в журнал аудита.',
        ]}
        confirmText="Удалить пользователя"
        busy={deleting}
        error={deleteError}
        reason={deleteReason}
        onReasonChange={setDeleteReason}
        reasonRequired
        reasonPlaceholder="Например: спам-рассылка, подтверждённое нарушение правил"
        reasonHint="Причина обязательна и сохраняется в admin_audit_log."
        onConfirm={handleDelete}
        onClose={() => {
          if (!deleting) {
            setDeleteTarget(null);
            setDeleteReason('');
            setDeleteError(null);
          }
        }}
      />

      <AdminConfirmDialog
        open={Boolean(restoreTarget)}
        title="Восстановить пользователя?"
        description={
          restoreTarget
            ? `Аккаунт «${restoreTarget.name}» (${restoreTarget.email}) вернётся в активный список.`
            : undefined
        }
        consequences={[
          'Сервер снимет флаг soft-delete и вернёт аккаунт в активный список.',
          'Пользователь снова сможет войти, если помнит пароль или восстановит его.',
          'Операция и причина попадут в журнал аудита.',
        ]}
        confirmText="Восстановить"
        tone="default"
        busy={restoring}
        error={restoreError}
        reason={restoreReason}
        onReasonChange={setRestoreReason}
        reasonRequired
        reasonPlaceholder="Например: апелляция удовлетворена"
        reasonHint="Причина обязательна и сохраняется в admin_audit_log."
        onConfirm={handleRestore}
        onClose={() => {
          if (!restoring) {
            setRestoreTarget(null);
            setRestoreReason('');
            setRestoreError(null);
          }
        }}
      />
    </div>
  );
};
