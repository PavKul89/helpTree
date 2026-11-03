import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowUpRight,
  CalendarDays,
  FileText,
  HandHeart,
  Plus,
  RefreshCw,
  Trash2,
  TreeDeciduous,
  Zap,
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { Modal } from '../components/Modal';
import { formatDate, getRelativeTime } from '../utils/dateUtils';
import { getStatusColor, getStatusLabel } from '../utils/statusHelpers';
import { createIdempotencyKey } from '../utils/idempotency';
import type { Help, Post } from '../types';
import './MyOrdersPage.css';

type OrdersTab = 'posts' | 'helps';

interface StatItem {
  label: string;
  value: number;
  Icon: typeof FileText;
}

const isHelpPage = (value: unknown): value is { content: Help[] } =>
  typeof value === 'object' &&
  value !== null &&
  'content' in value &&
  Array.isArray(value.content);

const getBoostTimeRemaining = (post: Post): string => {
  if (!post.boostedUntil) return '';
  const diff = new Date(post.boostedUntil).getTime() - Date.now();
  if (diff <= 0) return '';
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  return hours > 0 ? `${hours} ч ${minutes} мин` : `${minutes} мин`;
};

const isBoosted = (post: Post): boolean =>
  Boolean(post.boostedUntil && new Date(post.boostedUntil).getTime() > Date.now());

const StatusPill = ({ status, type = 'post' }: { status: string; type?: 'post' | 'help' }) => {
  const color = getStatusColor(status, type);
  return (
    <span
      className="my-orders-status"
      style={{ color, borderColor: `${color}66`, backgroundColor: `${color}1A` }}
    >
      <span className="my-orders-status-dot" style={{ backgroundColor: color }} aria-hidden="true" />
      {getStatusLabel(status, type)}
    </span>
  );
};

const LoadingState = () => (
  <div className="my-orders-loading" role="status" aria-label="Загружаем ваши данные">
    <div className="my-orders-stats-skeleton" aria-hidden="true">
      {[0, 1, 2, 3, 4].map((item) => <span key={item} />)}
    </div>
    <div className="my-orders-skeleton-list" aria-hidden="true">
      {[0, 1, 2].map((item) => (
        <div className="my-orders-skeleton-card" key={item}>
          <span />
          <span />
          <span />
        </div>
      ))}
    </div>
    <span className="my-orders-loading-label">Загружаем ваши публикации и отклики…</span>
  </div>
);

