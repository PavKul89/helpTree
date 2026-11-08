import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Archive,
  ArchiveRestore,
  Calendar,
  ClipboardList,
  ExternalLink,
  FilterX,
  Handshake,
  MapPin,
  RefreshCw,
  Search,
  Trash2,
  User,
} from 'lucide-react';
import { adminApi, type AdminPostStatus } from '../../api/adminApi';
import type { Post } from '../../types';
import type { PageResponse } from '../../api/walletApi';
import { AdminConfirmDialog } from '../../components/admin/AdminConfirmDialog';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { Pagination } from '../../components/Pagination';
import { useToast } from '../../components/Toast';
import {
  POST_STATUS_LABELS,
  POST_STATUS_ORDER,
  formatDateTime,
  formatNumber,
  getApiErrorMessage,
} from './adminUtils';

type StatusFilter = 'ALL' | AdminPostStatus;
type Scope = 'active' | 'deleted';
type SortOption = 'createdAt,desc' | 'createdAt,asc' | 'title,asc' | 'title,desc';

interface AppliedFilters {
  q: string;
  authorName: string;
  category: string;
  city: string;
}

const EMPTY_FILTERS: AppliedFilters = {
  q: '',
  authorName: '',
  category: '',
  city: '',
};

const readInitialStatus = (value: string | null): StatusFilter => {
  if (value && (POST_STATUS_ORDER as string[]).includes(value)) {
    return value as AdminPostStatus;
  }
  return 'ALL';
};

