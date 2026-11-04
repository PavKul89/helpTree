import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  FilterX,
  RefreshCw,
  Star,
  Trash2,
  UserRound,
} from 'lucide-react';
import { adminApi, type AdminReviewsQuery } from '../../api/adminApi';
import type { PageResponse } from '../../api/walletApi';
import type { Review } from '../../types';
import { Pagination } from '../../components/Pagination';
import { AdminConfirmDialog } from '../../components/admin/AdminConfirmDialog';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { useToast } from '../../components/Toast';
import { formatDateTime, formatNumber, getApiErrorMessage } from './adminUtils';

type RatingFilter = 'ALL' | '5' | '4' | '3' | '2' | '1';

const PAGE_SIZE = 20;

const Stars: React.FC<{ rating: number }> = ({ rating }) => (
  <span className="admin-stars" aria-label={`Оценка ${rating} из 5`}>
    {[1, 2, 3, 4, 5].map((value) => (
      <Star
        key={value}
        size={13}
        aria-hidden="true"
        className={value <= rating ? 'is-filled' : undefined}
        fill={value <= rating ? 'currentColor' : 'none'}
      />
    ))}
  </span>
);

/** Converts a `<input type="date">` value into an ISO local date-time. */
const toLocalDateTime = (value: string, endOfDay: boolean): string | undefined => {
  if (!value) return undefined;
  return endOfDay ? `${value}T23:59:59` : `${value}T00:00:00`;
};

