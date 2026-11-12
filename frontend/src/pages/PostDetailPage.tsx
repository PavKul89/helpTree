import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Bookmark,
  Ban,
  CalendarDays,
  Check,
  Flag,
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
import { reportsApi } from '../api/reportsApi';
import { notifyWalletUpdated, walletApi, type WalletPricingDto } from '../api/walletApi';
import type { Post, Comment, CommentSort, Help, Review, ReportReason } from '../types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { getStatusColor, getStatusLabel } from '../components/StatusBadge';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';
import { Avatar } from '../components/Avatar';
import { getRelativeTime } from '../utils/dateUtils';
import { createIdempotencyKey } from '../utils/idempotency';
import './PostDetailPage.css';

const COMMENT_PAGE_SIZE = 20;
const COMMENT_MAX_LENGTH = 1000;
const COMMENT_MAX_DEPTH = 3;

const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: 'Спам' },
  { value: 'ABUSE', label: 'Оскорбление' },
  { value: 'HARASSMENT', label: 'Травля' },
  { value: 'INAPPROPRIATE', label: 'Неуместный контент' },
  { value: 'FRAUD', label: 'Мошенничество' },
  { value: 'IMPERSONATION', label: 'Выдаёт себя за другого' },
  { value: 'OTHER', label: 'Другое' },
];

