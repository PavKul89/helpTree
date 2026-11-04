import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Activity as ActivityIcon,
  ArrowLeft,
  Ban,
  CheckCircle2,
  ClipboardList,
  Coins,
  Gift,
  Loader2,
  MessageSquare,
  Pencil,
  RefreshCw,
  ShieldCheck,
  Star,
  Trash2,
  Trophy,
} from 'lucide-react';
import {
  adminApi,
  type AdminUser,
  type RatingResponse,
} from '../../api/adminApi';
import type { Achievement } from '../../api/achievementApi';
import type { Activity } from '../../api/activityApi';
import type { CoinTransactionDto, PageResponse, WalletDto } from '../../api/walletApi';
import type { Post, Review } from '../../types';
import { AdminAvatar } from '../../components/admin/AdminAvatar';
import { AdminConfirmDialog } from '../../components/admin/AdminConfirmDialog';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { Pagination } from '../../components/Pagination';
import { createIdempotencyKey } from '../../utils/idempotency';
import { useToast } from '../../components/Toast';
import { EditUserDialog } from './EditUserDialog';
import {
  POST_STATUS_LABELS,
  USER_STATUS_LABELS,
  formatDateOnly,
  formatDateTime,
  formatNumber,
  formatRating,
  getApiErrorMessage,
  isUserBlocked,
} from './adminUtils';

type SectionKey = 'wallet' | 'posts' | 'reviews' | 'achievements' | 'activities';
type SectionErrors = Partial<Record<SectionKey, string>>;

const TX_PAGE_SIZE = 10;
const ADMIN_COINS_LIMIT = 1_000_000;
const ADMIN_COINS_MAX_REASON = 500;

const TRANSACTION_LABELS: Record<string, string> = {
  HELP_RECEIVED: 'Помощь получена',
  HELP_GIVEN: 'Помощь оказана',
  REVIEW_BONUS: 'Бонус за отзыв',
  DAILY_LOGIN: 'Ежедневный вход',
  POST_BOOST: 'Поднятие заявки',
  BOOST: 'Поднятие заявки',
  VIP_STATUS: 'VIP-статус',
  VIP: 'VIP-статус',
  ACCOUNT_UNBLOCK: 'Разблокировка аккаунта',
  GIFT_SENT: 'Подарок отправлен',
  GIFT_RECEIVED: 'Подарок получен',
  ADMIN_BONUS: 'Начисление администратора',
  FIRST_HELP: 'Первая помощь',
  NICKNAME_COLOR: 'Цвет ника',
};

const RARITY_LABELS: Record<string, string> = {
  COMMON: 'Обычное',
  UNCOMMON: 'Необычное',
  RARE: 'Редкое',
  EPIC: 'Эпическое',
  LEGENDARY: 'Легендарное',
};

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