export const AdminRequestsPage: React.FC = () => {
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [scope, setScope] = useState<Scope>('active');
  const [status, setStatus] = useState<StatusFilter>(() => readInitialStatus(searchParams.get('status')));
  const [draft, setDraft] = useState<AppliedFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<AppliedFilters>(EMPTY_FILTERS);
  const [sort, setSort] = useState<SortOption>('createdAt,desc');
  const [size, setSize] = useState(20);
  const [page, setPage] = useState(0);

  const [data, setData] = useState<PageResponse<Post> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [selected, setSelected] = useState<number[]>([]);

  const [deleteTarget, setDeleteTarget] = useState<Post | null>(null);
  const [deleteReason, setDeleteReason] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [restoreTarget, setRestoreTarget] = useState<Post | null>(null);
  const [restoreReason, setRestoreReason] = useState('');
  const [restoring, setRestoring] = useState(false);
  const [restoreError, setRestoreError] = useState<string | null>(null);

  const [statusTarget, setStatusTarget] = useState<Post | null>(null);
  const [statusValue, setStatusValue] = useState<AdminPostStatus>('OPEN');
  const [statusReason, setStatusReason] = useState('');
  const [statusBusy, setStatusBusy] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);

  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkReason, setBulkReason] = useState('');
  const [bulkBusy, setBulkBusy] = useState(false);
  const [bulkError, setBulkError] = useState<string | null>(null);

  useEffect(() => {
    const value = readInitialStatus(searchParams.get('status'));
    setStatus((previous) => (previous === value ? previous : value));
    setPage(0);
    // Deliberately reacts to the status query parameter only.
  }, [searchParams]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        if (scope === 'deleted') {
          const response = await adminApi.getDeletedPosts(
            {
              page,
              size,
              q: applied.q.trim() || undefined,
              category: applied.category.trim() || undefined,
              sort: 'deletedAt',
              direction: 'desc',
            },
            controller.signal
          );
          if (!cancelled) setData(response);
          return;
        }

        const response = await adminApi.getPosts(
          {
            page,
            size,
            status: status === 'ALL' ? undefined : status,
            title: applied.q.trim() || undefined,
            authorName: applied.authorName.trim() || undefined,
            category: applied.category.trim() || undefined,
            city: applied.city.trim() || undefined,
            sort,
          },
          controller.signal
        );
        if (!cancelled) setData(response);
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Не удалось загрузить список заявок.'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [page, size, status, applied, sort, reloadKey, scope]);

  useEffect(() => {
    setSelected([]);
  }, [scope, page, status, applied, sort, size]);

  const updateStatus = (value: StatusFilter) => {
    setStatus(value);
    setPage(0);
    const next = new URLSearchParams(searchParams);
    if (value === 'ALL') next.delete('status');
    else next.set('status', value);
    setSearchParams(next, { replace: true });
  };

  const changeScope = (nextScope: Scope) => {
    setScope(nextScope);
    setPage(0);
    setSelected([]);
  };

  const applyFilters = (event?: React.FormEvent) => {
    event?.preventDefault();
    setApplied({ ...draft });
    setPage(0);
  };

  const resetFilters = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setSort('createdAt,desc');
    setSize(20);
    setPage(0);
    updateStatus('ALL');
  };

  const reload = () => setReloadKey((value) => value + 1);

  const afterMutation = () => {
    const wasLastOnPage = Boolean(data && data.content.length === 1);
    if (page > 0 && wasLastOnPage) {
      setPage(page - 1);
    } else {
      reload();
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting || !deleteReason.trim()) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deletePost(deleteTarget.id, deleteReason.trim());
      showToast(`Заявка «${deleteTarget.title}» удалена.`, 'success');
      setDeleteTarget(null);
      setDeleteReason('');
      afterMutation();
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить заявку.'));
    } finally {
      setDeleting(false);
    }
  };

  const handleRestore = async () => {
    if (!restoreTarget || restoring || !restoreReason.trim()) return;
    setRestoring(true);
    setRestoreError(null);
    try {
      await adminApi.restorePost(restoreTarget.id, restoreReason.trim());
      showToast(`Заявка «${restoreTarget.title}» восстановлена.`, 'success');
      setRestoreTarget(null);
      setRestoreReason('');
      afterMutation();
    } catch (restoreFailure) {
      setRestoreError(getApiErrorMessage(restoreFailure, 'Не удалось восстановить заявку.'));
    } finally {
      setRestoring(false);
    }
  };

  const handleStatusChange = async () => {
    if (!statusTarget || statusBusy || !statusReason.trim()) return;
    setStatusBusy(true);
    setStatusError(null);
    try {
      await adminApi.changePostStatus(statusTarget.id, statusValue, statusReason.trim());
      showToast(`Статус заявки изменён на «${POST_STATUS_LABELS[statusValue]}».`, 'success');
      setStatusTarget(null);
      setStatusReason('');
      reload();
    } catch (statusFailure) {
      setStatusError(getApiErrorMessage(statusFailure, 'Не удалось изменить статус заявки.'));
    } finally {
      setStatusBusy(false);
    }
  };

  const handleBulk = async () => {
    if (bulkBusy || !bulkReason.trim() || selected.length === 0) return;
    setBulkBusy(true);
    setBulkError(null);
    try {
      const result = await adminApi.bulkPosts({
        action: scope === 'deleted' ? 'RESTORE' : 'DELETE',
        ids: selected,
        reason: bulkReason.trim(),
      });
      const label = scope === 'deleted' ? 'восстановлено' : 'удалено';
      showToast(
        `Массовая операция: ${result.succeeded} из ${result.requested} ${label}.` +
          (result.failed > 0 ? ` Ошибок: ${result.failed}.` : ''),
        result.failed > 0 ? 'info' : 'success'
      );
      setBulkOpen(false);
      setBulkReason('');
      setSelected([]);
      reload();
    } catch (bulkFailure) {
      setBulkError(getApiErrorMessage(bulkFailure, 'Не удалось выполнить массовую операцию.'));
    } finally {
      setBulkBusy(false);
    }
  };

  const toggleSelected = (id: number) => {
    setSelected((previous) =>
      previous.includes(id) ? previous.filter((value) => value !== id) : [...previous, id]
    );
  };

  const allSelected = Boolean(data && data.content.length > 0 && selected.length === data.content.length);

  const toggleAll = () => {
    if (!data) return;
    setSelected(allSelected ? [] : data.content.map((post) => post.id));
  };

  const filtersActive =
    applied.q !== '' ||
    applied.authorName !== '' ||
    applied.category !== '' ||
    applied.city !== '' ||
    status !== 'ALL' ||
    sort !== 'createdAt,desc' ||
    size !== 20;

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <ClipboardList size={17} aria-hidden="true" />
        <p>
          Активные заявки читаются через <code>GET /api/posts</code>, удалённые — через{' '}
          <code>GET /api/admin/posts/deleted</code>. Удаление, восстановление и смена статуса
          требуют причины и пишутся в <code>admin_audit_log</code>.
        </p>
      </div>

      <div className="admin-scope-tabs" role="tablist" aria-label="Тип заявок">
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'active'}
          className={`admin-scope-tab${scope === 'active' ? ' is-active' : ''}`}
          onClick={() => changeScope('active')}
        >
          <ClipboardList size={15} aria-hidden="true" />
          Активные
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'deleted'}
          className={`admin-scope-tab${scope === 'deleted' ? ' is-active' : ''}`}
          onClick={() => changeScope('deleted')}
        >
          <Archive size={15} aria-hidden="true" />
          Удалённые
        </button>
      </div>

      <form className="admin-toolbar-card" onSubmit={applyFilters}>
        <div className="admin-filter-grid">
          <label className="admin-field">
            <span>{scope === 'deleted' ? 'Поиск' : 'Название'}</span>
            <input
              value={draft.q}
              onChange={(event) => setDraft((previous) => ({ ...previous, q: event.target.value }))}
              placeholder={
                scope === 'deleted'
                  ? 'Название, описание или автор'
                  : 'Поиск по названию'
              }
            />
          </label>
          {scope === 'active' && (
            <label className="admin-field">
              <span>Автор</span>
              <input
                value={draft.authorName}
                onChange={(event) =>
                  setDraft((previous) => ({ ...previous, authorName: event.target.value }))
                }
                placeholder="Имя автора"
              />
            </label>
          )}
          <label className="admin-field">
            <span>Категория</span>
            <input
              value={draft.category}
              onChange={(event) =>
                setDraft((previous) => ({ ...previous, category: event.target.value }))
              }
              placeholder="Например, ремонт"
            />
          </label>
          {scope === 'active' && (
            <label className="admin-field">
              <span>Город</span>
              <input
                value={draft.city}
                onChange={(event) => setDraft((previous) => ({ ...previous, city: event.target.value }))}
                placeholder="Город автора"
              />
            </label>
          )}
        </div>

        <div className="admin-toolbar">
          <div className="admin-toolbar-filters">
            {scope === 'active' && (
              <label className="admin-filter">
                <span>Статус</span>
                <select value={status} onChange={(event) => updateStatus(event.target.value as StatusFilter)}>
                  <option value="ALL">Все</option>
                  {POST_STATUS_ORDER.map((value) => (
                    <option key={value} value={value}>
                      {POST_STATUS_LABELS[value]}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {scope === 'active' && (
              <label className="admin-filter">
                <span>Сортировка</span>
                <select value={sort} onChange={(event) => setSort(event.target.value as SortOption)}>
                  <option value="createdAt,desc">Сначала новые</option>
                  <option value="createdAt,asc">Сначала старые</option>
                  <option value="title,asc">Название А–Я</option>
                  <option value="title,desc">Название Я–А</option>
                </select>
              </label>
            )}

            <label className="admin-filter">
              <span>На странице</span>
              <select
                value={size}
                onChange={(event) => {
                  setSize(Number(event.target.value));
                  setPage(0);
                }}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
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
              onClick={reload}
              disabled={loading}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Обновить
            </button>
            <button type="submit" className="admin-button admin-button--primary admin-button--compact">
              <Search size={15} aria-hidden="true" />
              Применить
            </button>
          </div>
        </div>

        <div className="admin-toolbar-meta">
          <span>
            {scope === 'deleted' ? 'Удалённых' : 'Найдено'}{' '}
            <strong>{formatNumber(data?.totalElements ?? 0)}</strong> заявок
          </span>
          {data && (
            <span>
              Страница {data.totalPages === 0 ? 0 : data.number + 1} из {data.totalPages}
            </span>
          )}
        </div>
      </form>

      {selected.length > 0 && (
        <div className="admin-toolbar-card admin-bulk-bar">
          <span>
            Выбрано <strong>{selected.length}</strong>
          </span>
          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={() => setSelected([])}
            >
              Снять выбор
            </button>
            <button
              type="button"
              className={`admin-button admin-button--compact ${
                scope === 'deleted' ? 'admin-button--primary' : 'admin-button--danger'
              }`}
              onClick={() => {
                setBulkError(null);
                setBulkReason('');
                setBulkOpen(true);
              }}
            >
              {scope === 'deleted' ? (
                <ArchiveRestore size={15} aria-hidden="true" />
              ) : (
                <Trash2 size={15} aria-hidden="true" />
              )}
              {scope === 'deleted' ? 'Восстановить выбранные' : 'Удалить выбранные'}
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={6} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Заявки недоступны"
          message={error}
          onRetry={reload}
        />
      ) : !data || data.content.length === 0 ? (
        <AdminEmptyState
          title={scope === 'deleted' ? 'Удалённых заявок нет' : 'Заявки не найдены'}
          description={
            filtersActive
              ? 'По выбранным фильтрам сервер вернул пустой список. Сбросьте фильтры и попробуйте снова.'
              : scope === 'deleted'
                ? 'Ни одна заявка не была мягко удалена.'
                : 'Сервер вернул пустой список публикаций.'
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
          <div className="admin-bulk-select-all">
            <label>
              <input type="checkbox" checked={allSelected} onChange={toggleAll} />
              Выбрать все на странице
            </label>
          </div>

          <div className="admin-request-grid">
            {data.content.map((post) => (
              <article key={post.id} className="admin-request-card">
                <div className="admin-request-head">
                  <label className="admin-bulk-checkbox">
                    <input
                      type="checkbox"
                      checked={selected.includes(post.id)}
                      onChange={() => toggleSelected(post.id)}
                      aria-label={`Выбрать заявку ${post.title}`}
                    />
                  </label>
                  <span className={`admin-badge admin-badge--post-${post.status.toLowerCase()}`}>
                    {POST_STATUS_LABELS[post.status]}
                  </span>
                  <div className="admin-request-actions">
                    <Link
                      to={`/admin/users/${post.userId}`}
                      className="admin-icon-btn"
                      aria-label={`Профиль автора ${post.authorName}`}
                      title="Профиль автора"
                    >
                      <User size={16} aria-hidden="true" />
                    </Link>
                    {scope === 'deleted' ? (
                      <button
                        type="button"
                        className="admin-icon-btn"
                        onClick={() => {
                          setRestoreError(null);
                          setRestoreReason('');
                          setRestoreTarget(post);
                        }}
                        aria-label={`Восстановить заявку ${post.title}`}
                        title="Восстановить заявку"
                      >
                        <ArchiveRestore size={16} aria-hidden="true" />
                      </button>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="admin-icon-btn"
                          onClick={() => {
                            setStatusError(null);
                            setStatusReason('');
                            setStatusValue(post.status);
                            setStatusTarget(post);
                          }}
                          aria-label={`Изменить статус заявки ${post.title}`}
                          title="Изменить статус"
                        >
                          <RefreshCw size={16} aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          className="admin-icon-btn admin-icon-btn--danger"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteReason('');
                            setDeleteTarget(post);
                          }}
                          aria-label={`Удалить заявку ${post.title}`}
                          title="Удалить заявку"
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      </>
                    )}
                  </div>
                </div>

                <h2>
                  <Link to={`/posts/${post.id}`}>{post.title}</Link>
                </h2>
                <p className="admin-request-description">{post.description}</p>

                <div className="admin-request-meta">
                  <span>
                    <User size={13} aria-hidden="true" />
                    {post.authorName} · ID {post.userId}
                  </span>
                  <span>
                    <ClipboardList size={13} aria-hidden="true" />
                    {post.category}
                  </span>
                  {post.userCity && (
                    <span>
                      <MapPin size={13} aria-hidden="true" />
                      {post.userCity}
                    </span>
                  )}
                  <span>
                    <Calendar size={13} aria-hidden="true" />
                    {formatDateTime(post.createdAt)}
                  </span>
                  {post.helperName && (
                    <span>
                      <Handshake size={13} aria-hidden="true" />
                      {post.helperName}
                    </span>
                  )}
                  {post.boosted && <span className="admin-badge admin-badge--lime">Продвигается</span>}
                </div>

                <div className="admin-request-footer">
                  <Link to={`/posts/${post.id}`} className="admin-button admin-button--ghost admin-button--compact">
                    <ExternalLink size={15} aria-hidden="true" />
                    Открыть публикацию
                  </Link>
                  <Link
                    to={`/admin/users/${post.userId}`}
                    className="admin-button admin-button--ghost admin-button--compact"
                  >
                    <User size={15} aria-hidden="true" />
                    Автор
                  </Link>
                </div>
              </article>
            ))}
          </div>

          <Pagination
            page={data.number + 1}
            totalPages={Math.max(1, data.totalPages)}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            style={{ marginTop: '20px' }}
          />
        </>
      )}

      <AdminConfirmDialog
        open={Boolean(deleteTarget)}
        title="Удалить заявку?"
        description={deleteTarget ? `«${deleteTarget.title}» будет мягко удалена.` : undefined}
        consequences={[
          'Публикация исчезнет из публичного списка заявок и из профиля автора.',
          'Связанные помощи, комментарии и отзывы не удаляются автоматически.',
          'Заявку можно восстановить на вкладке «Удалённые»; причина попадёт в аудит.',
        ]}
        confirmText="Удалить заявку"
        busy={deleting}
        error={deleteError}
        reason={deleteReason}
        onReasonChange={setDeleteReason}
        reasonRequired
        reasonPlaceholder="Например: нарушение правил публикации"
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
        title="Восстановить заявку?"
        description={restoreTarget ? `«${restoreTarget.title}» снова появится в списках.` : undefined}
        consequences={[
          'Сервер снимет флаг soft-delete и вернёт публикацию в активные списки.',
          'Операция и причина сохраняются в admin_audit_log.',
        ]}
        confirmText="Восстановить"
        tone="default"
        busy={restoring}
        error={restoreError}
        reason={restoreReason}
        onReasonChange={setRestoreReason}
        reasonRequired
        reasonPlaceholder="Например: удаление было ошибочным"
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

      <AdminConfirmDialog
        open={Boolean(statusTarget)}
        title="Изменить статус заявки?"
        description={statusTarget ? `Заявка «${statusTarget.title}».` : undefined}
        consequences={[
          'Статус меняется административным override, даже если переход не предусмотрен пользовательским сценарием.',
          'Предыдущий и новый статус вместе с причиной сохраняются в admin_audit_log.',
        ]}
        confirmText="Изменить статус"
        tone="default"
        busy={statusBusy}
        error={statusError}
        reason={statusReason}
        onReasonChange={setStatusReason}
        reasonRequired
        reasonPlaceholder="Например: восстановление после ошибочного завершения"
        reasonHint="Причина обязательна и сохраняется в admin_audit_log."
        onConfirm={handleStatusChange}
        onClose={() => {
          if (!statusBusy) {
            setStatusTarget(null);
            setStatusReason('');
            setStatusError(null);
          }
        }}
      >
        <label className="admin-field admin-field--wide">
          <span>Новый статус</span>
          <select
            value={statusValue}
            onChange={(event) => setStatusValue(event.target.value as AdminPostStatus)}
          >
            {POST_STATUS_ORDER.map((value) => (
              <option key={value} value={value}>
                {POST_STATUS_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
      </AdminConfirmDialog>

      <AdminConfirmDialog
        open={bulkOpen}
        title={scope === 'deleted' ? 'Восстановить выбранные заявки?' : 'Удалить выбранные заявки?'}
        description={`Будет обработано заявок: ${selected.length}. Каждый элемент применяется отдельно.`}
        consequences={[
          'Успешные элементы не откатываются, если один из выбранных ID вернул ошибку.',
          'Итог операции и причина сохраняются в admin_audit_log как BULK_OPERATION.',
        ]}
        confirmText={scope === 'deleted' ? 'Восстановить' : 'Удалить'}
        tone={scope === 'deleted' ? 'default' : 'danger'}
        busy={bulkBusy}
        error={bulkError}
        reason={bulkReason}
        onReasonChange={setBulkReason}
        reasonRequired
        reasonPlaceholder="Например: массовая очистка спама"
        reasonHint="Причина обязательна и сохраняется в admin_audit_log."
        onConfirm={handleBulk}
        onClose={() => {
          if (!bulkBusy) {
            setBulkOpen(false);
            setBulkReason('');
            setBulkError(null);
          }
        }}
      />
    </div>
  );
};
