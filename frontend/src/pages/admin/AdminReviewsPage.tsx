import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  FilterX,
  RefreshCw,
  Search,
  Star,
  Trash2,
  UserRound,
  Users,
} from 'lucide-react';
import { adminApi, type AdminUser } from '../../api/adminApi';
import type { Review } from '../../types';
import { AdminAvatar } from '../../components/admin/AdminAvatar';
import { AdminConfirmDialog } from '../../components/admin/AdminConfirmDialog';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { useToast } from '../../components/Toast';
import {
  formatDateTime,
  formatNumber,
  formatRating,
  getApiErrorMessage,
  USER_STATUS_LABELS,
} from './adminUtils';

type RatingFilter = 'ALL' | '5' | '4' | '3' | '2' | '1';
type SortOption = 'created-desc' | 'created-asc' | 'rating-desc' | 'rating-asc';

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

export const AdminReviewsPage: React.FC = () => {
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [usersReloadKey, setUsersReloadKey] = useState(0);
  const [userQuery, setUserQuery] = useState('');
  const [selectedUserId, setSelectedUserId] = useState<number | null>(() => {
    const raw = searchParams.get('userId');
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  });

  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewsError, setReviewsError] = useState<string | null>(null);
  const [reviewsReloadKey, setReviewsReloadKey] = useState(0);

  const [search, setSearch] = useState('');
  const [ratingFilter, setRatingFilter] = useState<RatingFilter>('ALL');
  const [sort, setSort] = useState<SortOption>('created-desc');

  const [deleteTarget, setDeleteTarget] = useState<Review | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setUsersLoading(true);
      setUsersError(null);
      try {
        const data = await adminApi.getUsers();
        if (!cancelled) setUsers(data);
      } catch (loadError) {
        if (!cancelled) {
          setUsersError(getApiErrorMessage(loadError, 'Не удалось загрузить список пользователей.'));
        }
      } finally {
        if (!cancelled) setUsersLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [usersReloadKey]);

  useEffect(() => {
    if (!selectedUserId) {
      setReviews([]);
      setReviewsError(null);
      setReviewsLoading(false);
      return;
    }

    let cancelled = false;
    setReviewsLoading(true);
    setReviewsError(null);

    adminApi
      .getUserReviews(selectedUserId)
      .then((data) => {
        if (!cancelled) setReviews(data);
      })
      .catch((loadError) => {
        if (!cancelled) {
          setReviewsError(getApiErrorMessage(loadError, 'Не удалось загрузить отзывы пользователя.'));
        }
      })
      .finally(() => {
        if (!cancelled) setReviewsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedUserId, reviewsReloadKey]);

  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedUserId) ?? null,
    [users, selectedUserId]
  );

  const filteredUsers = useMemo(() => {
    const query = userQuery.trim().toLowerCase();
    const list = query
      ? users.filter((user) =>
          [user.name, user.email, user.city, String(user.id)]
            .filter(Boolean)
            .some((value) => String(value).toLowerCase().includes(query))
        )
      : users;

    return [...list].sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  }, [users, userQuery]);

  const visibleReviews = useMemo(() => {
    const query = search.trim().toLowerCase();

    const result = reviews.filter((review) => {
      if (ratingFilter !== 'ALL' && review.rating !== Number(ratingFilter)) return false;
      if (!query) return true;
      return [review.fromUserName, review.comment, String(review.helpId)]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    });

    return [...result].sort((a, b) => {
      switch (sort) {
        case 'created-asc':
          return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        case 'rating-desc':
          return b.rating - a.rating;
        case 'rating-asc':
          return a.rating - b.rating;
        case 'created-desc':
        default:
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      }
    });
  }, [reviews, search, ratingFilter, sort]);

  const averageRating = useMemo(() => {
    if (reviews.length === 0) return null;
    return reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length;
  }, [reviews]);

  const selectUser = (userId: number) => {
    setSelectedUserId(userId);
    setSearch('');
    setRatingFilter('ALL');
    setSort('created-desc');
    const next = new URLSearchParams(searchParams);
    next.set('userId', String(userId));
    setSearchParams(next, { replace: true });
  };

  const clearSelection = () => {
    setSelectedUserId(null);
    setReviews([]);
    const next = new URLSearchParams(searchParams);
    next.delete('userId');
    setSearchParams(next, { replace: true });
  };

  const filtersActive = search.trim() !== '' || ratingFilter !== 'ALL' || sort !== 'created-desc';

  const resetFilters = () => {
    setSearch('');
    setRatingFilter('ALL');
    setSort('created-desc');
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await adminApi.deleteReview(deleteTarget.id);
      setReviews((previous) => previous.filter((review) => review.id !== deleteTarget.id));
      showToast('Отзыв удалён.', 'success');
      setDeleteTarget(null);
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить отзыв.'));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Star size={17} aria-hidden="true" />
        <p>
          Глобального списка отзывов в API нет: backend отдаёт отзывы только через{' '}
          <code>GET /api/reviews/user/{'{userId}'}</code> и <code>/api/reviews/help/{'{helpId}'}</code>.
          Поэтому раздел начинается с выбора пользователя. Удаление доступно администратору через{' '}
          <code>DELETE /api/reviews/{'{id}'}</code>.
        </p>
      </div>

      <div className="admin-reviews-layout">
        <section className="admin-section admin-user-picker" aria-labelledby="admin-review-users-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Кому</span>
              <h2 id="admin-review-users-title">Выбор пользователя</h2>
              <p className="admin-section-description">
                Отзывы, полученные выбранным пользователем.
              </p>
            </div>
            <span className="admin-count-badge">
              <Users size={15} aria-hidden="true" />
              {formatNumber(users.length)}
            </span>
          </div>

          <div className="admin-search">
            <Search size={16} aria-hidden="true" />
            <input
              type="search"
              value={userQuery}
              onChange={(event) => setUserQuery(event.target.value)}
              placeholder="Имя, email, город или ID"
              aria-label="Поиск пользователя для просмотра отзывов"
            />
          </div>

          {usersLoading ? (
            <AdminLoadingBlock rows={4} />
          ) : usersError ? (
            <AdminErrorState
              title="Пользователи недоступны"
              message={usersError}
              onRetry={() => setUsersReloadKey((value) => value + 1)}
            />
          ) : filteredUsers.length === 0 ? (
            <AdminEmptyState
              title={users.length === 0 ? 'Пользователей нет' : 'Ничего не найдено'}
              description={
                users.length === 0
                  ? 'Сервер вернул пустой список активных аккаунтов.'
                  : 'Измените поисковый запрос.'
              }
            />
          ) : (
            <ul className="admin-user-picker-list" aria-label="Пользователи">
              {filteredUsers.map((user) => (
                <li key={user.id}>
                  <button
                    type="button"
                    className={`admin-user-picker-item${user.id === selectedUserId ? ' is-selected' : ''}`}
                    onClick={() => selectUser(user.id)}
                    aria-pressed={user.id === selectedUserId}
                  >
                    <AdminAvatar name={user.name} avatarUrl={user.avatarUrl} size="small" />
                    <span className="admin-user-picker-copy">
                      <strong>{user.name}</strong>
                      <small>{user.email}</small>
                    </span>
                    <span className="admin-user-picker-meta">
                      <span>{formatRating(user.rating)}</span>
                      <small>{USER_STATUS_LABELS[user.status] ?? user.status}</small>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="admin-stack">
          {!selectedUser ? (
            <AdminEmptyState
              icon={<UserRound size={24} />}
              title="Выберите пользователя"
              description="Отзывы загружаются только в разрезе получателя: в API нет эндпоинта глобального списка отзывов."
            />
          ) : (
            <>
              <section className="admin-section admin-review-summary" aria-labelledby="admin-review-summary-title">
                <div className="admin-review-summary-main">
                  <AdminAvatar name={selectedUser.name} avatarUrl={selectedUser.avatarUrl} size="large" />
                  <div>
                    <span className="admin-eyebrow">Получатель отзывов</span>
                    <h2 id="admin-review-summary-title">{selectedUser.name}</h2>
                    <p>
                      {selectedUser.email}
                      {selectedUser.city ? ` · ${selectedUser.city}` : ''} · ID {selectedUser.id}
                    </p>
                  </div>
                </div>

                <div className="admin-review-summary-actions">
                  {averageRating !== null && (
                    <span className="admin-rating-summary">
                      <Stars rating={Math.round(averageRating)} />
                      <strong>{averageRating.toFixed(2)}</strong>
                      <small>{formatNumber(reviews.length)} отзывов</small>
                    </span>
                  )}
                  <Link to={`/admin/users/${selectedUser.id}`} className="admin-button admin-button--secondary admin-button--compact">
                    <UserRound size={15} aria-hidden="true" />
                    Карточка пользователя
                  </Link>
                  <button
                    type="button"
                    className="admin-button admin-button--ghost admin-button--compact"
                    onClick={clearSelection}
                  >
                    <FilterX size={15} aria-hidden="true" />
                    Сбросить выбор
                  </button>
                </div>
              </section>

              <div className="admin-toolbar-card">
                <div className="admin-toolbar">
                  <div className="admin-search">
                    <Search size={16} aria-hidden="true" />
                    <input
                      type="search"
                      value={search}
                      onChange={(event) => setSearch(event.target.value)}
                      placeholder="Автор, комментарий или ID помощи"
                      aria-label="Поиск в отзывах"
                    />
                  </div>

                  <div className="admin-toolbar-filters">
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
                      <span>Сортировка</span>
                      <select value={sort} onChange={(event) => setSort(event.target.value as SortOption)}>
                        <option value="created-desc">Сначала новые</option>
                        <option value="created-asc">Сначала старые</option>
                        <option value="rating-desc">Оценка ↓</option>
                        <option value="rating-asc">Оценка ↑</option>
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
                      onClick={() => setReviewsReloadKey((value) => value + 1)}
                      disabled={reviewsLoading}
                    >
                      <RefreshCw size={15} aria-hidden="true" />
                      Обновить
                    </button>
                  </div>
                </div>

                <div className="admin-toolbar-meta">
                  <span>
                    Найдено <strong>{formatNumber(visibleReviews.length)}</strong> из{' '}
                    <strong>{formatNumber(reviews.length)}</strong>
                  </span>
                  <span>
                    Поиск и сортировка выполняются в браузере: сервер отдаёт все отзывы пользователя.
                  </span>
                </div>
              </div>

              {reviewsLoading ? (
                <div className="admin-section">
                  <AdminLoadingBlock rows={4} />
                </div>
              ) : reviewsError ? (
                <AdminErrorState
                  title="Отзывы недоступны"
                  message={reviewsError}
                  onRetry={() => setReviewsReloadKey((value) => value + 1)}
                />
              ) : reviews.length === 0 ? (
                <AdminEmptyState
                  title="Отзывов нет"
                  description="Пользователь ещё не получал отзывы."
                />
              ) : visibleReviews.length === 0 ? (
                <AdminEmptyState
                  title="Ничего не найдено"
                  description="По выбранным фильтрам отзывов нет."
                  action={
                    <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
                      <FilterX size={16} aria-hidden="true" />
                      Сбросить фильтры
                    </button>
                  }
                />
              ) : (
                <ul className="admin-review-list">
                  {visibleReviews.map((review) => (
                    <li key={review.id} className="admin-review">
                      <div className="admin-review-main">
                        <div className="admin-review-head">
                          <Stars rating={review.rating} />
                          <span className="admin-muted">
                            от {review.fromUserName} · помощь #{review.helpId}
                          </span>
                          <span className="admin-muted">{formatDateTime(review.createdAt)}</span>
                        </div>
                        <p>{review.comment || 'Без комментария.'}</p>
                        <div className="admin-review-links">
                          <Link to={`/admin/users/${review.fromUserId}`}>
                            <UserRound size={13} aria-hidden="true" />
                            Автор отзыва
                          </Link>
                          <Link to={`/posts/${review.helpId}`}>Открыть помощь</Link>
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
              )}
            </>
          )}
        </div>
      </div>

      <AdminConfirmDialog
        open={Boolean(deleteTarget)}
        title="Удалить отзыв?"
        description={
          deleteTarget
            ? `Отзыв от ${deleteTarget.fromUserName} с оценкой ${deleteTarget.rating}/5 для ${
                selectedUser?.name ?? deleteTarget.toUserName
              }.`
            : undefined
        }
        consequences={[
          'Отзыв будет мягко удалён и перестанет отображаться у получателя.',
          'Восстановление отзыва через API не предусмотрено.',
          'Рейтинг профиля не пересчитывается автоматически; для этого есть отдельная операция пересчёта.',
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