export const AdminReviewsPage: React.FC = () => {
  const { showToast } = useToast();

  // Draft filters are applied explicitly; this keeps the request in sync with
  // what the server actually received.
  const [userIdInput, setUserIdInput] = useState('');
  const [ratingFilter, setRatingFilter] = useState<RatingFilter>('ALL');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [appliedFilters, setAppliedFilters] = useState<AdminReviewsQuery>({
    size: PAGE_SIZE,
  });
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);

  const [data, setData] = useState<PageResponse<Review> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<Review | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // The request is built from the applied filters and the current page, never
  // from the draft inputs. Typing in a filter does not hit the server until
  // "Применить" is pressed.
  const query = useMemo<AdminReviewsQuery>(
    () => ({ ...appliedFilters, page, size: PAGE_SIZE }),
    [appliedFilters, page]
  );

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    setLoading(true);
    setError(null);

    adminApi
      .getAdminReviews(query, controller.signal)
      .then((response) => {
        if (!cancelled) setData(response);
      })
      .catch((loadError) => {
        if (cancelled || controller.signal.aborted) return;
        setError(getApiErrorMessage(loadError, 'Не удалось загрузить список отзывов.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [query, reloadKey]);

  const applyFilters = useCallback(() => {
    const parsedUserId = Number(userIdInput);
    setAppliedFilters({
      userId:
        Number.isFinite(parsedUserId) && parsedUserId > 0 ? parsedUserId : undefined,
      rating: ratingFilter === 'ALL' ? undefined : Number(ratingFilter),
      from: toLocalDateTime(fromDate, false),
      to: toLocalDateTime(toDate, true),
    });
    setPage(0);
    setReloadKey((value) => value + 1);
  }, [userIdInput, ratingFilter, fromDate, toDate]);

  const resetFilters = () => {
    setUserIdInput('');
    setRatingFilter('ALL');
    setFromDate('');
    setToDate('');
    setAppliedFilters({ size: PAGE_SIZE });
    setPage(0);
    setReloadKey((value) => value + 1);
  };

  const hasDraftFilters =
    userIdInput.trim() !== '' || ratingFilter !== 'ALL' || fromDate !== '' || toDate !== '';
  const hasAppliedFilters = Boolean(
    appliedFilters.userId || appliedFilters.rating || appliedFilters.from || appliedFilters.to
  );

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deleteReview(deleteTarget.id);
      showToast('Отзыв удалён, рейтинг получателя пересчитан.', 'success');
      setDeleteTarget(null);
      // A removed row can empty the last page; step back one page in that case.
      if (data && data.content.length === 1 && page > 0) {
        setPage((value) => Math.max(0, value - 1));
      } else {
        setReloadKey((value) => value + 1);
      }
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить отзыв.'));
    } finally {
      setDeleting(false);
    }
  };

  const reviews = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Star size={17} aria-hidden="true" />
        <p>
          Глобальный список отзывов <code>GET /api/admin/reviews</code> с серверными
          фильтрами по получателю, оценке и периоду. Удаление через{' '}
          <code>DELETE /api/reviews/{'{id}'}</code> выполняет soft-delete и сразу
          пересчитывает рейтинг получателя.
        </p>
      </div>

      <section className="admin-section" aria-labelledby="admin-review-filter-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Фильтры</span>
            <h2 id="admin-review-filter-title">Все отзывы</h2>
            <p className="admin-section-description">
              Фильтры и пагинация применяются на сервере.
            </p>
          </div>
          <span className="admin-count-badge">
            <UserRound size={15} aria-hidden="true" />
            {formatNumber(totalElements)}
          </span>
        </div>

        <div className="admin-toolbar">
          <label className="admin-filter">
            <span>ID получателя</span>
            <input
              type="number"
              min={1}
              value={userIdInput}
              onChange={(event) => setUserIdInput(event.target.value)}
              placeholder="Например, 42"
              aria-label="Фильтр по ID получателя отзыва"
            />
          </label>

          <label className="admin-filter">
            <span>Оценка</span>
            <select
              value={ratingFilter}
              onChange={(event) => setRatingFilter(event.target.value as RatingFilter)}
            >
              <option value="ALL">Все</option>
              <option value="5">5 звёзд</option>
              <option value="4">4 звезды</option>
              <option value="3">3 звезды</option>
              <option value="2">2 звезды</option>
              <option value="1">1 звезда</option>
            </select>
          </label>

          <label className="admin-filter">
            <span>С даты</span>
            <input
              type="date"
              value={fromDate}
              max={toDate || undefined}
              onChange={(event) => setFromDate(event.target.value)}
              aria-label="Отзывы с даты"
            />
          </label>

          <label className="admin-filter">
            <span>По дату</span>
            <input
              type="date"
              value={toDate}
              min={fromDate || undefined}
              onChange={(event) => setToDate(event.target.value)}
              aria-label="Отзывы по дату"
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

        <div className="admin-toolbar-meta">
          <span>
            Найдено <strong>{formatNumber(totalElements)}</strong> отзывов
          </span>
          <span>
            {appliedFilters.userId
              ? `Получатель ID ${appliedFilters.userId}`
              : 'Все получатели'}
            {appliedFilters.rating ? ` · оценка ${appliedFilters.rating}` : ''}
          </span>
        </div>
      </section>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={5} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Отзывы недоступны"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : reviews.length === 0 ? (
        <AdminEmptyState
          title="Отзывов нет"
          description="По выбранным фильтрам отзывов не найдено."
          action={
            <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
              <FilterX size={16} aria-hidden="true" />
              Сбросить фильтры
            </button>
          }
        />
      ) : (
        <>
          <ul className="admin-review-list">
            {reviews.map((review) => (
              <li key={review.id} className="admin-review">
                <div className="admin-review-main">
                  <div className="admin-review-head">
                    <Stars rating={review.rating} />
                    <span className="admin-muted">
                      от {review.fromUserName} для {review.toUserName} · помощь #{review.helpId}
                    </span>
                    <span className="admin-muted">{formatDateTime(review.createdAt)}</span>
                  </div>
                  <p>{review.comment || 'Без комментария.'}</p>
                  <div className="admin-review-links">
                    <Link to={`/admin/users/${review.fromUserId}`}>
                      <UserRound size={13} aria-hidden="true" />
                      Автор
                    </Link>
                    <Link to={`/admin/users/${review.toUserId}`}>
                      <UserRound size={13} aria-hidden="true" />
                      Получатель
                    </Link>
                    {review.postId && <Link to={`/posts/${review.postId}`}>Открыть заявку</Link>}
                  </div>
                </div>
                <button
                  type="button"
                  className="admin-icon-btn admin-icon-btn--danger"
                  onClick={() => {
                    setDeleteError(null);
                    setDeleteTarget(review);
                  }}
                  aria-label={`Удалить отзыв от ${review.fromUserName}`}
                  title="Удалить отзыв"
                >
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>

          <Pagination
            page={page + 1}
            totalPages={totalPages}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            style={{ marginTop: '18px' }}
          />
        </>
      )}

      <AdminConfirmDialog
        open={Boolean(deleteTarget)}
        title="Удалить отзыв?"
        description={
          deleteTarget
            ? `Отзыв от ${deleteTarget.fromUserName} с оценкой ${deleteTarget.rating}/5 для ${deleteTarget.toUserName}.`
            : undefined
        }
        consequences={[
          'Отзыв будет мягко удалён и перестанет отображаться у получателя.',
          'Восстановление отзыва через API не предусмотрено.',
          'Рейтинг получателя будет пересчитан из оставшихся подтверждённых действий.',
        ]}
        confirmText="Удалить отзыв"
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
