import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowUpRight,
  CalendarDays,
  Check,
  Clock3,
  FileText,
  HandHeart,
  MapPin,
  MessageCircle,
  Pencil,
  RefreshCw,
  ShieldAlert,
  Star,
  Trophy,
  Upload,
  X,
} from 'lucide-react';
import { authApi } from '../api/authApi';
import { achievementApi, type Achievement } from '../api/achievementApi';
import { activityApi, type Activity } from '../api/activityApi';
import { chatApi } from '../api/chatApi';
import { postsApi } from '../api/postsApi';
import { reviewApi } from '../api/reviewApi';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { Avatar } from '../components/Avatar';
import { getStatusLabel } from '../components/StatusBadge';
import { formatDate } from '../utils/dateUtils';
import { geocodeCity } from '../utils/geocoding';
import type { Post, Review, User, UserPublic } from '../types';
import './ProfilePage.css';

type ProfileUser = User | UserPublic;
type SectionKey = 'posts' | 'achievements' | 'activities' | 'reviews';
type SectionState = Record<SectionKey, { loading: boolean; error: boolean }>;

const EMPTY_SECTIONS: SectionState = {
  posts: { loading: true, error: false },
  achievements: { loading: true, error: false },
  activities: { loading: true, error: false },
  reviews: { loading: true, error: false },
};

const PROFILE_POST_PAGE_SIZE = 6;

const getErrorStatus = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } })?.response?.status;

const getInitials = (name: string): string =>
  name
    .trim()
    .split(/\s+/)
    .map((part) => part[0] || '')
    .join('')
    .slice(0, 2)
    .toLocaleUpperCase('ru');