type ReportTarget = {
  type: 'POST' | 'USER' | 'REVIEW';
  id: number;
  title: string;
};

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
  const boostKeyRef = useRef<string | null>(null);
  const [isCommenting, setIsCommenting] = useState(false);
  const [isReplying, setIsReplying] = useState(false);
  const [deletingCommentId, setDeletingCommentId] = useState<number | null>(null);
  const [isDeletingPost, setIsDeletingPost] = useState(false);
  const [commentsPage, setCommentsPage] = useState(0);
  const [commentsTotal, setCommentsTotal] = useState(0);
  const [commentsHasMore, setCommentsHasMore] = useState(false);
  const [commentsSort, setCommentsSort] = useState<CommentSort>('OLDEST');
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [reportingCommentId, setReportingCommentId] = useState<number | null>(null);
  const [reportReason, setReportReason] = useState<ReportReason>('SPAM');
  const [reportDetails, setReportDetails] = useState('');
  const [isReporting, setIsReporting] = useState(false);
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);
  const [pricing, setPricing] = useState<WalletPricingDto | null>(null);
  const reportKeyRef = useRef<string | null>(null);
  const commentsSortRef = useRef<CommentSort>('OLDEST');

  const applyCommentPage = useCallback(
    (data: Awaited<ReturnType<typeof postsApi.getComments>>, append: boolean) => {
      setComments((previous) => (append ? [...previous, ...data.content] : data.content));
      setCommentsPage(data.page);
      setCommentsTotal(data.totalComments);
      setCommentsHasMore(data.hasMore);
      setCommentsError(false);
    },
    []
  );

  const loadComments = useCallback(
    async (page: number, sort: CommentSort, append: boolean) => {
      setCommentsLoading(true);
      try {
        const data = await postsApi.getComments(postId, {
          page,
          size: COMMENT_PAGE_SIZE,
          sort,
        });
        applyCommentPage(data, append);
      } catch {
        if (append) {
          showToast('Не удалось загрузить следующие комментарии.', 'error');
        } else {
          setComments([]);
          setCommentsTotal(0);
          setCommentsHasMore(false);
          setCommentsError(true);
        }
      } finally {
        setCommentsLoading(false);
      }
    },
    [applyCommentPage, postId, showToast]
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    setNotFound(false);
    setForbidden(false);
    try {
      const postData = await postsApi.getById(postId);
      setPost(postData);
      const commentsRequest = loadComments(0, commentsSortRef.current, false);
      const [helpsResult, userResult] = await Promise.allSettled([
        helpApi.getAllHelpsByPost(postId),
        authApi.getCurrentUser(),
      ]);
      await commentsRequest;

      if (helpsResult.status === 'fulfilled') {
        setHelps(helpsResult.value);
        const confirmedHelp = helpsResult.value.find((help) => help.status === 'CONFIRMED');
        if (confirmedHelp) {
          try {
            setReviews(await reviewApi.getAllByHelp(confirmedHelp.id));
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
  }, [loadComments, postId, showToast, user?.id]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (!post?.id || !currentUserId) return;
    authApi.getFavorites(currentUserId)
      .then((favorites) => setIsFavorite(favorites.includes(post.id)))
      .catch(() => setIsFavorite(false));
  }, [post?.id, currentUserId]);

  useEffect(() => {
    if (!currentUserId) {
      setPricing(null);
      return;
    }
    const controller = new AbortController();
    walletApi
      .getPricing(currentUserId, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setPricing(data);
      })
      .catch(() => {
        if (!controller.signal.aborted) setPricing(null);
      });
    return () => controller.abort();
  }, [currentUserId]);

  const isAuthor = Boolean(post && user?.id === post.userId);
  const isUserBlocked = Boolean(
    user && (user.blockedUntil ? new Date(user.blockedUntil) > new Date() : user.blockedAt)
  );
  const canOfferHelp = Boolean(user && post && !isAuthor && post.status === 'OPEN' && !isUserBlocked);
  const isPostBoosted = Boolean(
    post?.boosted ||
      (post?.boostedUntil && new Date(post.boostedUntil).getTime() > Date.now())
  );
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
      const result = await postsApi.addComment(postId, { content });
      setComments((previous) =>
        commentsSort === 'NEWEST' ? [result.comment, ...previous] : [...previous, result.comment]
      );
      setCommentsTotal(result.totalComments);
      setCommentsError(false);
      setPost((previous) =>
        previous ? { ...previous, commentsCount: result.totalComments } : previous
      );
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
    // Keep the key across failed attempts so a retry replays instead of charging again.
    if (!boostKeyRef.current) {
      boostKeyRef.current = createIdempotencyKey('boost');
    }
    try {
      const result = await postsApi.boost(postId, boostKeyRef.current);
      boostKeyRef.current = null;
      setPost(result.post);
      notifyWalletUpdated(result.balance);
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
      const result = await postsApi.deleteComment(postId, commentId);
      // Replace in place: a tombstone keeps the thread and its replies visible.
      setComments((previous) =>
        previous.map((comment) => (comment.id === result.comment.id ? result.comment : comment))
      );
      setCommentsTotal(result.totalComments);
      setPost((previous) =>
        previous ? { ...previous, commentsCount: result.totalComments } : previous
      );
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
      notifyWalletUpdated();
      showToast(
        pricing ? `Отзыв отправлен! +${pricing.coinsPerReview} HC` : 'Отзыв отправлен!',
        'success'
      );
    } catch {
      showToast('Не удалось отправить отзыв.', 'error');
    }
  };

  const handleReply = async (parentCommentId: number) => {
    const content = replyContent.trim();
    if (!content || !user || isReplying) return;
    setIsReplying(true);
    try {
      const result = await postsApi.addComment(postId, { content, parentCommentId });
      setComments((previous) => [...previous, result.comment]);
      setCommentsTotal(result.totalComments);
      setCommentsError(false);
      setPost((previous) =>
        previous ? { ...previous, commentsCount: result.totalComments } : previous
      );
      setReplyContent('');
      setReplyingTo(null);
      showToast('Ответ добавлен.', 'success');
    } catch (error: unknown) {
      const message =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(message || 'Не удалось отправить ответ.', 'error');
    } finally {
      setIsReplying(false);
    }
  };

  const handleReportComment = async (commentId: number) => {
    if (!user || isReporting) return;
    setIsReporting(true);
    try {
      await postsApi.reportComment(postId, commentId, {
        reason: reportReason,
        details: reportDetails.trim() || undefined,
      });
      setReportingCommentId(null);
      setReportDetails('');
      setReportReason('SPAM');
      showToast('Жалоба отправлена. Спасибо, что помогаете модерировать.', 'success');
    } catch (error: unknown) {
      const message =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(message || 'Не удалось отправить жалобу.', 'error');
    } finally {
      setIsReporting(false);
    }
  };

  const openReportTarget = (target: ReportTarget) => {
    setReplyingTo(null);
    setReportingCommentId(null);
    setReportDetails('');
    setReportReason('SPAM');
    reportKeyRef.current = null;
    setReportTarget(target);
  };

  const handleSubmitReport = async () => {
    if (!reportTarget || !user || isReporting) return;
    setIsReporting(true);
    if (!reportKeyRef.current) {
      reportKeyRef.current = createIdempotencyKey('report');
    }
    try {
      if (reportTarget.type === 'REVIEW') {
        await reviewApi.report(
          reportTarget.id,
          { reason: reportReason, details: reportDetails.trim() || undefined },
          reportKeyRef.current
        );
      } else {
        await reportsApi.create(
          {
            targetType: reportTarget.type,
            targetId: reportTarget.id,
            reason: reportReason,
            description: reportDetails.trim() || undefined,
          },
          reportKeyRef.current
        );
      }
      reportKeyRef.current = null;
      setReportTarget(null);
      setReportDetails('');
      setReportReason('SPAM');
      showToast('Жалоба отправлена. Спасибо, что помогаете модерировать.', 'success');
    } catch (error: unknown) {
      const message =
        typeof error === 'object' && error !== null && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      showToast(message || 'Не удалось отправить жалобу.', 'error');
    } finally {
      setIsReporting(false);
    }
  };

  const handleSortChange = (sort: CommentSort) => {
    if (sort === commentsSort) return;
    commentsSortRef.current = sort;
    setCommentsSort(sort);
    setReplyingTo(null);
    setReportingCommentId(null);
    void loadComments(0, sort, false);
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

  const childrenByParent = new Map<number, Comment[]>();
  comments.forEach((comment) => {
    if (comment.parentCommentId == null) return;
    const siblings = childrenByParent.get(comment.parentCommentId) ?? [];
    siblings.push(comment);
    childrenByParent.set(comment.parentCommentId, siblings);
  });
  childrenByParent.forEach((siblings) => {
    siblings.sort((a, b) => {
      const byDate = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      return byDate !== 0 ? byDate : a.id - b.id;
    });
  });
  const rootComments = comments.filter((comment) => comment.parentCommentId == null);
  const canModerate = user?.role === 'ADMIN';

  const renderComment = (comment: Comment): ReactNode => {
    const children = childrenByParent.get(comment.id) ?? [];
    const isDeleted = comment.deleted;
    const canReply = Boolean(user) && !isDeleted && (comment.depth ?? 0) < COMMENT_MAX_DEPTH;
    const canDelete = Boolean(user) && (!isDeleted) && (user?.id === comment.userId || canModerate);
    const canReport = Boolean(user) && !isDeleted && user?.id !== comment.userId;

    return (
      <div className="post-detail-comment-thread" key={comment.id}>
        <article className={`post-detail-comment${isDeleted ? ' is-deleted' : ''}`}>
          <Avatar name={comment.userName} avatarUrl={comment.userAvatarUrl} size="medium" clickable userId={comment.userId} />
          <div className="post-detail-comment-body">
            <div className="post-detail-comment-head">
              <Link to={`/profile/${comment.userId}`}>{comment.userName}</Link>
              <time dateTime={comment.createdAt}>{getRelativeTime(comment.createdAt)}</time>
            </div>

            {isDeleted ? (
              <p className="post-detail-comment-tombstone">
                {comment.deletedByAuthor
                  ? 'Комментарий удалён автором'
                  : 'Комментарий удалён модератором'}
              </p>
            ) : (
              <>
                <p>{comment.content}</p>
                <div className="post-detail-comment-actions">
                  {canReply && (
                    <button
                      type="button"
                      onClick={() => {
                        setReportingCommentId(null);
                        setReplyingTo(replyingTo === comment.id ? null : comment.id);
                      }}
                    >
                      <MessageCircle size={13} aria-hidden="true" /> Ответить
                    </button>
                  )}
                  {canReport && (
                    <button
                      type="button"
                      className="post-detail-comment-report"
                      onClick={() => {
                        setReplyingTo(null);
                        setReportDetails('');
                        setReportReason('SPAM');
                        setReportingCommentId(reportingCommentId === comment.id ? null : comment.id);
                      }}
                    >
                      <Flag size={13} aria-hidden="true" /> Жалоба
                    </button>
                  )}
                  {canDelete && (
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
                      placeholder={`Ответ для ${comment.userName}…`}
                      maxLength={COMMENT_MAX_LENGTH}
                      aria-label="Текст ответа"
                    />
                    <Button type="submit" disabled={!replyContent.trim() || isReplying}>
                      <Send size={14} /> {isReplying ? 'Отправляем…' : 'Отправить'}
                    </Button>
                    <button type="button" onClick={() => { setReplyingTo(null); setReplyContent(''); }}>Отмена</button>
                  </form>
                )}

                {reportingCommentId === comment.id && (
                  <form
                    className="post-detail-report-form"
                    onSubmit={(event) => {
                      event.preventDefault();
                      void handleReportComment(comment.id);
                    }}
                  >
                    <label htmlFor={`report-reason-${comment.id}`}>Причина жалобы</label>
                    <select
                      id={`report-reason-${comment.id}`}
                      value={reportReason}
                      onChange={(event) => setReportReason(event.target.value as ReportReason)}
                    >
                      {REPORT_REASONS.map((option) => (
                        <option value={option.value} key={option.value}>{option.label}</option>
                      ))}
                    </select>
                    <textarea
                      value={reportDetails}
                      onChange={(event) => setReportDetails(event.target.value)}
                      placeholder="Детали (необязательно)"
                      rows={2}
                      maxLength={2000}
                      aria-label="Детали жалобы"
                    />
                    <div className="post-detail-report-actions">
                      <Button type="submit" disabled={isReporting}>
                        <Flag size={14} aria-hidden="true" />
                        {isReporting ? 'Отправляем…' : 'Отправить жалобу'}
                      </Button>
                      <button type="button" onClick={() => setReportingCommentId(null)}>Отмена</button>
                    </div>
                  </form>
                )}
              </>
            )}
          </div>
        </article>

        {children.length > 0 && (
          <div className="post-detail-comment-children">
            {children.map((child) => renderComment(child))}
          </div>
        )}
      </div>
    );
  };

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
                className="post-detail-message-button"
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
            {user && user.id !== post.userId && (
              <div className="post-detail-report-links">
                <button
                  type="button"
                  className="post-detail-favorite"
                  onClick={() =>
                    openReportTarget({ type: 'POST', id: post.id, title: post.title })
                  }
                >
                  <Flag size={15} aria-hidden="true" />
                  Жалоба на заявку
                </button>
                <button
                  type="button"
                  className="post-detail-favorite"
                  onClick={() =>
                    openReportTarget({
                      type: 'USER',
                      id: post.userId,
                      title: `профиль «${post.authorName}»`,
                    })
                  }
                >
                  <Flag size={15} aria-hidden="true" />
                  Жалоба на автора
                </button>
              </div>
            )}
          </div>
        )}

        {!canOfferHelp && user && !isAuthor && post.status === 'OPEN' && isUserBlocked && (
          <div className="post-detail-notice" role="status">
            <Ban size={17} aria-hidden="true" />
            Ваш аккаунт заблокирован. Сейчас нельзя откликнуться на заявку.
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
                  {isBoosting ? 'Поднимаем…' : isPostBoosted ? `В топе · ${boostTimeRemaining}` : `Поднять в топ${pricing ? ` · ${pricing.boostPrice} HC` : ''}`}
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

      {reportTarget && (
        <section className="post-detail-report-panel" aria-label="Форма жалобы">
          <form
            className="post-detail-report-form"
            onSubmit={(event) => {
              event.preventDefault();
              void handleSubmitReport();
            }}
          >
            <label htmlFor="post-detail-report-target">
              Жалоба на {reportTarget.title}
            </label>
            <select
              id="post-detail-report-target"
              value={reportReason}
              onChange={(event) => setReportReason(event.target.value as ReportReason)}
            >
              {REPORT_REASONS.map((option) => (
                <option value={option.value} key={option.value}>{option.label}</option>
              ))}
            </select>
            <textarea
              value={reportDetails}
              onChange={(event) => setReportDetails(event.target.value)}
              placeholder="Детали (необязательно)"
              rows={3}
              maxLength={2000}
              aria-label="Детали жалобы"
            />
            <div className="post-detail-report-actions">
              <Button type="submit" disabled={isReporting}>
                <Flag size={14} aria-hidden="true" />
                {isReporting ? 'Отправляем…' : 'Отправить жалобу'}
              </Button>
              <button type="button" onClick={() => setReportTarget(null)}>Отмена</button>
            </div>
          </form>
        </section>
      )}

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
        confirmClassName="post-detail-favorite-modal-confirm"
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
              <div className="post-detail-review-head">
                <p><strong>{review.fromUserName}</strong> оставил отзыв для <strong>{review.toUserName}</strong></p>
                {user && user.id !== review.fromUserId && (
                  <button
                    type="button"
                    className="post-detail-review-report"
                    onClick={() =>
                      openReportTarget({
                        type: 'REVIEW',
                        id: review.id,
                        title: `отзыв от ${review.fromUserName}`,
                      })
                    }
                  >
                    <Flag size={13} aria-hidden="true" /> Жалоба
                  </button>
                )}
              </div>
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
          <div className="post-detail-comments-toolbar">
            <div className="post-detail-comments-sort" role="group" aria-label="Сортировка комментариев">
              <button
                type="button"
                className={commentsSort === 'OLDEST' ? 'is-active' : ''}
                aria-pressed={commentsSort === 'OLDEST'}
                onClick={() => handleSortChange('OLDEST')}
              >
                Сначала старые
              </button>
              <button
                type="button"
                className={commentsSort === 'NEWEST' ? 'is-active' : ''}
                aria-pressed={commentsSort === 'NEWEST'}
                onClick={() => handleSortChange('NEWEST')}
              >
                Сначала новые
              </button>
            </div>
            <span aria-label={`${commentsTotal} комментариев`}>{commentsTotal}</span>
          </div>
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
              maxLength={COMMENT_MAX_LENGTH}
            />
            <div className="post-detail-form-footer">
              <span>{newComment.length}/{COMMENT_MAX_LENGTH}</span>
              <Button type="submit" disabled={!newComment.trim() || isCommenting}>
                <Send size={15} aria-hidden="true" /> {isCommenting ? 'Отправляем…' : 'Отправить'}
              </Button>
            </div>
          </form>
        )}

        {commentsError ? (
          <div className="post-detail-comments-error" role="alert">
            <p>Не удалось загрузить комментарии. Попробуйте ещё раз.</p>
            <button type="button" onClick={() => void loadComments(0, commentsSort, false)}>Повторить</button>
          </div>
        ) : commentsLoading && comments.length === 0 ? (
          <div className="post-detail-comments-empty" role="status">
            <span><MessageCircle size={18} aria-hidden="true" /></span>
            <p>Загружаем обсуждение…</p>
          </div>
        ) : comments.length === 0 ? (
          <div className="post-detail-comments-empty">
            <span><MessageCircle size={18} aria-hidden="true" /></span>
            <p>Комментариев пока нет. Будьте первым, кто задаст вопрос или уточнит детали.</p>
          </div>
        ) : (
          <div className="post-detail-comment-list">
            {rootComments.map((comment) => renderComment(comment))}
            {commentsHasMore && (
              <button
                type="button"
                className="post-detail-comments-more"
                onClick={() => void loadComments(commentsPage + 1, commentsSort, true)}
                disabled={commentsLoading}
              >
                {commentsLoading ? 'Загружаем…' : 'Показать ещё'}
              </button>
            )}
          </div>
        )}
      </section>
    </main>
  );
};
