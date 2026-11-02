import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
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
type SortOption = 'createdAt,desc' | 'createdAt,asc' | 'title,asc' | 'title,desc';

interface AppliedFilters {
  title: string;
  authorName: string;
  category: string;
  city: string;
}

const EMPTY_FILTERS: AppliedFilters = {
  title: '',
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

  const [deleteTarget, setDeleteTarget] = useState<Post | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

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
        const response = await adminApi.getPosts(
          {
            page,
            size,
            status: status === 'ALL' ? undefined : status,
            title: applied.title.trim() || undefined,
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
  }, [page, size, status, applied, sort, reloadKey]);

  const updateStatus = (value: StatusFilter) => {
    setStatus(value);
    setPage(0);
    const next = new URLSearchParams(searchParams);
    if (value === 'ALL') next.delete('status');
    else next.set('status', value);
    setSearchParams(next, { replace: true });
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

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deletePost(deleteTarget.id);
      showToast(`Заявка «${deleteTarget.title}» удалена.`, 'success');
      const wasLastOnPage = Boolean(data && data.content.length === 1);
      setDeleteTarget(null);
      if (page > 0 && wasLastOnPage) {
        setPage(page - 1);
      } else {
        setReloadKey((value) => value + 1);
      }
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить заявку.'));
    } finally {
      setDeleting(false);
    }
  };

  const filtersActive =
    applied.title !== '' ||
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
          Раздел использует <code>GET /api/posts</code> с серверной пагинацией и фильтрами{' '}
          <code>status</code>, <code>title</code>, <code>authorName</code>, <code>category</code>,{' '}
          <code>city</code>. Удалённые заявки backend в список не включает.
        </p>
      </div>

      <form className="admin-toolbar-card" onSubmit={applyFilters}>
        <div className="admin-filter-grid">
          <label className="admin-field">
            <span>Название</span>
            <input
              value={draft.title}
              onChange={(event) => setDraft((previous) => ({ ...previous, title: event.target.value }))}
              placeholder="Поиск по названию"
            />
          </label>
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
          <label className="admin-field">
            <span>Город</span>
            <input
              value={draft.city}
              onChange={(event) => setDraft((previous) => ({ ...previous, city: event.target.value }))}
              placeholder="Город автора"
            />
          </label>
        </div>

        <div className="admin-toolbar">
          <div className="admin-toolbar-filters">
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

            <label className="admin-filter">
              <span>Сортировка</span>
              <select value={sort} onChange={(event) => setSort(event.target.value as SortOption)}>
                <option value="createdAt,desc">Сначала новые</option>
                <option value="createdAt,asc">Сначала старые</option>
                <option value="title,asc">Название А–Я</option>
                <option value="title,desc">Название Я–А</option>
              </select>
            </label>

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
              onClick={() => setReloadKey((value) => value + 1)}
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
            Найдено <strong>{formatNumber(data?.totalElements ?? 0)}</strong> заявок
          </span>
          {data && (
            <span>
              Страница {data.totalPages === 0 ? 0 : data.number + 1} из {data.totalPages}
            </span>
          )}
        </div>
      </form>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={6} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Заявки недоступны"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : !data || data.content.length === 0 ? (
        <AdminEmptyState
          title="Заявки не найдены"
          description={
            filtersActive
              ? 'По выбранным фильтрам сервер вернул пустой список. Сбросьте фильтры и попробуйте снова.'
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
          <div className="admin-request-grid">
            {data.content.map((post) => (
              <article key={post.id} className="admin-request-card">
                <div className="admin-request-head">
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
                    <button
                      type="button"
                      className="admin-icon-btn admin-icon-btn--danger"
                      onClick={() => {
                        setDeleteError(null);
                        setDeleteTarget(post);
                      }}
                      aria-label={`Удалить заявку ${post.title}`}
                      title="Удалить заявку"
                    >
                      <Trash2 size={16} aria-hidden="true" />
                    </button>
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
          'Восстановление доступно только через POST /api/posts/{id}/restore, но списка удалённых в интерфейсе нет.',
        ]}
        confirmText="Удалить заявку"
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