export const ProfilePage = () => {
  const { userId: routeUserId } = useParams<{ userId?: string }>();
  const navigate = useNavigate();
  const { user: currentUser, setUser } = useAuth();
  const { showToast } = useToast();
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const parsedUserId = routeUserId ? Number(routeUserId) : undefined;
  const invalidUserId =
    routeUserId !== undefined &&
    (!Number.isSafeInteger(parsedUserId) || (parsedUserId ?? 0) <= 0);
  const isOwnProfile =
    !routeUserId || (parsedUserId !== undefined && parsedUserId === currentUser?.id);

  const [profileUser, setProfileUser] = useState<ProfileUser | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<
    'not-found' | 'access' | 'general' | null
  >(null);
  const [sections, setSections] = useState<SectionState>(EMPTY_SECTIONS);
  const [posts, setPosts] = useState<Post[]>([]);
  const [totalPosts, setTotalPosts] = useState(0);
  const [postPage, setPostPage] = useState(0);
  const [loadingMorePosts, setLoadingMorePosts] = useState(false);
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [isEditing, setIsEditing] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [startingChat, setStartingChat] = useState(false);
  const [editForm, setEditForm] = useState({
    name: '',
    email: '',
    phone: '',
    city: '',
    birthDate: '',
  });

  useEffect(() => {
    let cancelled = false;

    const loadProfile = async () => {
      setProfileLoading(true);
      setProfileError(null);
      setProfileUser(null);

      if (invalidUserId) {
        setProfileError('not-found');
        setProfileLoading(false);
        return;
      }

      try {
        const viewingOwnProfile = !routeUserId || parsedUserId === currentUser?.id;
        const userData = viewingOwnProfile
          ? await authApi.getCurrentUser()
          : await authApi.getUserById(parsedUserId!);
        if (cancelled) return;

        setProfileUser(userData);
        if (viewingOwnProfile) {
          const ownProfile = userData as User;
          setEditForm({
            name: ownProfile.name || '',
            email: ownProfile.email || '',
            phone: ownProfile.phone || '',
            city: ownProfile.city || '',
            birthDate: ownProfile.birthDate?.split('T')[0] || '',
          });
        }

        setSections({
          posts: { loading: true, error: false },
          achievements: { loading: true, error: false },
          activities: { loading: true, error: false },
          reviews: { loading: true, error: false },
        });

        const targetId = userData.id;
        const [postResult, achievementResult, activityResult, reviewResult] =
          await Promise.allSettled([
            postsApi.getAll({ userId: targetId, size: PROFILE_POST_PAGE_SIZE, page: 0 }),
            achievementApi.getAllAchievements(targetId),
            activityApi.getAllActivities(targetId, { size: 20 }),
            reviewApi.getAllByUser(targetId),
          ]);

        if (cancelled) return;

        if (postResult.status === 'fulfilled') {
          setPosts(postResult.value.content || []);
          setTotalPosts(postResult.value.totalElements || 0);
          setPostPage(0);
        }
        if (achievementResult.status === 'fulfilled') {
          setAchievements(achievementResult.value.filter((item) => item.isEarned !== false));
        }
        if (activityResult.status === 'fulfilled') {
          setActivities(activityResult.value.content);
        }
        if (reviewResult.status === 'fulfilled') setReviews(reviewResult.value);

        setSections({
          posts: { loading: false, error: postResult.status === 'rejected' },
          achievements: { loading: false, error: achievementResult.status === 'rejected' },
          activities: { loading: false, error: activityResult.status === 'rejected' },
          reviews: { loading: false, error: reviewResult.status === 'rejected' },
        });
      } catch (error) {
        if (cancelled) return;
        const status = getErrorStatus(error);
        setProfileError(
          status === 404 ? 'not-found' : status === 401 || status === 403 ? 'access' : 'general'
        );
      } finally {
        if (!cancelled) setProfileLoading(false);
      }
    };

    void loadProfile();
    return () => {
      cancelled = true;
    };
  }, [currentUser?.id, invalidUserId, parsedUserId, routeUserId]);

  // Visibility is enforced by the server; do not filter activity in the browser.
  const publicActivities = activities;

  const handleStartChat = useCallback(async () => {
    if (!profileUser || isOwnProfile) return;
    if (!currentUser) {
      navigate('/login');
      return;
    }

    setStartingChat(true);
    try {
      const chat = await chatApi.createChat({ participantId: profileUser.id });
      navigate(`/chats/${chat.id}`);
    } catch {
      showToast('Не удалось открыть диалог. Попробуйте ещё раз.', 'error');
    } finally {
      setStartingChat(false);
    }
  }, [currentUser, isOwnProfile, navigate, profileUser, showToast]);

  const handleSaveProfile = async () => {
    if (!currentUser) return;
    setSavingProfile(true);
    try {
      let latitude: number | undefined;
      let longitude: number | undefined;
      if (editForm.city.trim()) {
        const coords = await geocodeCity(editForm.city.trim());
        if (coords) {
          latitude = coords.lat;
          longitude = coords.lng;
        }
      }

      const updatedUser = await authApi.updateProfile(currentUser.id, {
        name: editForm.name.trim(),
        email: editForm.email.trim(),
        phone: editForm.phone.trim() || undefined,
        city: editForm.city.trim() || undefined,
        birthDate: editForm.birthDate
          ? new Date(editForm.birthDate).toISOString()
          : null,
        latitude,
        longitude,
      });
      setProfileUser(updatedUser);
      setUser(updatedUser);
      setIsEditing(false);
      showToast('Профиль обновлён', 'success');
    } catch {
      showToast('Не удалось сохранить профиль. Проверьте данные и повторите попытку.', 'error');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleAvatarChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !currentUser || !isOwnProfile) return;

    setUploadingAvatar(true);
    try {
      const avatarUrl = await authApi.uploadAvatar(currentUser.id, file);
      const updatedUser = { ...currentUser, avatarUrl };
      setProfileUser(updatedUser);
      setUser(updatedUser);
      showToast('Фото профиля обновлено', 'success');
    } catch {
      showToast('Не удалось загрузить фото профиля.', 'error');
    } finally {
      setUploadingAvatar(false);
      event.target.value = '';
    }
  };

  const handleLoadMorePosts = async () => {
    if (!profileUser || loadingMorePosts) return;
    const nextPage = postPage + 1;
    setLoadingMorePosts(true);
    try {
      const result = await postsApi.getAll({
        userId: profileUser.id,
        size: PROFILE_POST_PAGE_SIZE,
        page: nextPage,
      });
      setPosts((current) => [...current, ...result.content]);
      setTotalPosts(result.totalElements);
      setPostPage(nextPage);
    } catch {
      showToast('Не удалось загрузить следующие заявки.', 'error');
    } finally {
      setLoadingMorePosts(false);
    }
  };

  const handleRetrySection = async (section: SectionKey) => {
    if (!profileUser) return;
    setSections((current) => ({
      ...current,
      [section]: { loading: true, error: false },
    }));

    try {
      if (section === 'posts') {
        const result = await postsApi.getAll({
          userId: profileUser.id,
          size: PROFILE_POST_PAGE_SIZE,
          page: 0,
        });
        setPosts(result.content || []);
        setTotalPosts(result.totalElements || 0);
        setPostPage(0);
      } else if (section === 'achievements') {
        const result = await achievementApi.getAllAchievements(profileUser.id);
        setAchievements(result.filter((item) => item.isEarned !== false));
      } else if (section === 'activities') {
        setActivities(
          (await activityApi.getAllActivities(profileUser.id, { size: 20 })).content
        );
      } else {
        setReviews(await reviewApi.getAllByUser(profileUser.id));
      }
      setSections((current) => ({
        ...current,
        [section]: { loading: false, error: false },
      }));
    } catch {
      setSections((current) => ({
        ...current,
        [section]: { loading: false, error: true },
      }));
    }
  };

  if (profileLoading) {
    return (
      <main className="public-profile">
        <div className="profile-loading" role="status" aria-label="Загрузка профиля">
          <span className="profile-spinner" />
          <span>Загружаем профиль…</span>
        </div>
      </main>
    );
  }

  if (!profileUser || profileError) {
    const missing = profileError === 'not-found';
    const noAccess = profileError === 'access';
    return (
      <main className="public-profile">
        <section className="profile-state">
          <span className="profile-state-icon">
            {missing || noAccess ? <ShieldAlert size={25} /> : <RefreshCw size={25} />}
          </span>
          <h1>
            {missing
              ? 'Пользователь не найден'
              : noAccess
              ? 'Нет доступа к профилю'
              : 'Не удалось загрузить профиль'}
          </h1>
          <p>
            {missing
              ? 'Проверьте ссылку или вернитесь к списку участников.'
              : noAccess
              ? 'Войдите в аккаунт или обратитесь к администратору, если считаете, что это ошибка.'
              : 'Проверьте соединение и попробуйте загрузить страницу ещё раз.'}
          </p>
          <div className="profile-state-actions">
            {!missing && !noAccess && (
              <button
                type="button"
                className="profile-button profile-button--primary"
                onClick={() => window.location.reload()}
              >
                <RefreshCw size={15} /> Повторить
              </button>
            )}
            {noAccess && !currentUser && (
              <Link className="profile-button profile-button--primary" to="/login">
                Войти
              </Link>
            )}
            <Link className="profile-button profile-button--secondary" to="/users">
              К участникам
            </Link>
          </div>
        </section>
      </main>
    );
  }

  const avatarUrl = profileUser.avatarUrl;
  const createdAt = isOwnProfile ? (profileUser as User).createdAt : undefined;
  const rating =
    typeof profileUser.rating === 'number' && Number.isFinite(profileUser.rating)
      ? profileUser.rating
      : null;
  const helpedCount =
    typeof profileUser.helpedCount === 'number' && Number.isFinite(profileUser.helpedCount)
      ? profileUser.helpedCount
      : null;
  const hasMorePosts = posts.length < totalPosts;

  return (
    <main className="public-profile">
      <nav className="profile-breadcrumb" aria-label="Навигационная цепочка">
        <Link to="/">Главная</Link>
        <span aria-hidden="true">/</span>
        <Link to="/users">Участники</Link>
        <span aria-hidden="true">/</span>
        <span>{profileUser.name}</span>
      </nav>

      <section className="profile-hero" aria-labelledby="profile-name">
        <div className="profile-hero-glow" aria-hidden="true" />
        <div className="profile-hero-main">
          <div className="profile-avatar-frame">
            <Avatar
              name={profileUser.name}
              avatarUrl={avatarUrl}
              size="xlarge"
            />
            {isOwnProfile && (
              <button
                type="button"
                className="profile-avatar-edit"
                onClick={() => avatarInputRef.current?.click()}
                disabled={uploadingAvatar}
                aria-label="Изменить фото профиля"
                title="Изменить фото профиля"
              >
                {uploadingAvatar ? <span className="profile-spinner" /> : <Upload size={15} />}
              </button>
            )}
            {isOwnProfile && (
              <input
                ref={avatarInputRef}
                className="profile-file-input"
                type="file"
                accept="image/jpeg,image/png,image/gif"
                onChange={handleAvatarChange}
                aria-label="Выберите фото профиля"
              />
            )}
          </div>
          <div className="profile-identity">
            <span className="profile-eyebrow">Сообщество HelpTree</span>
            <h1 id="profile-name">{profileUser.name || getInitials('Участник')}</h1>
            <div className="profile-meta">
              {createdAt && (
                <span>
                  <CalendarDays size={14} aria-hidden="true" />
                  В сообществе с {formatDate(createdAt)}
                </span>
              )}
              {isOwnProfile && (profileUser as User).city && (
                <span>
                  <MapPin size={14} aria-hidden="true" />
                  {(profileUser as User).city}
                </span>
              )}
            </div>
          </div>
          <div className="profile-actions">
            {isOwnProfile ? (
              <button
                type="button"
                className="profile-button profile-button--secondary"
                onClick={() => setIsEditing((editing) => !editing)}
              >
                {isEditing ? <X size={15} /> : <Pencil size={15} />}
                {isEditing ? 'Закрыть' : 'Редактировать'}
              </button>
            ) : (
              <button
                type="button"
                className="profile-button profile-button--primary"
                onClick={handleStartChat}
                disabled={startingChat}
              >
                <MessageCircle size={16} />
                {startingChat ? 'Открываем…' : currentUser ? 'Написать' : 'Войти и написать'}
              </button>
            )}
          </div>
        </div>

        {isEditing && isOwnProfile && (
          <div className="profile-edit-panel">
            <div className="profile-edit-heading">
              <div>
                <span className="profile-eyebrow">Настройки владельца</span>
                <h2>Личные данные</h2>
              </div>
              <p>Email, телефон и дата рождения доступны только вам.</p>
            </div>
            <div className="profile-edit-grid">
              <label>
                Имя
                <input
                  value={editForm.name}
                  onChange={(event) =>
                    setEditForm((form) => ({ ...form, name: event.target.value }))
                  }
                  autoComplete="name"
                />
              </label>
              <label>
                Email
                <input
                  type="email"
                  value={editForm.email}
                  onChange={(event) =>
                    setEditForm((form) => ({ ...form, email: event.target.value }))
                  }
                  autoComplete="email"
                />
              </label>
              <label>
                Телефон
                <input
                  type="tel"
                  value={editForm.phone}
                  onChange={(event) =>
                    setEditForm((form) => ({ ...form, phone: event.target.value }))
                  }
                  autoComplete="tel"
                />
              </label>
              <label>
                Город
                <input
                  value={editForm.city}
                  onChange={(event) =>
                    setEditForm((form) => ({ ...form, city: event.target.value }))
                  }
                  autoComplete="address-level2"
                />
              </label>
              <label>
                Дата рождения
                <input
                  type="date"
                  value={editForm.birthDate}
                  onChange={(event) =>
                    setEditForm((form) => ({ ...form, birthDate: event.target.value }))
                  }
                />
              </label>
            </div>
            <div className="profile-edit-actions">
              <button
                type="button"
                className="profile-button profile-button--primary"
                onClick={handleSaveProfile}
                disabled={savingProfile}
              >
                <Check size={15} />
                {savingProfile ? 'Сохраняем…' : 'Сохранить'}
              </button>
              <button
                type="button"
                className="profile-button profile-button--secondary"
                onClick={() => setIsEditing(false)}
              >
                Отмена
              </button>
            </div>
          </div>
        )}

        <div className="profile-stats" aria-label="Статистика участника">
          {rating !== null && (
            <div className="profile-stat">
              <span className="profile-stat-icon"><Star size={16} /></span>
              <strong>{rating.toFixed(1)}</strong>
              <span>рейтинг</span>
            </div>
          )}
          {helpedCount !== null && (
            <div className="profile-stat">
              <span className="profile-stat-icon"><HandHeart size={17} /></span>
              <strong>{helpedCount}</strong>
              <span>помощь оказана</span>
            </div>
          )}
          <div className="profile-stat">
            <span className="profile-stat-icon"><FileText size={16} /></span>
            <strong>{sections.posts.loading ? '…' : sections.posts.error ? '—' : totalPosts}</strong>
            <span>заявки</span>
          </div>
        </div>
      </section>

      <div className="profile-content-grid">
        <section className="profile-section profile-posts" aria-labelledby="profile-posts-title">
          <div className="profile-section-heading">
            <div>
              <span className="profile-eyebrow">Публикации</span>
              <h2 id="profile-posts-title">Заявки участника</h2>
            </div>
            {!sections.posts.loading && !sections.posts.error && (
              <span className="profile-section-count">{totalPosts}</span>
            )}
          </div>
          {sections.posts.loading ? (
            <div className="profile-post-list" aria-label="Загрузка заявок">
              {[0, 1, 2].map((index) => <div className="profile-skeleton-post" key={index} />)}
            </div>
          ) : sections.posts.error ? (
            <SectionError onRetry={() => void handleRetrySection('posts')} />
          ) : posts.length === 0 ? (
            <div className="profile-empty">Публичных заявок пока нет.</div>
          ) : (
            <>
              <div className="profile-post-list">
                {posts.map((post) => (
                  <Link className="profile-post-card" to={`/posts/${post.id}`} key={post.id}>
                    {post.imageUrls?.[0] ? (
                      <img src={post.imageUrls[0]} alt="" loading="lazy" />
                    ) : (
                      <span className="profile-post-placeholder"><HandHeart size={20} /></span>
                    )}
                    <span className="profile-post-copy">
                      <span className="profile-post-category">{post.category}</span>
                      <strong>{post.title}</strong>
                      <span className="profile-post-description">{post.description}</span>
                      <span className="profile-post-meta">
                        <span className={`profile-status profile-status--${post.status.toLowerCase()}`}>
                          {getStatusLabel(post.status)}
                        </span>
                        <span>{formatDate(post.createdAt)}</span>
                      </span>
                    </span>
                    <ArrowUpRight className="profile-post-arrow" size={17} aria-hidden="true" />
                  </Link>
                ))}
              </div>
              {hasMorePosts && (
                <button
                  type="button"
                  className="profile-more-button"
                  onClick={() => void handleLoadMorePosts()}
                  disabled={loadingMorePosts}
                >
                  {loadingMorePosts ? 'Загружаем…' : 'Показать ещё заявки'}
                </button>
              )}
            </>
          )}
        </section>

        <div className="profile-side-sections">
          <section className="profile-section" aria-labelledby="profile-achievements-title">
            <div className="profile-section-heading">
              <div>
                <span className="profile-eyebrow">Путь помощи</span>
                <h2 id="profile-achievements-title">Достижения</h2>
              </div>
              <Trophy size={18} className="profile-section-mark" />
            </div>
            {sections.achievements.loading ? (
              <div className="profile-loading-inline"><span className="profile-spinner" /> Загружаем…</div>
            ) : sections.achievements.error ? (
              <SectionError onRetry={() => void handleRetrySection('achievements')} />
            ) : achievements.length === 0 ? (
              <div className="profile-empty profile-empty--compact">Достижений пока нет.</div>
            ) : (
              <div className="profile-achievement-list">
                {achievements.map((achievement) => (
                  <article className="profile-achievement" key={achievement.id ?? achievement.type}>
                    <span className="profile-achievement-emoji" aria-hidden="true">
                      {achievement.emoji || '✦'}
                    </span>
                    <span>
                      <strong>{achievement.name}</strong>
                      <small>{achievement.description}</small>
                      {achievement.earnedAt && (
                        <small className="profile-earned-date">
                          Получено {formatDate(achievement.earnedAt)}
                        </small>
                      )}
                    </span>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="profile-section" aria-labelledby="profile-activity-title">
            <div className="profile-section-heading">
              <div>
                <span className="profile-eyebrow">Недавнее</span>
                <h2 id="profile-activity-title">Активность</h2>
              </div>
              <Clock3 size={18} className="profile-section-mark" />
            </div>
            {sections.activities.loading ? (
              <div className="profile-loading-inline"><span className="profile-spinner" /> Загружаем…</div>
            ) : sections.activities.error ? (
              <SectionError onRetry={() => void handleRetrySection('activities')} />
            ) : publicActivities.length === 0 ? (
              <div className="profile-empty profile-empty--compact">Публичной активности пока нет.</div>
            ) : (
              <ol className="profile-activity-list">
                {publicActivities.slice(0, 8).map((activity, index) => (
                  <li className="profile-activity" key={`${activity.type}-${activity.timestamp}-${index}`}>
                    <span className="profile-activity-icon" aria-hidden="true">
                      {activity.emoji || '•'}
                    </span>
                    <span className="profile-activity-copy">
                      <strong>{activity.title}</strong>
                      {activity.description && <small>{activity.description}</small>}
                      <time dateTime={activity.timestamp}>{formatDate(activity.timestamp)}</time>
                    </span>
                    {activity.relatedPostId && (
                      <Link
                        to={`/posts/${activity.relatedPostId}`}
                        className="profile-activity-link"
                        aria-label={`Открыть заявку ${activity.relatedPostTitle || ''}`}
                      >
                        <ArrowUpRight size={15} />
                      </Link>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>

      {isOwnProfile && (
        <section className="profile-section profile-analytics" aria-labelledby="profile-analytics-title">
          <div className="profile-section-heading">
            <div>
              <span className="profile-eyebrow">Ваш вклад</span>
              <h2 id="profile-analytics-title">Моя статистика</h2>
              <p className="profile-analytics-description">
                Данные за всё время из профиля и загруженных разделов.
              </p>
            </div>
            <Link to="/activity" className="profile-analytics-link">
              История активности <ArrowUpRight size={15} aria-hidden="true" />
            </Link>
          </div>
          <div className="profile-analytics-grid">
            <article className="profile-analytics-metric">
              <HandHeart size={18} aria-hidden="true" />
              <span>Помощь оказана</span>
              <strong>{helpedCount === null ? '—' : helpedCount.toLocaleString('ru-RU')}</strong>
              <small>Показатель из профиля</small>
            </article>
            <article className="profile-analytics-metric">
              <FileText size={18} aria-hidden="true" />
              <span>Создано заявок</span>
              <strong>
                {sections.posts.loading
                  ? '…'
                  : sections.posts.error
                    ? '—'
                    : totalPosts.toLocaleString('ru-RU')}
              </strong>
              <small>Всего по данным каталога</small>
            </article>
            <article className="profile-analytics-metric">
              <Trophy size={18} aria-hidden="true" />
              <span>Получено достижений</span>
              <strong>
                {sections.achievements.loading
                  ? '…'
                  : sections.achievements.error
                    ? '—'
                    : achievements.length.toLocaleString('ru-RU')}
              </strong>
              <small>Только уже полученные</small>
            </article>
            <article className="profile-analytics-metric">
              <MessageCircle size={18} aria-hidden="true" />
              <span>Получено отзывов</span>
              <strong>
                {sections.reviews.loading
                  ? '…'
                  : sections.reviews.error
                    ? '—'
                    : reviews.length.toLocaleString('ru-RU')}
              </strong>
              <small>По данным раздела отзывов</small>
            </article>
          </div>
        </section>
      )}

      <section className="profile-section profile-reviews" aria-labelledby="profile-reviews-title">
        <div className="profile-section-heading">
          <div>
            <span className="profile-eyebrow">Обратная связь</span>
            <h2 id="profile-reviews-title">Отзывы</h2>
          </div>
          {!sections.reviews.loading && !sections.reviews.error && (
            <span className="profile-section-count">{reviews.length}</span>
          )}
        </div>
        {sections.reviews.loading ? (
          <div className="profile-loading-inline"><span className="profile-spinner" /> Загружаем…</div>
        ) : sections.reviews.error ? (
          <SectionError onRetry={() => void handleRetrySection('reviews')} />
        ) : reviews.length === 0 ? (
          <div className="profile-empty">Отзывов пока нет.</div>
        ) : (
          <div className="profile-review-grid">
            {reviews.map((review) => (
              <article className="profile-review" key={review.id}>
                <div className="profile-review-top">
                  <Link to={`/profile/${review.fromUserId}`} className="profile-review-author">
                    <Avatar name={review.fromUserName} size="small" />
                    <strong>{review.fromUserName}</strong>
                  </Link>
                  {typeof review.rating === 'number' && (
                    <span className="profile-review-rating">
                      <Star size={14} fill="currentColor" />
                      {review.rating}
                    </span>
                  )}
                </div>
                {review.comment?.trim() ? (
                  <p>{review.comment.trim()}</p>
                ) : (
                  <p className="profile-review-no-comment">Текст отзыва не оставлен.</p>
                )}
                <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
};

const SectionError = ({ onRetry }: { onRetry: () => void }) => (
  <div className="profile-section-error" role="status">
    <span>Не удалось загрузить этот раздел.</span>
    <button type="button" onClick={onRetry}>Повторить</button>
  </div>
);

export default ProfilePage;
