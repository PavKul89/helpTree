import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Bookmark,
  Ban,
  CalendarDays,
  Check,
  HandHeart,
  Image as ImageIcon,
  MapPin,
  MessageCircle,
  Send,
  Star,
  Trash2,
  X,
  Zap,
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { reviewApi } from '../api/reviewApi';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import type { Post, Comment, Help, Review } from '../types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { getStatusColor, getStatusLabel } from '../components/StatusBadge';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';
import { Avatar } from '../components/Avatar';
import { getRelativeTime } from '../utils/dateUtils';
import './PostDetailPage.css';

const StatusBadge = ({ status, type = 'post' }: { status: string; type?: 'post' | 'help' }) => {
  const color = getStatusColor(status, type);
  return (
    <span
      className="post-detail-status"
      style={{ color, borderColor: `${color}66`, backgroundColor: `${color}1A` }}
    >
      <span className="post-detail-status-dot" style={{ backgroundColor: color }} aria-hidden="true" />
      {getStatusLabel(status, type)}
    </span>
  );
};

export const PostDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showToast } = useToast();
  const postId = Number(id);
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [helps, setHelps] = useState<Help[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [newComment, setNewComment] = useState('');
  const [reviewComment, setReviewComment] = useState('');
  const [replyingTo, setReplyingTo] = useState<number | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [commentsError, setCommentsError] = useState(false);
  const [isFavorite, setIsFavorite] = useState(false);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [showDeletePostModal, setShowDeletePostModal] = useState(false);
  const [showFavoriteModal, setShowFavoriteModal] = useState(false);
  const [isBoosting, setIsBoosting] = useState(false);
  const isStartingChatRef = useRef(false);
  const [isCommenting, setIsCommenting] = useState(false);
  const [isReplying, setIsReplying] = useState(false);
  const [deletingCommentId, setDeletingCommentId] = useState<number | null>(null);
  const [isDeletingPost, setIsDeletingPost] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    setNotFound(false);
    setForbidden(false);
    try {
      const postData = await postsApi.getById(postId);
      setPost(postData);
      const [commentsResult, helpsResult, userResult] = await Promise.allSettled([
        postsApi.getComments(postId),
        helpApi.getHelpsByPost(postId),
        authApi.getCurrentUser(),
      ]);

      if (commentsResult.status === 'fulfilled') {
        setComments(commentsResult.value);
        setCommentsError(false);
      } else {
        setComments([]);
        setCommentsError(true);
      }

      if (helpsResult.status === 'fulfilled') {
        setHelps(helpsResult.value);
        const confirmedHelp = helpsResult.value.find((help) => help.status === 'CONFIRMED');
        if (confirmedHelp) {
          try {
            setReviews(await reviewApi.getByHelp(confirmedHelp.id));
          } catch {
            setReviews([]);
            showToast('Не удалось загрузить отзывы.', 'error');
          }
        } else {
          setReviews([]);
        }
      } else {
        setHelps([]);
        setReviews([]);
        showToast('Не удалось загрузить отклики.', 'error');
      }

      if (userResult.status === 'fulfilled') {
        setCurrentUserId(userResult.value.id);
      } else {
        setCurrentUserId(user?.id ?? null);
      }
    } catch (error: unknown) {
      const status =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { status?: number } }).response?.status
          : undefined;
      setNotFound(status === 404);
      setForbidden(status === 403);
      setLoadError(status !== 404 && status !== 403);
    } finally {
      setLoading(false);
    }
  }, [postId, showToast, user?.id]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (!post?.id || !currentUserId) return;
    authApi.getFavorites(currentUserId)
      .then((favorites) => setIsFavorite(favorites.includes(post.id)))
      .catch(() => setIsFavorite(false));
  }, [post?.id, currentUserId]);

  const isAuthor = Boolean(post && user?.id === post.userId);
  const isAuthorBlocked = Boolean(post?.authorBlockedAt);
  const isUserBlocked = Boolean(
    user && (user.blockedUntil ? new Date(user.blockedUntil) > new Date() : user.blockedAt)
  );
  const canOfferHelp = Boolean(user && post && !isAuthor && post.status === 'OPEN' && !isUserBlocked && !isAuthorBlocked);
  const isPostBoosted = Boolean(post?.boostedUntil && new Date(post.boostedUntil).getTime() > Date.now());
  const boostTimeRemaining = (() => {
    if (!post?.boostedUntil) return '';
    const diff = new Date(post.boostedUntil).getTime() - Date.now();
    if (diff <= 0) return '';
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    return hours > 0 ? `${hours}ч ${minutes}мин` : `${minutes}мин`;
  })();

  const handleAddComment = async () => {
    const content = newComment.trim();
    if (!content || !user || isCommenting) return;
    setIsCommenting(true);
    try {
      const comment = await postsApi.addComment(postId, { content });
      setComments((previous) => [...previous, comment]);
      setNewComment('');
      showToast('Комментарий добавлен.', 'success');
    } catch {
      showToast('Не удалось отправить комментарий. Попробуйте ещё раз.', 'error');
    } finally {
      setIsCommenting(false);
    }
  };

  const handleOfferHelp = async () => {
    if (!user || !canOfferHelp) return;
    try {
      await helpApi.acceptHelp({ postId, helperId: user.id });
      await loadData();
      showToast('Вы откликнулись на пост!', 'success');
    } catch (error: unknown) {
      const message =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(message || 'Не удалось откликнуться на пост.', 'error');
    }
  };

  const handleCompleteHelp = async (helpId: number) => {
    try {
      await helpApi.completeHelp(helpId);
      await loadData();
    } catch {
      showToast('Не удалось обновить отклик.', 'error');
    }
  };

  const handleConfirmHelp = async (helpId: number) => {
    try {
      await helpApi.confirmHelp(helpId);
      await loadData();
    } catch {
      showToast('Не удалось подтвердить выполнение.', 'error');
    }
  };

  const handleCancelHelp = async (helpId: number) => {
    try {
      await helpApi.cancelHelp(helpId);
      await loadData();
    } catch {
      showToast('Не удалось отменить отклик.', 'error');
    }
  };

  const handleDeletePost = async () => {
    setIsDeletingPost(true);
    try {
      await postsApi.delete(postId);
      navigate('/');
    } catch {
      setShowDeletePostModal(true);
      showToast('Не удалось удалить пост. Попробуйте ещё раз.', 'error');
    } finally {
      setIsDeletingPost(false);
    }
  };

  const handleBoostPost = async () => {
    if (isBoosting) return;
    setIsBoosting(true);
    try {
      const result = await postsApi.boost(postId);
      setPost(result.post);
      showToast(result.message, 'success');
    } catch (error: unknown) {
      const message =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(message || 'Не удалось поднять пост.', 'error');
    } finally {
      setIsBoosting(false);
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    setDeletingCommentId(commentId);
    try {
      await postsApi.deleteComment(postId, commentId);
      setComments((previous) => previous.filter((comment) => comment.id !== commentId));
      showToast('Комментарий удалён.', 'success');
    } catch {
      showToast('Не удалось удалить комментарий.', 'error');
    } finally {
      setDeletingCommentId(null);
    }
  };

  const handleSubmitReview = async (helpId: number) => {
    if (!user || !helps.some((help) => help.id === helpId)) return;
    try {
      await reviewApi.create({ helpId, rating: 5, comment: reviewComment });
      setReviewComment('');
      await loadData();
      showToast('Отзыв отправлен! +2 HC', 'success');
    } catch {
      showToast('Не удалось отправить отзыв.', 'error');
    }
  };

  const handleReply = async (parentCommentId: number) => {
    const content = replyContent.trim();
    if (!content || !user || isReplying) return;
    setIsReplying(true);
    try {
      const comment = await postsApi.addComment(postId, { content, parentCommentId });
      setComments((previous) => [...previous, comment]);
      setReplyContent('');
      setReplyingTo(null);
      showToast('Ответ добавлен.', 'success');
    } catch {
      showToast('Не удалось отправить ответ.', 'error');
    } finally {
      setIsReplying(false);
    }
  };

  const handleToggleFavorite = async () => {
    if (!currentUserId || !post) return;
    if (!isFavorite && (post.status === 'IN_PROGRESS' || post.status === 'COMPLETED')) {
      setShowFavoriteModal(true);
      return;
    }
    try {
      if (isFavorite) {
        await authApi.removeFavorite(currentUserId, post.id);
        setIsFavorite(false);
        showToast('Убрано из избранного', 'success');
      } else {
        await authApi.addFavorite(currentUserId, post.id);
        setIsFavorite(true);
        showToast('Добавлено в избранное', 'success');
      }
    } catch {
      showToast('Не удалось обновить избранное.', 'error');
    }
  };

  const handleStartChat = async () => {
    if (!post || isStartingChatRef.current) return;
    isStartingChatRef.current = true;
    try {
      const chat = await chatApi.createChat({ participantId: post.userId });
      navigate(`/chats/${chat.id}`);
    } catch {
      showToast('Не удалось открыть чат. Попробуйте ещё раз.', 'error');
    } finally {
      isStartingChatRef.current = false;
    }
  };

  if (loading) {
    return (
      <main className="post-detail-page">
        <Link to="/" className="post-detail-back"><ArrowLeft size={16} aria-hidden="true" /> На главную</Link>
        <div className="post-detail-loading" role="status" aria-label="Загружаем заявку">
          <div className="post-detail-loading-card">
            <div className="post-detail-loading-badges"><span /><span /></div>
            <span className="post-detail-loading-title" />
            <span />
            <span />
            <span className="post-detail-loading-image" />
            <div className="post-detail-loading-author"><span /><i /><i /></div>
          </div>
          <span className="post-detail-loading-sr">Загружаем заявку и обсуждение…</span>
        </div>
      </main>
    );
  }

  if (loadError || forbidden || notFound || !post) {
    return (
      <main className="post-detail-page">
        <Link to="/" className="post-detail-back"><ArrowLeft size={16} aria-hidden="true" /> На главную</Link>
        <section className="post-detail-state" role={loadError ? 'alert' : undefined}>
          <span className="post-detail-state-icon"><ImageIcon size={23} aria-hidden="true" /></span>
          <h1>{loadError ? 'Не удалось загрузить заявку' : forbidden ? 'Недостаточно прав' : 'Заявка не найдена'}</h1>
          <p>{loadError
            ? 'Проверьте соединение и попробуйте загрузить страницу ещё раз.'
            : forbidden
              ? 'У вас нет доступа к этой заявке.'
              : 'Возможно, публикация была удалена или ссылка устарела.'}</p>
          {loadError && (
            <Button onClick={() => void loadData()}>Повторить загрузку</Button>
          )}
        </section>
      </main>
    );
  }

  const confirmedHelps = helps.filter(
    (help) => help.status === 'CONFIRMED' && (help.helperId === user?.id || post.userId === user?.id)
  );
  const showReviews = confirmedHelps.length > 0;

  return (
    <main className="post-detail-page">
      <nav className="post-detail-breadcrumb" aria-label="Навигационная цепочка">
        <Link to="/">Главная</Link>
        <span aria-hidden="true">/</span>
        <Link to="/posts">Заявки</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{post.title}</span>
      </nav>

      <article className="post-detail-card">
        <div className="post-detail-heading">
          <div className="post-detail-badges">
            <StatusBadge status={post.status} />
            {post.category && <span className="post-detail-category">{post.category}</span>}
          </div>
          <h1>{post.title}</h1>
          {post.description && <p className="post-detail-description">{post.description}</p>}

          {post.imageUrls && post.imageUrls.length > 0 && (
            <div className="post-detail-gallery">
              <a className="post-detail-hero-image" href={post.imageUrls[0]} target="_blank" rel="noreferrer">
                <img src={post.imageUrls[0]} alt={post.title} />
                {post.imageUrls.length > 1 && <span className="post-detail-image-count">+{post.imageUrls.length - 1} фото</span>}
              </a>
              {post.imageUrls.length > 1 && (
                <div className="post-detail-thumbnails">
                  {post.imageUrls.slice(1, 5).map((image, index) => (
                    <a href={image} target="_blank" rel="noreferrer" key={`${image}-${index}`} aria-label={`Открыть фото ${index + 2}`}>
                      <img src={image} alt="" />
                    </a>
                  ))}
                  {post.imageUrls.length > 5 && <span>+{post.imageUrls.length - 5}</span>}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="post-detail-author">
          <Avatar
            name={post.authorName}
            avatarUrl={post.authorAvatarUrl}
            size="large"
            clickable
            userId={post.userId}
          />
          <div className="post-detail-author-info">
            <Link to={`/profile/${post.userId}`} className="post-detail-author-name">{post.authorName}</Link>
            <div className="post-detail-author-meta">
              {post.userCity && <span><MapPin size={14} aria-hidden="true" />{post.userCity}</span>}
              <span><Star size={14} aria-hidden="true" />{Number.isFinite(post.authorRating) ? post.authorRating.toFixed(1) : '—'}</span>
              <span><CalendarDays size={14} aria-hidden="true" />{getRelativeTime(post.createdAt)}</span>
            </div>
          </div>
        </div>

        {(canOfferHelp || (user && user.id !== post.userId) || (currentUserId && currentUserId !== post.userId)) && (
          <div className="post-detail-actions">
            {canOfferHelp && (
              <Button onClick={() => void handleOfferHelp()}>
                <HandHeart size={17} aria-hidden="true" /> Откликнуться
              </Button>
            )}
            {user && user.id !== post.userId && (
              <Button
                variant="outline"
                onClick={() => void handleStartChat()}
                disableRipple
              >
                <MessageCircle size={16} aria-hidden="true" />
                Написать
              </Button>
            )}
            {currentUserId && currentUserId !== post.userId && (
              <button
                type="button"
                className={`post-detail-favorite${isFavorite ? ' is-active' : ''}`}
                onClick={() => void handleToggleFavorite()}
                aria-pressed={isFavorite}
              >
                <Bookmark
                  size={15}
                  aria-hidden="true"
                  fill={isFavorite ? 'currentColor' : 'none'}
                  strokeWidth={isFavorite ? 2.5 : 2}
                />
                {isFavorite ? 'В избранном' : 'В избранное'}
              </button>
            )}
          </div>
        )}

        {!canOfferHelp && user && !isAuthor && post.status === 'OPEN' && (isUserBlocked || isAuthorBlocked) && (
          <div className="post-detail-notice" role="status">
            <Ban size={17} aria-hidden="true" />
            {isUserBlocked
              ? 'Ваш аккаунт заблокирован. Сейчас нельзя откликнуться на заявку.'
              : 'Автор заявки заблокирован. Сейчас нельзя откликнуться.'}
          </div>
        )}

        {isAuthor && (
          <div className="post-detail-owner-actions" aria-label="Действия владельца заявки">
            <span className="post-detail-owner-label">Управление заявкой</span>
            <div className="post-detail-owner-buttons">
              {post.status === 'OPEN' && (
                <Button
                  variant="outline"
                  onClick={() => void handleBoostPost()}
                  disabled={isBoosting}
                  className="post-detail-boost-button"
                >
                  <Zap size={15} aria-hidden="true" />
                  {isBoosting ? 'Поднимаем…' : isPostBoosted ? `В топе · ${boostTimeRemaining}` : 'Поднять в топ · 5 HC'}
                </Button>
              )}
              <Button variant="outline" onClick={() => navigate(`/posts/${post.id}/edit`)}>
                Редактировать
              </Button>
              <Button variant="danger" onClick={() => setShowDeletePostModal(true)} disabled={isDeletingPost}>
                <Trash2 size={15} aria-hidden="true" /> Удалить пост
              </Button>
            </div>
          </div>
        )}
      </article>

      <Modal
        isOpen={showDeletePostModal}
        onClose={() => setShowDeletePostModal(false)}
        onConfirm={handleDeletePost}
        title="Удаление поста"
        message="Вы уверены, что хотите удалить этот пост? Это действие нельзя отменить."
        confirmText={isDeletingPost ? 'Удаляем…' : 'Удалить'}
        cancelText="Отмена"
      />
      <Modal
        isOpen={showFavoriteModal}
        onClose={() => setShowFavoriteModal(false)}
        onConfirm={() => setShowFavoriteModal(false)}
        title="Нельзя добавить в избранное"
        message={'В избранное можно добавлять только\nоткрытые заявки.'}
        confirmText="Понятно"
        cancelText={null}
      />

      {helps.length > 0 && (
        <section className="post-detail-section">
          <div className="post-detail-section-heading">
            <h2>Отклики</h2>
            <span>{helps.length}</span>
          </div>
          <div className="post-detail-help-list">
            {helps.map((help) => (
              <article className="post-detail-help-item" key={help.id}>
                <div className="post-detail-help-identity">
                  <Avatar name={help.helperName} size="medium" clickable userId={help.helperId} />
                  <div><strong>{help.helperName}</strong><StatusBadge status={help.status} type="help" /></div>
                </div>
                <div className="post-detail-help-actions">
                  {help.status === 'ACCEPTED' && !isAuthor && help.helperId === user?.id && (
                    <Button onClick={() => void handleCompleteHelp(help.id)}><Check size={15} /> Выполнено</Button>
                  )}
                  {help.status === 'COMPLETED' && isAuthor && (
                    <Button onClick={() => void handleConfirmHelp(help.id)}><Check size={15} /> Подтвердить</Button>
                  )}
                  {(help.status === 'ACCEPTED' || help.status === 'PENDING') && !isAuthor && help.helperId === user?.id && (
                    <Button variant="outline" onClick={() => void handleCancelHelp(help.id)}><X size={15} /> Отменить</Button>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      {showReviews && (
        <section className="post-detail-section">
          <div className="post-detail-section-heading"><h2>Отзывы</h2></div>
          {reviews.length > 0 ? reviews.map((review) => (
            <article className="post-detail-review" key={review.id}>
              <p><strong>{review.fromUserName}</strong> оставил отзыв для <strong>{review.toUserName}</strong></p>
              {review.comment && <p className="post-detail-review-comment">{review.comment}</p>}
            </article>
          )) : <p className="post-detail-empty-note">Отзывов пока нет.</p>}

          {user && confirmedHelps.map((help) => {
            const existingReview = reviews.find((review) =>
              (user.id === post.userId && review.toUserId === help.helperId) ||
              (user.id === help.helperId && review.toUserId === post.userId)
            );
            if (existingReview) {
              return <p className="post-detail-empty-note" key={help.id}>Вы уже оставили отзыв для {user.id === post.userId ? help.helperName : post.authorName}.</p>;
            }
            return (
              <div className="post-detail-review-form" key={help.id}>
                <label htmlFor={`review-${help.id}`}>Отзыв для {user.id === post.userId ? help.helperName : post.authorName}</label>
                <textarea
                  id={`review-${help.id}`}
                  value={reviewComment}
                  onChange={(event) => setReviewComment(event.target.value)}
                  placeholder="Комментарий (необязательно)"
                  rows={3}
                />
                <Button onClick={() => void handleSubmitReview(help.id)}>Отправить отзыв</Button>
              </div>
            );
          })}
        </section>
      )}

      <section className="post-detail-section post-detail-comments" aria-labelledby="post-detail-comments-heading">
        <div className="post-detail-section-heading">
          <div>
            <h2 id="post-detail-comments-heading">Комментарии</h2>
            <p>Обсудите детали заявки с автором и сообществом.</p>
          </div>
          <span aria-label={`${comments.length} комментариев`}>{comments.length}</span>
        </div>

        {user && (
          <form
            className="post-detail-comment-form"
            onSubmit={(event) => {
              event.preventDefault();
              void handleAddComment();
            }}
          >
            <label htmlFor="post-detail-new-comment">Оставить комментарий</label>
            <textarea
              id="post-detail-new-comment"
              value={newComment}
              onChange={(event) => setNewComment(event.target.value)}
              placeholder="Напишите комментарий…"
              rows={4}
              maxLength={2000}
            />
            <div className="post-detail-form-footer">
              <span>{newComment.length}/2000</span>
              <Button type="submit" disabled={!newComment.trim() || isCommenting}>
                <Send size={15} aria-hidden="true" /> {isCommenting ? 'Отправляем…' : 'Отправить'}
              </Button>
            </div>
          </form>
        )}

        {commentsError ? (
          <div className="post-detail-comments-error" role="alert">
            <p>Не удалось загрузить комментарии. Попробуйте ещё раз.</p>
            <button type="button" onClick={() => void loadData()}>Повторить</button>
          </div>
        ) : comments.length === 0 ? (
          <div className="post-detail-comments-empty">
            <span><MessageCircle size={18} aria-hidden="true" /></span>
            <p>Комментариев пока нет. Будьте первым, кто задаст вопрос или уточнит детали.</p>
          </div>
        ) : (
          <div className="post-detail-comment-list">
            {comments.map((comment) => (
              <article className="post-detail-comment" key={comment.id}>
                <Avatar name={comment.userName} size="medium" clickable userId={comment.userId} />
                <div className="post-detail-comment-body">
                  <div className="post-detail-comment-head">
                    <Link to={`/profile/${comment.userId}`}>{comment.userName}</Link>
                    <time dateTime={comment.createdAt}>{getRelativeTime(comment.createdAt)}</time>
                  </div>
                  <p>{comment.content}</p>
                  <div className="post-detail-comment-actions">
                    {user && (
                      <button type="button" onClick={() => setReplyingTo(replyingTo === comment.id ? null : comment.id)}>
                        <MessageCircle size={13} aria-hidden="true" /> Ответить
                      </button>
                    )}
                    {user?.id === comment.userId && (
                      <button
                        type="button"
                        className="post-detail-comment-delete"
                        onClick={() => void handleDeleteComment(comment.id)}
                        disabled={deletingCommentId === comment.id}
                      >
                        <Trash2 size={13} aria-hidden="true" />
                        {deletingCommentId === comment.id ? 'Удаляем…' : 'Удалить'}
                      </button>
                    )}
                  </div>
                  {replyingTo === comment.id && (
                    <form
                      className="post-detail-reply-form"
                      onSubmit={(event) => {
                        event.preventDefault();
                        void handleReply(comment.id);
                      }}
                    >
                      <input
                        value={replyContent}
                        onChange={(event) => setReplyContent(event.target.value)}
                        placeholder="Написать ответ…"
                        maxLength={2000}
                        aria-label="Текст ответа"
                      />
                      <Button type="submit" disabled={!replyContent.trim() || isReplying}>
                        <Send size={14} /> {isReplying ? 'Отправляем…' : 'Отправить'}
                      </Button>
                      <button type="button" onClick={() => { setReplyingTo(null); setReplyContent(''); }}>Отмена</button>
                    </form>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
};