export const MyOrdersPage = () => {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [myHelps, setMyHelps] = useState<Help[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [activeTab, setActiveTab] = useState<OrdersTab>('posts');
  const [deletePostId, setDeletePostId] = useState<number | null>(null);
  const [deletingPostId, setDeletingPostId] = useState<number | null>(null);
  const [boostingPostId, setBoostingPostId] = useState<number | null>(null);
  const boostKeyRef = useRef<{ postId: number; key: string } | null>(null);

  const loadData = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setLoadError(false);
    try {
      const [postsResponse, helpsResponse] = await Promise.all([
        postsApi.getByUser(user.id),
        helpApi.getHelpsByHelper(user.id),
      ]);
      const helpsPayload: unknown = helpsResponse;
      setMyPosts(Array.isArray(postsResponse) ? postsResponse : []);
      if (Array.isArray(helpsPayload)) {
        setMyHelps(helpsPayload as Help[]);
      } else if (isHelpPage(helpsPayload)) {
        setMyHelps(helpsPayload.content);
      } else {
        setMyHelps([]);
      }
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const helpsList = myHelps;
  const completedHelps = helpsList.filter((help) => help.status === 'COMPLETED').length;
  const stats: StatItem[] = [
    { label: 'Всего постов', value: myPosts.length, Icon: FileText },
    { label: 'Открытых', value: myPosts.filter((post) => post.status === 'OPEN').length, Icon: TreeDeciduous },
    { label: 'В работе', value: myPosts.filter((post) => post.status === 'IN_PROGRESS').length, Icon: RefreshCw },
    { label: 'Завершено', value: myPosts.filter((post) => post.status === 'COMPLETED').length, Icon: HandHeart },
    { label: 'Помог', value: completedHelps, Icon: HandHeart },
  ];

  const handleDeletePost = async () => {
    if (deletePostId === null || deletingPostId !== null) return;
    const postId = deletePostId;
    setDeletingPostId(postId);
    try {
      await postsApi.delete(postId);
      setMyPosts((posts) => posts.filter((post) => post.id !== postId));
      setDeletePostId(null);
      showToast('Пост удалён', 'success');
    } catch {
      setDeletePostId(postId);
      showToast('Не удалось удалить пост. Попробуйте ещё раз.', 'error');
    } finally {
      setDeletingPostId(null);
    }
  };

  const handleBoostPost = async (postId: number) => {
    setBoostingPostId(postId);
    // Reuse the same key if the same post is retried after a failure.
    if (!boostKeyRef.current || boostKeyRef.current.postId !== postId) {
      boostKeyRef.current = { postId, key: createIdempotencyKey('boost') };
    }
    try {
      const result = await postsApi.boost(postId, boostKeyRef.current.key);
      boostKeyRef.current = null;
      showToast(result.message, 'success');
      setMyPosts((posts) => posts.map((post) => post.id === postId ? result.post : post));
    } catch (error: unknown) {
      const responseMessage =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(responseMessage || 'Не удалось поднять пост. Попробуйте ещё раз.', 'error');
    } finally {
      setBoostingPostId(null);
    }
  };

  return (
    <main className="my-orders-page">
      <nav className="my-orders-breadcrumb" aria-label="Навигационная цепочка">
        <Link to="/">Главная</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Мои заказы</span>
      </nav>

      <header className="my-orders-header">
        <div className="my-orders-heading">
          <span className="my-orders-eyebrow">Личный кабинет</span>
          <h1>Мои заказы</h1>
          <p>Ваши публикации и отклики на просьбы о помощи — в одном месте.</p>
        </div>
        <Link className="my-orders-create" to="/posts/new">
          <Plus size={18} aria-hidden="true" />
          Создать пост
        </Link>
      </header>

      {loading ? (
        <LoadingState />
      ) : (
        <>
          {loadError && (
            <div className="my-orders-error" role="alert">
              <span>Не удалось загрузить заказы. Проверьте соединение и попробуйте снова.</span>
              <button type="button" onClick={() => void loadData()}>
                <RefreshCw size={15} aria-hidden="true" /> Повторить
              </button>
            </div>
          )}

          <section className="my-orders-stats" aria-label="Статистика моих заказов">
            {stats.map(({ label, value, Icon }, index) => (
              <article className={`my-orders-stat${index === 0 ? ' my-orders-stat--featured' : ''}`} key={label}>
                <span className="my-orders-stat-icon" aria-hidden="true"><Icon size={17} /></span>
                <strong>{loadError ? '—' : value.toLocaleString('ru-RU')}</strong>
                <span>{label}</span>
              </article>
            ))}
          </section>

          <section className="my-orders-panel" aria-label="Публикации и отклики">
            <div className="my-orders-tabs" role="tablist" aria-label="Тип заказов">
              <button
                type="button"
                id="my-orders-tab-posts"
                role="tab"
                aria-selected={activeTab === 'posts'}
                aria-controls="my-orders-results"
                className={activeTab === 'posts' ? 'is-active' : ''}
                onClick={() => setActiveTab('posts')}
              >
                Мои посты <span>{myPosts.length}</span>
              </button>
              <button
                type="button"
                id="my-orders-tab-helps"
                role="tab"
                aria-selected={activeTab === 'helps'}
                aria-controls="my-orders-results"
                className={activeTab === 'helps' ? 'is-active' : ''}
                onClick={() => setActiveTab('helps')}
              >
                Мои отклики <span>{helpsList.length}</span>
              </button>
            </div>

            <div
              id="my-orders-results"
              className="my-orders-content"
              role="tabpanel"
              aria-labelledby={`my-orders-tab-${activeTab}`}
              tabIndex={0}
            >
              {loadError ? (
                <div className="my-orders-state">
                  <span className="my-orders-state-icon"><RefreshCw size={24} aria-hidden="true" /></span>
                  <h2>Данные временно недоступны</h2>
                  <p>Не удалось получить ваши публикации и отклики.</p>
                  <button type="button" className="my-orders-secondary" onClick={() => void loadData()}>
                    <RefreshCw size={16} aria-hidden="true" /> Повторить загрузку
                  </button>
                </div>
              ) : activeTab === 'posts' ? (
                myPosts.length === 0 ? (
                  <div className="my-orders-state">
                    <span className="my-orders-state-icon"><FileText size={25} aria-hidden="true" /></span>
                    <h2>Пока нет публикаций</h2>
                    <p>Создайте первый пост, чтобы рассказать сообществу, какая помощь вам нужна.</p>
                    <Link className="my-orders-create my-orders-create--small" to="/posts/new">
                      <Plus size={17} aria-hidden="true" /> Создать пост
                    </Link>
                  </div>
                ) : (
                  <div className="my-orders-list">
                    {myPosts.map((post) => (
                      <article className="my-orders-card" key={post.id}>
                        <div className="my-orders-card-main">
                          <div className="my-orders-card-flags">
                            <StatusPill status={post.status} />
                            {isBoosted(post) && (
                              <span className="my-orders-boosted">
                                <Zap size={13} aria-hidden="true" />
                                В топе{getBoostTimeRemaining(post) ? ` · ${getBoostTimeRemaining(post)}` : ''}
                              </span>
                            )}
                          </div>
                          <h2><Link to={`/posts/${post.id}`}>{post.title}</Link></h2>
                          {post.description && <p className="my-orders-description">{post.description}</p>}
                          <div className="my-orders-card-meta">
                            {post.category && <span className="my-orders-category">{post.category}</span>}
                            <time dateTime={post.createdAt}>
                              <CalendarDays size={14} aria-hidden="true" />
                              {getRelativeTime(post.createdAt)}
                            </time>
                          </div>
                        </div>
                        <div className="my-orders-card-actions" aria-label={`Действия для поста ${post.title}`}>
                          <Link className="my-orders-action my-orders-action--quiet" to={`/posts/${post.id}/edit`}>
                            Редактировать
                          </Link>
                          <Link className="my-orders-action my-orders-action--quiet" to={`/posts/${post.id}`}>
                            Открыть <ArrowUpRight size={14} aria-hidden="true" />
                          </Link>
                          {post.status === 'OPEN' && (
                            <button
                              type="button"
                              className="my-orders-action my-orders-action--boost"
                              onClick={() => void handleBoostPost(post.id)}
                              disabled={boostingPostId === post.id}
                            >
                              <Zap size={14} aria-hidden="true" />
                              {boostingPostId === post.id ? 'Поднимаем…' : isBoosted(post) ? 'Продлить' : 'В топ'}
                            </button>
                          )}
                          <button
                            type="button"
                            className="my-orders-action my-orders-action--danger"
                            onClick={() => setDeletePostId(post.id)}
                            disabled={deletingPostId === post.id}
                          >
                            <Trash2 size={14} aria-hidden="true" />
                            {deletingPostId === post.id ? 'Удаляем…' : 'Удалить'}
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                )
              ) : helpsList.length === 0 ? (
                <div className="my-orders-state">
                  <span className="my-orders-state-icon"><HandHeart size={25} aria-hidden="true" /></span>
                  <h2>Пока нет откликов</h2>
                  <p>Найдите просьбу, на которую сможете откликнуться, и помогите участнику сообщества.</p>
                  <Link className="my-orders-secondary" to="/posts">
                    <TreeDeciduous size={16} aria-hidden="true" /> Перейти в каталог
                  </Link>
                </div>
              ) : (
                <div className="my-orders-list">
                  {helpsList.map((help) => (
                    <article className="my-orders-card my-orders-card--help" key={help.id}>
                      <div className="my-orders-card-main">
                        <div className="my-orders-card-flags">
                          <StatusPill status={help.status} type="help" />
                          <span className="my-orders-help-label">Ваш отклик</span>
                        </div>
                        <h2><Link to={`/posts/${help.postId}`}>{help.postTitle}</Link></h2>
                        <p className="my-orders-help-author">Автор заявки: <strong>{help.receiverName}</strong></p>
                      </div>
                      <div className="my-orders-card-actions my-orders-help-actions">
                        <time dateTime={help.acceptedAt}>
                          <CalendarDays size={14} aria-hidden="true" />
                          {formatDate(help.acceptedAt)}
                        </time>
                        <Link className="my-orders-action my-orders-action--quiet" to={`/posts/${help.postId}`}>
                          Открыть заявку <ArrowUpRight size={14} aria-hidden="true" />
                        </Link>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>
        </>
      )}

      <Modal
        isOpen={deletePostId !== null}
        onClose={() => setDeletePostId(null)}
        onConfirm={handleDeletePost}
        title="Удалить публикацию?"
        message="Пост будет удалён. Это действие нельзя отменить."
        confirmText="Удалить"
        cancelText="Оставить"
        variant="danger"
      />
    </main>
  );
};