export const AdminUserDetailPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const id = Number(userId);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [user, setUser] = useState<AdminUser | null>(null);
  const [wallet, setWallet] = useState<WalletDto | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [sectionErrors, setSectionErrors] = useState<SectionErrors>({});

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const [transactions, setTransactions] = useState<PageResponse<CoinTransactionDto> | null>(null);
  const [txPage, setTxPage] = useState(0);
  const [txLoading, setTxLoading] = useState(true);
  const [txError, setTxError] = useState<string | null>(null);
  const [txReloadKey, setTxReloadKey] = useState(0);

  const [editOpen, setEditOpen] = useState(false);

  const [ratingValue, setRatingValue] = useState('');
  const [ratingBusy, setRatingBusy] = useState(false);
  const [ratingError, setRatingError] = useState<string | null>(null);

  const [coinAmount, setCoinAmount] = useState('');
  const [coinReason, setCoinReason] = useState('');
  const [coinsBusy, setCoinsBusy] = useState(false);
  const [coinsError, setCoinsError] = useState<string | null>(null);
  // Reused across retries of the same command; cleared whenever the command changes.
  const coinsKeyRef = useRef<string | null>(null);

  const [recalcOpen, setRecalcOpen] = useState(false);
  const [recalcBusy, setRecalcBusy] = useState(false);
  const [recalcError, setRecalcError] = useState<string | null>(null);
  const [recalcResult, setRecalcResult] = useState<RatingResponse | null>(null);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [postTarget, setPostTarget] = useState<Post | null>(null);
  const [postBusy, setPostBusy] = useState(false);
  const [postDeleteError, setPostDeleteError] = useState<string | null>(null);

  const [reviewTarget, setReviewTarget] = useState<Review | null>(null);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewDeleteError, setReviewDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isFinite(id) || id <= 0) {
      setError('Некорректный ID пользователя.');
      setLoading(false);
      return;
    }

    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      setSectionErrors({});
      setRecalcResult(null);

      try {
        const loadedUser = await adminApi.getUser(id);
        if (cancelled) return;
        setUser(loadedUser);
        setRatingValue(String(loadedUser.rating ?? ''));

        const errors: SectionErrors = {};
        const settle = async <T,>(
          promise: Promise<T>,
          key: SectionKey,
          fallback: string
        ): Promise<T | undefined> => {
          try {
            return await promise;
          } catch (sectionError) {
            errors[key] = getApiErrorMessage(sectionError, fallback);
            return undefined;
          }
        };

        const [walletData, postsData, reviewsData, achievementsData, activitiesData] =
          await Promise.all([
            settle(adminApi.getWallet(id), 'wallet', 'Кошелёк недоступен.'),
            settle(adminApi.getUserPosts(id), 'posts', 'Заявки пользователя недоступны.'),
            settle(adminApi.getUserReviews(id), 'reviews', 'Отзывы пользователя недоступны.'),
            settle(
              adminApi.getUserAchievements(id),
              'achievements',
              'Достижения пользователя недоступны.'
            ),
            settle(
              adminApi.getUserActivities(id, 50),
              'activities',
              'Активность пользователя недоступна.'
            ),
          ]);

        if (cancelled) return;

        if (walletData) setWallet(walletData);
        if (postsData) setPosts(postsData);
        if (reviewsData) setReviews(reviewsData);
        if (achievementsData) setAchievements(achievementsData);
        if (activitiesData) setActivities(activitiesData);
        setSectionErrors(errors);
      } catch (loadError) {
        if (!cancelled) {
          setError(getApiErrorMessage(loadError, 'Пользователь не найден или недоступен.'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [id, reloadKey]);

  useEffect(() => {
    if (!Number.isFinite(id) || id <= 0) return;

    let cancelled = false;
    setTxLoading(true);
    setTxError(null);

    adminApi
      .getWalletTransactions(id, txPage, TX_PAGE_SIZE)
      .then((data) => {
        if (!cancelled) setTransactions(data);
      })
      .catch((txFailure) => {
        if (!cancelled) {
          setTxError(getApiErrorMessage(txFailure, 'История транзакций недоступна.'));
        }
      })
      .finally(() => {
        if (!cancelled) setTxLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [id, txPage, txReloadKey]);

  const reviewAverage = useMemo(() => {
    if (reviews.length === 0) return null;
    return reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length;
  }, [reviews]);

  const refreshWallet = async () => {
    try {
      const [walletData, txData] = await Promise.all([
        adminApi.getWallet(id),
        adminApi.getWalletTransactions(id, txPage, TX_PAGE_SIZE),
      ]);
      setWallet(walletData);
      setTransactions(txData);
      setSectionErrors((previous) => {
        if (!previous.wallet) return previous;
        const next = { ...previous };
        delete next.wallet;
        return next;
      });
      setTxError(null);
    } catch {
      // The mutation already succeeded; a refresh failure must not report it as failed.
    }
  };

  const handleRatingSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (ratingBusy) return;

    const parsed = Number(ratingValue);
    if (!Number.isFinite(parsed) || parsed < 0 || parsed > 5) {
      setRatingError('Рейтинг должен быть числом от 0 до 5.');
      return;
    }

    setRatingBusy(true);
    setRatingError(null);
    try {
      await adminApi.updateUserRating(id, parsed);
      setUser((previous) => (previous ? { ...previous, rating: parsed } : previous));
      showToast(`Рейтинг профиля обновлён: ${parsed.toFixed(2)}.`, 'success');
    } catch (ratingFailure) {
      setRatingError(getApiErrorMessage(ratingFailure, 'Не удалось изменить рейтинг.'));
    } finally {
      setRatingBusy(false);
    }
  };

  const handleCoinsSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (coinsBusy) return;

    const amount = Number(coinAmount);
    if (!Number.isInteger(amount) || amount === 0 || Math.abs(amount) > ADMIN_COINS_LIMIT) {
      setCoinsError(
        `Укажите целое число HC от −${formatNumber(ADMIN_COINS_LIMIT)} до ${formatNumber(ADMIN_COINS_LIMIT)}, кроме нуля.`
      );
      return;
    }

    const reason = coinReason.trim();
    if (!reason) {
      setCoinsError('Причина операции обязательна.');
      return;
    }
    if (reason.length > ADMIN_COINS_MAX_REASON) {
      setCoinsError(`Причина не должна превышать ${ADMIN_COINS_MAX_REASON} символов.`);
      return;
    }

    if (!coinsKeyRef.current) {
      coinsKeyRef.current = createIdempotencyKey('admin');
    }

    setCoinsBusy(true);
    setCoinsError(null);
    try {
      const result = await adminApi.addCoins(id, amount, reason, coinsKeyRef.current);
      coinsKeyRef.current = null;
      if (result.applied) {
        const action = amount > 0 ? 'Начислено' : 'Списано';
        showToast(
          `${action} ${formatNumber(Math.abs(amount))} HC. Баланс: ${formatNumber(result.newBalance)} HC.`,
          'success'
        );
        setCoinAmount('');
      } else {
        showToast(
          `Эта операция уже была применена ранее. Баланс: ${formatNumber(result.newBalance)} HC.`,
          'info'
        );
      }
      await refreshWallet();
    } catch (coinsFailure) {
      setCoinsError(getApiErrorMessage(coinsFailure, 'Не удалось изменить HelpCoins.'));
    } finally {
      setCoinsBusy(false);
    }
  };

  const handleRecalculate = async () => {
    if (recalcBusy) return;
    setRecalcBusy(true);
    setRecalcError(null);
    try {
      const result = await adminApi.recalculateRating(id);
      setRecalcResult(result);
      showToast(`Рейтинг пересчитан: ${formatRating(result.overallRating)}.`, 'success');
      setRecalcOpen(false);
    } catch (recalcFailure) {
      setRecalcError(getApiErrorMessage(recalcFailure, 'Не удалось пересчитать рейтинг.'));
    } finally {
      setRecalcBusy(false);
    }
  };

  const handleDeleteUser = async () => {
    if (deleteBusy) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await adminApi.deleteUser(id);
      showToast(`Пользователь «${user?.name ?? id}» удалён.`, 'success');
      navigate('/admin/users');
    } catch (deleteFailure) {
      setDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить пользователя.'));
    } finally {
      setDeleteBusy(false);
    }
  };

  const handleDeletePost = async () => {
    if (!postTarget || postBusy) return;
    setPostBusy(true);
    setPostDeleteError(null);
    try {
      await adminApi.deletePost(postTarget.id);
      setPosts((previous) => previous.filter((post) => post.id !== postTarget.id));
      showToast(`Заявка «${postTarget.title}» удалена.`, 'success');
      setPostTarget(null);
    } catch (deleteFailure) {
      setPostDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить заявку.'));
    } finally {
      setPostBusy(false);
    }
  };

  const handleDeleteReview = async () => {
    if (!reviewTarget || reviewBusy) return;
    setReviewBusy(true);
    setReviewDeleteError(null);
    try {
      await adminApi.deleteReview(reviewTarget.id);
      setReviews((previous) => previous.filter((review) => review.id !== reviewTarget.id));
      showToast('Отзыв удалён.', 'success');
      setReviewTarget(null);
    } catch (deleteFailure) {
      setReviewDeleteError(getApiErrorMessage(deleteFailure, 'Не удалось удалить отзыв.'));
    } finally {
      setReviewBusy(false);
    }
  };

  const breadcrumb = (
    <nav className="admin-breadcrumb" aria-label="Хлебные крошки">
      <Link to="/admin">Админ-панель</Link>
      <span aria-hidden="true">/</span>
      <Link to="/admin/users">Пользователи</Link>
      <span aria-hidden="true">/</span>
      <span aria-current="page">{user?.name ?? 'Пользователь'}</span>
    </nav>
  );

  if (loading) {
    return (
      <div className="admin-stack">
        {breadcrumb}
        <div className="admin-notice">
          <RefreshCw size={17} className="admin-spin-slow" aria-hidden="true" />
          <p>Загружаем карточку пользователя и связанные данные…</p>
        </div>
        <AdminLoadingBlock rows={3} />
        <AdminLoadingBlock rows={5} />
      </div>
    );
  }

  if (error || !user) {
    return (
      <div className="admin-stack">
        {breadcrumb}
        <AdminErrorState
          title="Пользователь недоступен"
          message={error ?? 'Не удалось получить профиль пользователя.'}
          onRetry={() => setReloadKey((value) => value + 1)}
        >
          <Link to="/admin/users" className="admin-button admin-button--secondary">
            <ArrowLeft size={16} aria-hidden="true" />
            К списку пользователей
          </Link>
        </AdminErrorState>
      </div>
    );
  }

  const blocked = isUserBlocked(user);
  const earnedAchievements = achievements.filter((item) => item.isEarned).length;

  return (
    <div className="admin-stack">
      {breadcrumb}

      <section className="admin-profile-hero" aria-labelledby="admin-profile-name">
        <div className="admin-profile-main">
          <AdminAvatar name={user.name} avatarUrl={user.avatarUrl} size="large" />
          <div className="admin-profile-copy">
            <div className="admin-badge-row">
              <span className={`admin-badge ${user.role === 'ADMIN' ? 'admin-badge--lime' : 'admin-badge--neutral'}`}>
                <ShieldCheck size={12} aria-hidden="true" />
                {user.role}
              </span>
              <span className={`admin-badge admin-badge--status-${user.status.toLowerCase()}`}>
                {USER_STATUS_LABELS[user.status] ?? user.status}
              </span>
              {blocked && (
                <span className="admin-badge admin-badge--danger">
                  <Ban size={12} aria-hidden="true" />
                  блокировка
                </span>
              )}
              {user.vipUntil && new Date(user.vipUntil).getTime() > Date.now() && (
                <span className="admin-badge admin-badge--vip">VIP до {formatDateOnly(user.vipUntil)}</span>
              )}
            </div>

            <h2 id="admin-profile-name">{user.name}</h2>
            <p>
              {user.email}
              {user.city ? ` · ${user.city}` : ''}
            </p>

            <div className="admin-profile-meta">
              <span>ID {user.id}</span>
              <span>Создан {formatDateOnly(user.createdAt)}</span>
              {user.phone && <span>{user.phone}</span>}
              <span>{formatNumber(user.helpedCount)} помощей</span>
            </div>
          </div>
        </div>

        <div className="admin-profile-actions">
          <button
            type="button"
            className="admin-button admin-button--secondary"
            onClick={() => setEditOpen(true)}
          >
            <Pencil size={16} aria-hidden="true" />
            Редактировать
          </button>
          <button
            type="button"
            className="admin-button admin-button--secondary"
            onClick={() => {
              setRecalcError(null);
              setRecalcOpen(true);
            }}
          >
            <RefreshCw size={16} aria-hidden="true" />
            Пересчитать рейтинг
          </button>
        </div>
      </section>

      {blocked && (
        <div className="admin-warning" role="status">
          <Ban size={17} aria-hidden="true" />
          <p>
            Аккаунт заблокирован
            {user.blockedUntil ? ` до ${formatDateTime(user.blockedUntil)}` : ''}.
            {user.blockedAt ? ` Блокировка начата ${formatDateTime(user.blockedAt)}.` : ''}
            {' '}Блокировка управляется долгом и планировщиком; отдельного admin-эндпоинта
            блокировки/разблокировки в API нет.
          </p>
        </div>
      )}

      <div className="admin-kpi-grid admin-kpi-grid--compact">
        <article className="admin-kpi">
          <span className="admin-kpi-icon" aria-hidden="true">
            <Star size={18} />
          </span>
          <div>
            <span className="admin-kpi-label">Рейтинг профиля</span>
            <strong>{formatRating(user.rating)}</strong>
            <small>поле rating из UserDto</small>
          </div>
        </article>
        <article className="admin-kpi">
          <span className="admin-kpi-icon" aria-hidden="true">
            <CheckCircle2 size={18} />
          </span>
          <div>
            <span className="admin-kpi-label">Помог</span>
            <strong>{formatNumber(user.helpedCount)}</strong>
            <small>helpedCount</small>
          </div>
        </article>
        <article className="admin-kpi">
          <span className="admin-kpi-icon" aria-hidden="true">
            <Ban size={18} />
          </span>
          <div>
            <span className="admin-kpi-label">Долг</span>
            <strong>{formatNumber(user.debtCount)}</strong>
            <small>debtCount</small>
          </div>
        </article>
        <article className="admin-kpi">
          <span className="admin-kpi-icon" aria-hidden="true">
            <Coins size={18} />
          </span>
          <div>
            <span className="admin-kpi-label">HelpCoins</span>
            <strong>{formatNumber(user.helpCoins)}</strong>
            <small>helpCoins</small>
          </div>
        </article>
      </div>

      <div className="admin-split">
        <section className="admin-section" aria-labelledby="admin-operations-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Операции</span>
              <h2 id="admin-operations-title">Административные действия</h2>
              <p className="admin-section-description">
                Каждое действие вызывает существующий admin-эндпоинт; интерфейс не обходит
                серверную авторизацию.
              </p>
            </div>
          </div>

          <div className="admin-operation">
            <h3>Изменить рейтинг профиля</h3>
            <form className="admin-inline-form" onSubmit={handleRatingSubmit} noValidate>
              <label className="admin-field">
                <span>Рейтинг (0–5)</span>
                <input
                  type="number"
                  min={0}
                  max={5}
                  step={0.01}
                  value={ratingValue}
                  onChange={(event) => {
                    setRatingValue(event.target.value);
                    setRatingError(null);
                  }}
                  aria-invalid={Boolean(ratingError)}
                  aria-label="Новое значение рейтинга"
                />
              </label>
              <button type="submit" className="admin-button admin-button--primary" disabled={ratingBusy}>
                {ratingBusy ? (
                  <Loader2 size={16} className="admin-spinner" aria-hidden="true" />
                ) : (
                  <Star size={16} aria-hidden="true" />
                )}
                {ratingBusy ? 'Сохраняем…' : 'Применить'}
              </button>
            </form>
            {ratingError && (
              <div className="admin-inline-alert" role="alert">
                {ratingError}
              </div>
            )}
            <p className="admin-field-hint">
              <code>PUT /api/users/{user.id}/rating?rating=…</code>. Меняет только поле рейтинга
              профиля.
            </p>
          </div>

          <div className="admin-operation">
            <h3>Изменить HelpCoins</h3>
            <form className="admin-inline-form admin-inline-form--wrap" onSubmit={handleCoinsSubmit} noValidate>
              <label className="admin-field admin-field--amount">
                <span>Сумма, HC</span>
                <input
                  type="number"
                  min={-ADMIN_COINS_LIMIT}
                  max={ADMIN_COINS_LIMIT}
                  step={1}
                  value={coinAmount}
                  onChange={(event) => {
                    setCoinAmount(event.target.value);
                    coinsKeyRef.current = null;
                    setCoinsError(null);
                  }}
                  placeholder="Например, 50 или −20"
                  aria-invalid={Boolean(coinsError)}
                  aria-label="Сумма изменения HelpCoins"
                />
              </label>
              <label className="admin-field admin-field--grow">
                <span>Причина (обязательно)</span>
                <input
                  value={coinReason}
                  onChange={(event) => {
                    setCoinReason(event.target.value);
                    coinsKeyRef.current = null;
                    setCoinsError(null);
                  }}
                  maxLength={ADMIN_COINS_MAX_REASON}
                  required
                  aria-invalid={Boolean(coinsError)}
                  aria-label="Причина изменения HelpCoins"
                />
              </label>
              <button type="submit" className="admin-button admin-button--primary" disabled={coinsBusy}>
                {coinsBusy ? (
                  <Loader2 size={16} className="admin-spinner" aria-hidden="true" />
                ) : (
                  <Coins size={16} aria-hidden="true" />
                )}
                {coinsBusy ? 'Применяем…' : 'Применить'}
              </button>
            </form>
            {coinsError && (
              <div className="admin-inline-alert" role="alert">
                {coinsError}
              </div>
            )}
            <p className="admin-field-hint">
              Положительная сумма начисляет HC, отрицательная — списывает. Лимит одной операции —
              {' '}{formatNumber(ADMIN_COINS_LIMIT)} HC, причина обязательна и попадает в аудит.
            </p>
            <p className="admin-field-hint">
              <code>POST /api/users/{user.id}/wallet/admin-add</code>. Повторный запрос с тем же
              ключом идемпотентности не спишет и не начислит HC второй раз.
            </p>
          </div>

          {recalcResult && (
            <div className="admin-result" role="status">
              <div className="admin-result-head">
                <span className="admin-eyebrow">Результат пересчёта</span>
                <strong>{formatRating(recalcResult.overallRating)}</strong>
              </div>
              <dl className="admin-result-grid">
                <div>
                  <dt>Уровень</dt>
                  <dd>{recalcResult.level}</dd>
                </div>
                <div>
                  <dt>Подтверждённые акты</dt>
                  <dd>{formatNumber(recalcResult.totalHelps)}</dd>
                </div>
                <div>
                  <dt>Все записи о помощи</dt>
                  <dd>{formatNumber(recalcResult.totalHelpRecords)}</dd>
                </div>
                <div>
                  <dt>Активные / отменённые</dt>
                  <dd>{formatNumber(recalcResult.activeHelps)} / {formatNumber(recalcResult.cancelledHelps)}</dd>
                </div>
                <div>
                  <dt>Помощей получено</dt>
                  <dd>{formatNumber(recalcResult.totalReceivedHelps)}</dd>
                </div>
                <div>
                  <dt>Отзывы</dt>
                  <dd>
                    {formatNumber(recalcResult.reviewCount)}
                    {recalcResult.averageReviewRating != null
                      ? ` · ${recalcResult.averageReviewRating.toFixed(2)}`
                      : ''}
                  </dd>
                </div>
                <div>
                  <dt>Успешность</dt>
                  <dd>{formatNumber(recalcResult.successRate)}</dd>
                </div>
              </dl>
              <p className="admin-field-hint">
                Пересчёт заново строит рейтинг и статистику из подтверждённых помощи и
                отзывов; <code>UserDto.rating</code> синхронизируется с результатом.
              </p>
            </div>
          )}
        </section>

        <section className="admin-section" aria-labelledby="admin-account-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Учётная запись</span>
              <h2 id="admin-account-title">Данные профиля</h2>
            </div>
          </div>

          <dl className="admin-detail-list">
            <div>
              <dt>Email</dt>
              <dd>{user.email}</dd>
            </div>
            <div>
              <dt>Телефон</dt>
              <dd>{user.phone || '—'}</dd>
            </div>
            <div>
              <dt>Город</dt>
              <dd>{user.city || '—'}</dd>
            </div>
            <div>
              <dt>Дата рождения</dt>
              <dd>{user.birthDate ? formatDateOnly(user.birthDate) : '—'}</dd>
            </div>
            <div>
              <dt>Создан</dt>
              <dd>{formatDateTime(user.createdAt)}</dd>
            </div>
            <div>
              <dt>VIP до</dt>
              <dd>{user.vipUntil ? formatDateTime(user.vipUntil) : '—'}</dd>
            </div>
            <div>
              <dt>Долг с</dt>
              <dd>{user.debtStartedAt ? formatDateTime(user.debtStartedAt) : '—'}</dd>
            </div>
            <div>
              <dt>Блокировка</dt>
              <dd>
                {user.blockedAt ? formatDateTime(user.blockedAt) : '—'}
                {user.blockedUntil ? ` → ${formatDateTime(user.blockedUntil)}` : ''}
              </dd>
            </div>
          </dl>

          <p className="admin-field-hint">
            Смена роли через API не предусмотрена: <code>UpdateUserRequest</code> не содержит поля{' '}
            <code>role</code>.
          </p>
        </section>
      </div>

      <section className="admin-section" aria-labelledby="admin-wallet-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Экономика</span>
            <h2 id="admin-wallet-title">Кошелёк и транзакции</h2>
            <p className="admin-section-description">
              <code>GET /api/users/{user.id}/wallet</code> и{' '}
              <code>GET /api/users/{user.id}/wallet/transactions</code> с серверной пагинацией.
            </p>
          </div>
          <button
            type="button"
            className="admin-button admin-button--ghost admin-button--compact"
            onClick={() => {
              setTxReloadKey((value) => value + 1);
              refreshWallet();
            }}
            disabled={txLoading}
          >
            <RefreshCw size={15} aria-hidden="true" />
            Обновить
          </button>
        </div>

        {sectionErrors.wallet ? (
          <div className="admin-inline-alert" role="alert">
            {sectionErrors.wallet}
          </div>
        ) : wallet ? (
          <div className="admin-wallet-grid">
            <article className="admin-wallet-card">
              <span>Баланс</span>
              <strong>{formatNumber(wallet.balance)} HC</strong>
            </article>
            <article className="admin-wallet-card">
              <span>Всего заработано</span>
              <strong>{formatNumber(wallet.totalEarned)} HC</strong>
            </article>
            <article className="admin-wallet-card">
              <span>Всего потрачено</span>
              <strong>{formatNumber(wallet.totalSpent)} HC</strong>
            </article>
          </div>
        ) : (
          <AdminLoadingBlock rows={1} />
        )}

        <div className="admin-subsection">
          <h3>Транзакции</h3>
          {txLoading ? (
            <AdminLoadingBlock rows={3} />
          ) : txError ? (
            <div className="admin-inline-alert" role="alert">
              <span>{txError}</span>
              <button
                type="button"
                className="admin-button admin-button--ghost admin-button--compact"
                onClick={() => setTxReloadKey((value) => value + 1)}
              >
                Повторить
              </button>
            </div>
          ) : !transactions || transactions.content.length === 0 ? (
            <AdminEmptyState title="Транзакций нет" description="История кошелька пользователя пуста." />
          ) : (
            <>
              <div className="admin-table-wrap">
                <table className="admin-table admin-table--compact">
                  <thead>
                    <tr>
                      <th scope="col">Дата</th>
                      <th scope="col">Тип</th>
                      <th scope="col">Сумма</th>
                      <th scope="col">Описание</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions.content.map((transaction) => (
                      <tr key={transaction.id}>
                        <td>{formatDateTime(transaction.createdAt)}</td>
                        <td>{TRANSACTION_LABELS[transaction.type] ?? transaction.type}</td>
                        <td
                          className={
                            transaction.amount >= 0 ? 'admin-amount-positive' : 'admin-amount-negative'
                          }
                        >
                          {transaction.amount > 0 ? '+' : ''}
                          {formatNumber(transaction.amount)} HC
                        </td>
                        <td>{transaction.description || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={txPage + 1}
                totalPages={Math.max(1, transactions.totalPages)}
                onPageChange={(nextPage) => setTxPage(nextPage - 1)}
                style={{ marginTop: '16px' }}
              />
            </>
          )}
        </div>
      </section>

      <section className="admin-section" aria-labelledby="admin-posts-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Заявки</span>
            <h2 id="admin-posts-title">Публикации пользователя</h2>
            <p className="admin-section-description">
              <code>GET /api/posts/user/{user.id}</code>; удалённые посты сервер не возвращает.
            </p>
          </div>
          <span className="admin-count-badge">
            <ClipboardList size={15} aria-hidden="true" />
            {formatNumber(posts.length)}
          </span>
        </div>

        {sectionErrors.posts ? (
          <AdminErrorState
            title="Заявки недоступны"
            message={sectionErrors.posts}
            onRetry={() => setReloadKey((value) => value + 1)}
          />
        ) : posts.length === 0 ? (
          <AdminEmptyState title="Заявок нет" description="Пользователь ещё не создавал заявки." />
        ) : (
          <ul className="admin-record-list">
            {posts.map((post) => (
              <li key={post.id} className="admin-record">
                <div className="admin-record-main">
                  <div className="admin-record-title">
                    <Link to={`/posts/${post.id}`}>{post.title}</Link>
                    <span className={`admin-badge admin-badge--post-${post.status.toLowerCase()}`}>
                      {POST_STATUS_LABELS[post.status]}
                    </span>
                  </div>
                  <p>{post.description}</p>
                  <div className="admin-record-meta">
                    <span>{post.category}</span>
                    {post.userCity && <span>{post.userCity}</span>}
                    <span>{formatDateTime(post.createdAt)}</span>
                    {post.helperName && <span>Помогает: {post.helperName}</span>}
                  </div>
                </div>
                <button
                  type="button"
                  className="admin-icon-btn admin-icon-btn--danger"
                  onClick={() => {
                    setPostDeleteError(null);
                    setPostTarget(post);
                  }}
                  aria-label={`Удалить заявку ${post.title}`}
                  title="Удалить заявку"
                >
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="admin-section" aria-labelledby="admin-reviews-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Репутация</span>
            <h2 id="admin-reviews-title">Полученные отзывы</h2>
            <p className="admin-section-description">
              <code>GET /api/reviews/user/{user.id}</code>. Глобального списка отзывов в API нет —
              отзывы доступны только в разрезе пользователя или помощи.
            </p>
          </div>
          {reviewAverage !== null && (
            <span className="admin-rating-summary">
              <Stars rating={Math.round(reviewAverage)} />
              <strong>{reviewAverage.toFixed(2)}</strong>
              <small>{reviews.length} отзывов</small>
            </span>
          )}
        </div>

        {sectionErrors.reviews ? (
          <AdminErrorState
            title="Отзывы недоступны"
            message={sectionErrors.reviews}
            onRetry={() => setReloadKey((value) => value + 1)}
          />
        ) : reviews.length === 0 ? (
          <AdminEmptyState title="Отзывов нет" description="Пользователь ещё не получал отзывы." />
        ) : (
          <ul className="admin-review-list">
            {reviews.map((review) => (
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
                </div>
                <button
                  type="button"
                  className="admin-icon-btn admin-icon-btn--danger"
                  onClick={() => {
                    setReviewDeleteError(null);
                    setReviewTarget(review);
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
      </section>

      <div className="admin-split">
        <section className="admin-section" aria-labelledby="admin-achievements-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">Достижения</span>
              <h2 id="admin-achievements-title">Прогресс достижений</h2>
              <p className="admin-section-description">
                Получено {earnedAchievements} из {achievements.length}.
              </p>
            </div>
            <Trophy size={20} className="admin-section-mark" aria-hidden="true" />
          </div>

          {sectionErrors.achievements ? (
            <div className="admin-inline-alert" role="alert">
              {sectionErrors.achievements}
            </div>
          ) : achievements.length === 0 ? (
            <AdminEmptyState title="Достижений нет" description="Сервер не вернул достижения." />
          ) : (
            <ul className="admin-achievement-list">
              {achievements.map((achievement) => {
                const progress = achievement.targetValue
                  ? Math.min(
                      100,
                      Math.round(((achievement.currentProgress ?? 0) / achievement.targetValue) * 100)
                    )
                  : achievement.isEarned
                    ? 100
                    : 0;

                return (
                  <li
                    key={achievement.id ?? `${achievement.type}-${achievement.name}`}
                    className={`admin-achievement${achievement.isEarned ? ' is-earned' : ''}`}
                  >
                    <span className="admin-achievement-emoji" aria-hidden="true">
                      {achievement.emoji}
                    </span>
                    <div className="admin-achievement-copy">
                      <div className="admin-achievement-head">
                        <strong>{achievement.name}</strong>
                        <span className={`admin-badge admin-badge--rarity-${achievement.rarity.toLowerCase()}`}>
                          {RARITY_LABELS[achievement.rarity] ?? achievement.rarity}
                        </span>
                      </div>
                      <p>{achievement.description}</p>
                      <div className="admin-progress" aria-label={`Прогресс ${progress}%`}>
                        <span style={{ width: `${progress}%` }} />
                      </div>
                      <small>
                        {achievement.isEarned
                          ? `Получено${achievement.earnedAt ? ` ${formatDateOnly(achievement.earnedAt)}` : ''}`
                          : `${formatNumber(achievement.currentProgress)} / ${formatNumber(achievement.targetValue)}`}
                      </small>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="admin-section" aria-labelledby="admin-activity-title">
          <div className="admin-section-head">
            <div>
              <span className="admin-eyebrow">История</span>
              <h2 id="admin-activity-title">Активность пользователя</h2>
              <p className="admin-section-description">
                <code>GET /api/users/{user.id}/activities</code>. Это действия пользователя, а не
                журнал административных операций.
              </p>
            </div>
            <ActivityIcon size={20} className="admin-section-mark" aria-hidden="true" />
          </div>

          {sectionErrors.activities ? (
            <div className="admin-inline-alert" role="alert">
              {sectionErrors.activities}
            </div>
          ) : activities.length === 0 ? (
            <AdminEmptyState title="Активности нет" description="Сервер не вернул события." />
          ) : (
            <ol className="admin-timeline">
              {activities.map((activity, index) => (
                <li key={`${activity.type}-${activity.timestamp}-${index}`}>
                  <span className="admin-timeline-emoji" aria-hidden="true">
                    {activity.emoji}
                  </span>
                  <div>
                    <div className="admin-timeline-head">
                      <strong>{activity.title}</strong>
                      <span>{formatDateTime(activity.timestamp)}</span>
                    </div>
                    <p>
                      {activity.typeLabel}
                      {activity.description ? ` · ${activity.description}` : ''}
                    </p>
                    <div className="admin-timeline-links">
                      {activity.relatedPostId && (
                        <Link to={`/posts/${activity.relatedPostId}`}>
                          <ClipboardList size={13} aria-hidden="true" />
                          {activity.relatedPostTitle || `Заявка #${activity.relatedPostId}`}
                        </Link>
                      )}
                      {activity.relatedUserId && (
                        <Link to={`/admin/users/${activity.relatedUserId}`}>
                          <MessageSquare size={13} aria-hidden="true" />
                          {activity.relatedUserName || `Пользователь #${activity.relatedUserId}`}
                        </Link>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      <section className="admin-section admin-section--danger" aria-labelledby="admin-danger-title">
        <div className="admin-section-head">
          <div>
            <span className="admin-eyebrow">Опасная зона</span>
            <h2 id="admin-danger-title">Удаление аккаунта</h2>
            <p className="admin-section-description">
              Мягкое удаление через <code>DELETE /api/users/{user.id}</code>. Списка удалённых
              аккаунтов в интерфейсе нет, восстановление из панели недоступно.
            </p>
          </div>
          <button
            type="button"
            className="admin-button admin-button--danger"
            onClick={() => {
              setDeleteError(null);
              setDeleteOpen(true);
            }}
          >
            <Trash2 size={16} aria-hidden="true" />
            Удалить пользователя
          </button>
        </div>
      </section>

      <EditUserDialog
        open={editOpen}
        user={user}
        onClose={() => setEditOpen(false)}
        onSaved={(updated) => setUser(updated)}
      />

      <AdminConfirmDialog
        open={recalcOpen}
        title="Пересчитать рейтинг?"
        description={`Профиль «${user.name}», текущий рейтинг ${formatRating(user.rating)}.`}
        consequences={[
          'Сервер пересчитает статистику рейтинга по фактическим помощям и отзывам.',
          'Итоговое значение может отличаться от текущего рейтинга профиля.',
          'Результат пересчёта отобразится на этой странице; поле профиля rating не перезаписывается.',
        ]}
        confirmText="Пересчитать"
        tone="default"
        busy={recalcBusy}
        error={recalcError}
        onConfirm={handleRecalculate}
        onClose={() => {
          if (!recalcBusy) {
            setRecalcOpen(false);
            setRecalcError(null);
          }
        }}
      />

      <AdminConfirmDialog
        open={deleteOpen}
        title="Удалить пользователя?"
        description={`Аккаунт «${user.name}» (${user.email}) будет мягко удалён.`}
        consequences={[
          'Сервер выполнит soft-delete: пользователь исчезнет из списка активных аккаунтов.',
          'Пользователь потеряет доступ к аккаунту; операция необратима из этой панели.',
          'Восстановление возможно только через POST /api/users/{id}/restore, но списка удалённых нет.',
        ]}
        confirmText="Удалить пользователя"
        busy={deleteBusy}
        error={deleteError}
        onConfirm={handleDeleteUser}
        onClose={() => {
          if (!deleteBusy) {
            setDeleteOpen(false);
            setDeleteError(null);
          }
        }}
      />

      <AdminConfirmDialog
        open={Boolean(postTarget)}
        title="Удалить заявку?"
        description={postTarget ? `«${postTarget.title}» будет мягко удалена.` : undefined}
        consequences={[
          'Публикация исчезнет из публичного списка и профиля автора.',
          'Связанные данные не удаляются; восстановление доступно только через admin API.',
        ]}
        confirmText="Удалить заявку"
        busy={postBusy}
        error={postDeleteError}
        onConfirm={handleDeletePost}
        onClose={() => {
          if (!postBusy) {
            setPostTarget(null);
            setPostDeleteError(null);
          }
        }}
      />

      <AdminConfirmDialog
        open={Boolean(reviewTarget)}
        title="Удалить отзыв?"
        description={
          reviewTarget
            ? `Отзыв от ${reviewTarget.fromUserName} с оценкой ${reviewTarget.rating}/5.`
            : undefined
        }
        consequences={[
          'Отзыв будет мягко удалён и перестанет отображаться у получателя.',
          'Восстановление отзыва через API не предусмотрено.',
        ]}
        confirmText="Удалить отзыв"
        busy={reviewBusy}
        error={reviewDeleteError}
        onConfirm={handleDeleteReview}
        onClose={() => {
          if (!reviewBusy) {
            setReviewTarget(null);
            setReviewDeleteError(null);
          }
        }}
      />

      <div className="admin-detail-footer">
        <Link to="/admin/users" className="admin-button admin-button--ghost">
          <ArrowLeft size={16} aria-hidden="true" />
          К списку пользователей
        </Link>
        <Link to={`/profile/${user.id}`} className="admin-button admin-button--ghost">
          <Gift size={16} aria-hidden="true" />
          Публичный профиль
        </Link>
      </div>
    </div>
  );
};
