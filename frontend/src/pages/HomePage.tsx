import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  Bookmark,
  ChevronRight,
  CircleCheckBig,
  ClipboardList,
  FilePlus,
  Flame,
  Heart,
  HeartHandshake,
  LayoutGrid,
  Leaf,
  MapPin,
  MessageCircle,
  Play,
  RefreshCw,
  Sparkles,
  Star,
  TreeDeciduous,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { statsApi, type Stats } from '../api/statsApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { CATEGORIES, CATEGORY_ICONS } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';
import { getRelativeTime } from '../utils/dateUtils';
import type { Post, UserPublic } from '../types';
import './HomePage.css';

const HOME_CATEGORIES = CATEGORIES.slice(0, 10);

const STATUS_CLASS: Record<Post['status'], string> = {
  OPEN: 'home-status--open',
  IN_PROGRESS: 'home-status--progress',
  COMPLETED: 'home-status--completed',
  CANCELLED: 'home-status--cancelled',
};

const AVATAR_COLORS = ['#8CCB55', '#A9D874', '#7BB98A', '#C8F56A', '#6FAE7A'];

const getInitials = (name: string): string =>
  name
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

const getAvatarColor = (name: string): string => {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
};

const shorten = (text: string, max: number): string => {
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max).trimEnd()}…` : text;
};

const formatNumber = (value: number): string => value.toLocaleString('ru-RU');

export const HomePage = () => {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [stats, setStats] = useState<Stats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState(false);

  const [posts, setPosts] = useState<Post[]>([]);
  const [postsLoading, setPostsLoading] = useState(true);
  const [postsError, setPostsError] = useState(false);

  const [community, setCommunity] = useState<UserPublic[]>([]);
  const [favorites, setFavorites] = useState<number[]>([]);
  const [howOpen, setHowOpen] = useState(false);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    setStatsError(false);
    try {
      const data = await statsApi.getStats();
      setStats(data);
    } catch (error) {
      console.error('Failed to load stats', error);
      setStatsError(true);
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadPosts = useCallback(async () => {
    setPostsLoading(true);
    setPostsError(false);
    try {
      // The backend already returns boosted posts first, then the freshest ones.
      const data = await postsApi.getAll({ page: 0, size: 4 });
      setPosts(data.content);
    } catch (error) {
      console.error('Failed to load posts', error);
      setPostsError(true);
    } finally {
      setPostsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
    loadPosts();
  }, [loadStats, loadPosts]);

  useEffect(() => {
    let cancelled = false;
    authApi
      .getAllUsers()
      .then((users) => {
        if (cancelled) return;
        const active = [...users]
          .sort((a, b) => (b.helpedCount || 0) - (a.helpedCount || 0))
          .slice(0, 5);
        setCommunity(active);
      })
      .catch((error) => {
        console.error('Failed to load community members', error);
        if (!cancelled) setCommunity([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!user) {
      setFavorites([]);
      return;
    }
    let cancelled = false;
    authApi
      .getFavorites(user.id)
      .then((ids) => {
        if (!cancelled) setFavorites(ids);
      })
      .catch((error) => {
        console.error('Failed to load favorites', error);
      });
    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!howOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setHowOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [howOpen]);

  const toggleFavorite = async (post: Post, event: React.MouseEvent | React.KeyboardEvent) => {
    event.preventDefault();
    event.stopPropagation();

    if (!user) {
      navigate('/login');
      return;
    }
    if (post.status !== 'OPEN' || user.id === post.userId) return;

    const wasFavorite = favorites.includes(post.id);
    setFavorites((current) =>
      wasFavorite ? current.filter((id) => id !== post.id) : [...current, post.id]
    );

    try {
      if (wasFavorite) {
        await authApi.removeFavorite(user.id, post.id);
      } else {
        await authApi.addFavorite(user.id, post.id);
      }
    } catch (error) {
      console.error('Failed to update favorite', error);
      setFavorites((current) =>
        wasFavorite ? [...current, post.id] : current.filter((id) => id !== post.id)
      );
      showToast('Не удалось обновить избранное', 'error');
    }
  };

  const statsItems = useMemo(
    () => [
      {
        key: 'users',
        icon: Users,
        value: stats?.totalUsers ?? 0,
        label: 'активных участников',
      },
      {
        key: 'helps',
        icon: CircleCheckBig,
        value: stats?.totalHelps ?? 0,
        label: 'актов помощи',
      },
      {
        key: 'posts',
        icon: ClipboardList,
        value: stats?.activePosts ?? 0,
        label: 'открытых заявок',
      },
    ],
    [stats]
  );

  return (
    <div className="home-page">
      <section className="home-hero" aria-label="HelpTree — добрые дела начинаются здесь">
        <div className="home-hero-photo" aria-hidden="true" />
        <div className="home-hero-shade" aria-hidden="true" />
        <div className="home-hero-inner">
          <div className="home-hero-content">
            <span className="home-hero-badge">
              <Heart size={14} strokeWidth={2.4} aria-hidden="true" />
              Вместе — мы можем больше
            </span>

            <h1 className="home-hero-title">
              Добрые дела
              <br />
              <span>начинаются здесь</span>
            </h1>

            <p className="home-hero-subtitle">
              Помогайте, получайте поддержку и находите людей рядом. Вместе мы создаём сильное и
              заботливое сообщество.
            </p>

            <div className="home-hero-actions">
              <Link to="/posts/new" className="home-btn home-btn--primary">
                Создать заявку
                <ArrowRight size={18} aria-hidden="true" />
              </Link>
              <button
                type="button"
                className="home-btn home-btn--ghost"
                onClick={() => setHowOpen(true)}
              >
                <span className="home-play-icon" aria-hidden="true">
                  <Play size={13} fill="currentColor" />
                </span>
                Как это работает
              </button>
            </div>

            <div className="home-hero-people">
              {community.length > 0 && (
                <div className="home-avatar-stack" aria-hidden="true">
                  {community.slice(0, 4).map((member) => (
                    <span
                      key={member.id}
                      className="home-stack-avatar"
                      style={{
                        background: member.avatarUrl
                          ? `url(${member.avatarUrl}) center / cover no-repeat`
                          : getAvatarColor(member.name),
                      }}
                    >
                      {!member.avatarUrl && getInitials(member.name)}
                    </span>
                  ))}
                </div>
              )}
              <span className="home-hero-people-text">
                <UserPlus size={15} aria-hidden="true" />
                Присоединяйтесь к сообществу HelpTree
              </span>
            </div>
          </div>
        </div>
      </section>

      <section className="home-stats" aria-label="Статистика HelpTree">
        <div className="home-stats-card">
          {statsLoading && (
            <>
              {[0, 1, 2].map((item) => (
                <div className="home-stat home-stat--loading" key={item}>
                  <span className="home-skeleton home-skeleton--icon" />
                  <span className="home-skeleton home-skeleton--value" />
                  <span className="home-skeleton home-skeleton--label" />
                </div>
              ))}
            </>
          )}

          {!statsLoading && statsError && (
            <div className="home-stats-error">
              <span>Статистика временно недоступна.</span>
              <button type="button" className="home-retry" onClick={loadStats}>
                <RefreshCw size={14} aria-hidden="true" />
                Повторить
              </button>
            </div>
          )}

          {!statsLoading && !statsError &&
            statsItems.map((item, index) => {
              const Icon = item.icon;
              return (
                <div className="home-stat" key={item.key}>
                  <span className="home-stat-icon">
                    <Icon size={22} aria-hidden="true" />
                  </span>
                  <span className="home-stat-value">{formatNumber(item.value)}</span>
                  <span className="home-stat-label">{item.label}</span>
                  {index < statsItems.length - 1 && <span className="home-stat-divider" />}
                </div>
              );
            })}
        </div>
      </section>

      <section className="home-section" id="categories">
        <div className="home-section-head">
          <div className="home-section-heading">
            <span className="home-section-icon">
              <LayoutGrid size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className="home-section-title">Категории помощи</h2>
              <p className="home-section-note">
                Выберите направление — каталог откроется с нужным фильтром.
              </p>
            </div>
          </div>
          <Link to="/posts" className="home-section-link">
            Все категории
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>

        <div className="home-categories">
          {HOME_CATEGORIES.map((category) => {
            const Icon = CATEGORY_ICONS[category] || Leaf;
            return (
              <Link
                key={category}
                to={`/posts?category=${encodeURIComponent(category)}`}
                className="home-category-card"
              >
                <span className="home-category-icon">
                  <Icon size={26} strokeWidth={1.7} aria-hidden="true" />
                </span>
                <span className="home-category-name">{category}</span>
                <ChevronRight className="home-category-arrow" size={16} aria-hidden="true" />
              </Link>
            );
          })}
        </div>
      </section>

      <section className="home-section" id="popular">
        <div className="home-section-head">
          <div className="home-section-heading">
            <span className="home-section-icon home-section-icon--flame">
              <Flame size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className="home-section-title">Популярные заявки</h2>
              <p className="home-section-note">
                Свежие просьбы о помощи и заявки, поднятые в топ.
              </p>
            </div>
          </div>
          <Link to="/posts" className="home-section-link">
            Все заявки
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>

        {postsLoading && (
          <div className="home-posts-grid" aria-label="Загрузка заявок">
            {[0, 1, 2, 3].map((item) => (
              <div className="home-post-card home-post-card--skeleton" key={item}>
                <span className="home-skeleton home-skeleton--media" />
                <span className="home-post-skeleton-body">
                  <span className="home-skeleton home-skeleton--status" />
                  <span className="home-skeleton home-skeleton--title" />
                  <span className="home-skeleton home-skeleton--line" />
                  <span className="home-skeleton home-skeleton--line home-skeleton--line-short" />
                  <span className="home-skeleton home-skeleton--meta" />
                </span>
              </div>
            ))}
          </div>
        )}

        {!postsLoading && postsError && (
          <div className="home-data-state home-data-state--error">
            <span className="home-data-state-icon home-data-state-icon--error">
              <X size={22} aria-hidden="true" />
            </span>
            <div>
              <h3>Не удалось загрузить заявки</h3>
              <p>Проверьте соединение и попробуйте ещё раз.</p>
            </div>
            <button type="button" className="home-retry home-retry--light" onClick={loadPosts}>
              <RefreshCw size={14} aria-hidden="true" />
              Повторить
            </button>
          </div>
        )}

        {!postsLoading && !postsError && posts.length === 0 && (
          <div className="home-data-state home-data-state--empty">
            <span className="home-data-state-icon">
              <Sparkles size={22} aria-hidden="true" />
            </span>
            <div>
              <h3>Пока нет заявок</h3>
              <p>Станьте первым, кто попросит о помощи или предложит её сообществу.</p>
            </div>
            <Link to="/posts/new" className="home-btn home-btn--dark">
              Создать заявку
              <ArrowRight size={16} aria-hidden="true" />
            </Link>
          </div>
        )}

        {!postsLoading && !postsError && posts.length > 0 && (
          <div className="home-posts-grid">
            {posts.map((post) => {
              const CategoryIcon = CATEGORY_ICONS[post.category] || Leaf;
              const canFavorite = post.status === 'OPEN' && (!user || user.id !== post.userId);
              const isFavorite = favorites.includes(post.id);

              return (
                <Link
                  key={post.id}
                  to={`/posts/${post.id}`}
                  className="home-post-card"
                  aria-label={`Заявка: ${post.title}`}
                >
                  <span className="home-post-media">
                    <CategoryIcon size={30} strokeWidth={1.6} aria-hidden="true" />
                    {post.imageUrls && post.imageUrls.length > 0 && (
                      <img
                        src={post.imageUrls[0]}
                        alt=""
                        loading="lazy"
                        onError={(event) => {
                          event.currentTarget.style.display = 'none';
                        }}
                      />
                    )}
                    {post.boosted && (
                      <span className="home-post-boosted" title="Заявка поднята в топ">
                        <Flame size={11} aria-hidden="true" />
                        Топ
                      </span>
                    )}
                  </span>

                  <span className="home-post-body">
                    <span className="home-post-top">
                      <span className={`home-status ${STATUS_CLASS[post.status]}`}>
                        {getStatusLabel(post.status)}
                      </span>
                      {canFavorite && (
                        <span
                          className={`home-fav ${isFavorite ? 'home-fav--active' : ''}`}
                          role="button"
                          tabIndex={0}
                          aria-label={isFavorite ? 'Убрать из избранного' : 'В избранное'}
                          title={isFavorite ? 'Убрать из избранного' : 'В избранное'}
                          onClick={(event) => toggleFavorite(post, event)}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              toggleFavorite(post, event);
                            }
                          }}
                        >
                          <Bookmark
                            size={16}
                            fill={isFavorite ? 'currentColor' : 'none'}
                            aria-hidden="true"
                          />
                        </span>
                      )}
                    </span>

                    <span className="home-post-title">{post.title}</span>
                    <span className="home-post-description">{shorten(post.description, 110)}</span>

                    <span className="home-post-tags">
                      <span className="home-post-tag">
                        <CategoryIcon size={12} aria-hidden="true" />
                        {post.category}
                      </span>
                      {post.userCity && (
                        <span className="home-post-tag">
                          <MapPin size={12} aria-hidden="true" />
                          {post.userCity}
                        </span>
                      )}
                    </span>

                    <span className="home-post-footer">
                      <span className="home-post-author">
                        <span
                          className="home-post-avatar"
                          style={{
                            background: post.authorAvatarUrl
                              ? `url(${post.authorAvatarUrl}) center / cover no-repeat`
                              : getAvatarColor(post.authorName),
                          }}
                        >
                          {!post.authorAvatarUrl && getInitials(post.authorName)}
                        </span>
                        <span className="home-post-author-name">{post.authorName}</span>
                        {post.authorRating > 0 && (
                          <span className="home-post-rating">
                            <Star size={11} fill="currentColor" aria-hidden="true" />
                            {post.authorRating.toFixed(1)}
                          </span>
                        )}
                      </span>
                      <span className="home-post-date">{getRelativeTime(post.createdAt)}</span>
                    </span>
                  </span>
                </Link>
              );
            })}
          </div>
        )}
      </section>

      <section className="home-community" aria-label="Сообщество HelpTree">
        <span className="home-community-leaves" aria-hidden="true">
          <Leaf size={190} strokeWidth={0.8} />
          <Leaf size={120} strokeWidth={0.8} />
        </span>
        <span className="home-community-icon">
          <TreeDeciduous size={42} strokeWidth={1.4} aria-hidden="true" />
        </span>
        <div className="home-community-text">
          <h2>Помогая другим, вы делаете мир теплее</h2>
          <p>Присоединяйтесь к сообществу HelpTree и будьте частью добрых перемен!</p>
        </div>
        <Link to={user ? '/users' : '/register'} className="home-btn home-btn--primary">
          Стать участником
          <ArrowRight size={17} aria-hidden="true" />
        </Link>
      </section>

      <section className="home-join" aria-label="Присоединиться к HelpTree">
        <div className="home-join-text">
          <span className="home-join-eyebrow">
            <HeartHandshake size={16} aria-hidden="true" />
            HelpTree
          </span>
          <h2>{user ? 'Продолжайте делать добрые дела' : 'Готовы присоединиться?'}</h2>
          <p>
            {user
              ? 'Создайте заявку или найдите того, кому нужна помощь рядом.'
              : 'Зарегистрируйтесь, чтобы создавать заявки, откликаться и находить людей рядом.'}
          </p>
        </div>
        <div className="home-join-actions">
          <Link
            to={user ? '/posts/new' : '/register'}
            className="home-btn home-btn--primary"
          >
            {user ? 'Создать заявку' : 'Стать участником'}
            <ArrowRight size={17} aria-hidden="true" />
          </Link>
          <Link to="/posts" className="home-btn home-btn--outline">
            Смотреть заявки
          </Link>
        </div>
      </section>

      {howOpen && (
        <div
          className="home-modal-backdrop"
          role="presentation"
          onClick={() => setHowOpen(false)}
        >
          <div
            className="home-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="how-it-works-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="home-modal-head">
              <div>
                <span className="home-modal-eyebrow">
                  <Sparkles size={14} aria-hidden="true" />
                  Просто и по-человечески
                </span>
                <h2 id="how-it-works-title">Как это работает</h2>
              </div>
              <button
                type="button"
                className="home-modal-close"
                onClick={() => setHowOpen(false)}
                aria-label="Закрыть"
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <ol className="home-steps">
              <li>
                <span className="home-step-icon">
                  <FilePlus size={19} aria-hidden="true" />
                </span>
                <div>
                  <h3>Опишите, какая помощь нужна</h3>
                  <p>Создайте заявку: категория, город и детали помогут быстрее найти отклик.</p>
                </div>
              </li>
              <li>
                <span className="home-step-icon">
                  <Users size={19} aria-hidden="true" />
                </span>
                <div>
                  <h3>Найдите помощника</h3>
                  <p>Участники видят заявки в каталоге и могут откликнуться на вашу просьбу.</p>
                </div>
              </li>
              <li>
                <span className="home-step-icon">
                  <MessageCircle size={19} aria-hidden="true" />
                </span>
                <div>
                  <h3>Договоритесь в чате</h3>
                  <p>Обсудите детали, время и место прямо во встроенном чате HelpTree.</p>
                </div>
              </li>
              <li>
                <span className="home-step-icon">
                  <CircleCheckBig size={19} aria-hidden="true" />
                </span>
                <div>
                  <h3>Подтвердите помощь</h3>
                  <p>После выполнения заявка закрывается, а рейтинг участника растёт.</p>
                </div>
              </li>
            </ol>

            <div className="home-modal-actions">
              <Link to={user ? '/posts/new' : '/register'} className="home-btn home-btn--primary">
                {user ? 'Создать заявку' : 'Стать участником'}
                <ArrowRight size={17} aria-hidden="true" />
              </Link>
              <button
                type="button"
                className="home-btn home-btn--outline"
                onClick={() => setHowOpen(false)}
              >
                Понятно
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
