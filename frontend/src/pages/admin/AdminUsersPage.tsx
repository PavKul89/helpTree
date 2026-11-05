import React, { useDeferredValue, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
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

const PAGE_SIZE = 10;

export const AdminUsersPage: React.FC = () => {
  const { showToast } = useToast();

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [sort, setSort] = useState<SortOption>('created-desc');
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const sortParams: Record<SortOption, { sort: 'createdAt' | 'rating' | 'helpedCount' | 'name'; direction: 'asc' | 'desc' }> = {
          'created-desc': { sort: 'createdAt', direction: 'desc' },
          'rating-desc': { sort: 'rating', direction: 'desc' },
          'helped-desc': { sort: 'helpedCount', direction: 'desc' },
          'name-asc': { sort: 'name', direction: 'asc' },
        };
        const { sort: sortField, direction } = sortParams[sort];
        const data = await adminApi.getUsers({
          page,
          size: PAGE_SIZE,
          q: deferredSearch.trim() || undefined,
          role: roleFilter === 'ALL' ? undefined : roleFilter,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          sort: sortField,
          direction,
        });
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
  }, [reloadKey, page, deferredSearch, roleFilter, statusFilter, sort]);

  useEffect(() => {
    setPage(0);
  }, [deferredSearch, roleFilter, statusFilter, sort]);

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
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deleteUser(deleteTarget.id);
      setUsers((previous) => previous.filter((user) => user.id !== deleteTarget.id));
      setTotal((previous) => Math.max(0, previous - 1));
      showToast(`Пользователь «${deleteTarget.name}» удалён.`, 'success');
      setDeleteTarget(null);
      setReloadKey((value) => value + 1);
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить пользователя.'));
    } finally {
      setDeleting(false);
    }
  };

  const filtersActive =
    search.trim().length > 0 || roleFilter !== 'ALL' || statusFilter !== 'ALL' || sort !== 'created-desc';

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Users size={17} aria-hidden="true" />
        <p>
          <code>GET /api/users</code> фильтрует, сортирует и разбивает на страницы на сервере.
          Поиск <code>q</code> охватывает имя, email и город; телефон и ID серверным поиском не
          поддерживаются. Удалённые аккаунты в этот список не попадают.
        </p>
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
              <select value={sort} onChange={(event) => setSort(event.target.value as SortOption)}>
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
            Всего <strong>{formatNumber(total)}</strong>
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
          title={filtersActive ? 'Ничего не найдено' : 'Пользователей нет'}
          description={
            filtersActive
              ? 'Измените поисковый запрос или сбросьте фильтры.'
              : 'Сервер вернул пустой список активных пользователей.'
          }
          action={
            filtersActive ? (
              <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
                <FilterX size={16} aria-hidden="true" />
                Сбросить фильтры
              </button>
            ) : (
              <button
                type="button"
                className="admin-button admin-button--primary"
                onClick={() => setCreateOpen(true)}
              >
                <UserPlus size={16} aria-hidden="true" />
                Создать пользователя
              </button>
            )
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
                  <th scope="col" className="admin-col-wide">Создан</th>
                  <th scope="col">
                    <span className="admin-visually-hidden">Действия</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const blocked = isUserBlocked(user);
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
                        </div>
                      </td>
                      <td className="admin-col-optional">{formatRating(user.rating)}</td>
                      <td className="admin-col-optional">
                        {formatNumber(user.helpedCount)} / {formatNumber(user.debtCount)}
                      </td>
                      <td className="admin-col-optional">{formatNumber(user.helpCoins)}</td>
                      <td className="admin-col-optional">{user.city || '—'}</td>
                      <td className="admin-col-wide">{formatDateTime(user.createdAt)}</td>
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
                          <button
                            type="button"
                            className="admin-icon-btn admin-icon-btn--danger"
                            onClick={() => {
                              setDeleteError(null);
                              setDeleteTarget(user);
                            }}
                            aria-label={`Удалить пользователя ${user.name}`}
                            title="Удалить"
                          >
                            <Trash2 size={16} aria-hidden="true" />
                          </button>
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
          'Пользователь не сможет пользоваться аккаунтом; восстановление доступно только через административный API.',
          'Списка удалённых аккаунтов в интерфейсе нет, поэтому восстановить пользователя из этой панели нельзя.',
        ]}
        confirmText="Удалить пользователя"
        busy={deleting}
        error={deleteError}
        onConfirm={handleDelete}
        onClose={() => {
          if (!deleting) {
            setDeleteTarget(null);
            setDeleteError(null);
          }
        }}
      />
    </div>
  );
};
