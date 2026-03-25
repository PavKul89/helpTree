import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { reviewApi } from '../api/reviewApi';
import { imagesApi } from '../api/imagesApi';
import type { Post, Comment, Help, Review } from '../types';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { Modal } from '../components/Modal';
import { theme } from '../theme';

export const PostDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [helps, setHelps] = useState<Help[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [newComment, setNewComment] = useState('');
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [replyingTo, setReplyingTo] = useState<number | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({ title: '', description: '' });
  const [showDeletePostModal, setShowDeletePostModal] = useState(false);
  const [showDeleteImageModal, setShowDeleteImageModal] = useState<string | null>(null);
  const { user } = useAuth();

  useEffect(() => {
    loadData();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const loadData = async () => {
    try {
      const postData = await postsApi.getById(Number(id));
      setPost(postData);
      const commentsData = await postsApi.getComments(Number(id));
      setComments(commentsData);
      const helpsData = await helpApi.getHelpsByPost(Number(id));
      setHelps(helpsData);
      const confirmedHelp = helpsData.find(h => h.status === 'CONFIRMED');
      if (confirmedHelp) {
        const reviewsData = await reviewApi.getByHelp(confirmedHelp.id);
        setReviews(reviewsData);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddComment = async () => {
    if (!newComment.trim() || !user) return;
    try {
      await postsApi.addComment(Number(id), { content: newComment });
      setNewComment('');
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleOfferHelp = async () => {
    if (!user) return;
    try {
      await helpApi.acceptHelp({ postId: Number(id), helperId: user.id });
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleCompleteHelp = async (helpId: number) => {
    try {
      await helpApi.completeHelp(helpId);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleConfirmHelp = async (helpId: number) => {
    try {
      await helpApi.confirmHelp(helpId);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleCancelHelp = async (helpId: number) => {
    try {
      await helpApi.cancelHelp(helpId);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeletePost = async () => {
    try {
      await postsApi.delete(Number(id));
      navigate('/');
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeletePostClick = () => {
    setShowDeletePostModal(true);
  };

  const handleEditPost = async () => {
    try {
      await postsApi.update(Number(id), { title: editForm.title, description: editForm.description });
      setIsEditing(false);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const startEdit = () => {
    setEditForm({ title: post!.title, description: post!.description });
    setIsEditing(true);
  };

  const handleDeleteImageClick = (url: string) => {
    setShowDeleteImageModal(url);
  };

  const handleDeleteImageConfirm = async () => {
    if (!showDeleteImageModal || !post) return;
    const url = showDeleteImageModal;
    try {
      const newImageUrls = post.imageUrls?.filter(img => img !== url) || [];
      await postsApi.update(Number(id), { imageUrls: newImageUrls });
      try {
        await imagesApi.delete(url);
      } catch (e) {
        console.log('Изображение уже удалено из хранилища');
      }
      loadData();
    } catch (err: any) {
      console.error('Error updating post:', err?.response?.data || err);
    }
  };

  const handleDeleteImage = async (url: string) => {
    if (!post) return;
    try {
      const newImageUrls = post.imageUrls?.filter(img => img !== url) || [];
      await postsApi.update(Number(id), { imageUrls: newImageUrls });
      try {
        await imagesApi.delete(url);
      } catch (e) {
        console.log('Изображение уже удалено из хранилища');
      }
      loadData();
    } catch (err: any) {
      console.error('Error updating post:', err?.response?.data || err);
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    try {
      await postsApi.deleteComment(Number(id), commentId);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleReply = async (parentCommentId: number) => {
    if (!replyContent.trim()) return;
    try {
      await postsApi.addComment(Number(id), { content: replyContent, parentCommentId });
      setReplyContent('');
      setReplyingTo(null);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleSubmitReview = async (helpId: number, toUserId: number) => {
    try {
      await reviewApi.create({ helpId, rating: reviewRating, comment: reviewComment });
      setReviewRating(5);
      setReviewComment('');
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const getStatusColor = (status: string) => {
    const colors: Record<string, string> = {
      OPEN: '#10B981',
      IN_PROGRESS: '#38bdf8',
      COMPLETED: '#F59E0B',
      CANCELLED: '#EF4444',
    };
    return colors[status] || '#6B7280';
  };

  const getStatusLabel = (status: string) => {
    const labels: Record<string, string> = {
      OPEN: 'Открыт',
      IN_PROGRESS: 'В работе',
      COMPLETED: 'Завершён',
      CANCELLED: 'Отменён',
    };
    return labels[status] || status;
  };

  if (loading) return <Spinner message="Загрузка поста..." />;
  if (!post) return <div style={styles.notFound}>Пост не найден</div>;

  const isAuthor = user?.id === post.userId;
  const canOfferHelp = user && !isAuthor && post.status === 'OPEN';

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        {isEditing ? (
          <div>
            <input
              type="text"
              value={editForm.title}
              onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
              style={styles.input}
            />
            <textarea
              value={editForm.description}
              onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
              style={{ ...styles.input, height: 100 }}
            />
            <div style={styles.buttonGroup}>
              <Button onClick={handleEditPost} style={{ marginRight: 10 }}>Сохранить</Button>
              <Button variant="outline" onClick={() => setIsEditing(false)}>Отмена</Button>
            </div>
          </div>
        ) : (
          <>
            <h1 style={styles.title}>{post.title}</h1>
            <p style={styles.description}>{post.description}</p>
          </>
        )}
        
        {post.imageUrls && post.imageUrls.length > 0 ? (
          <div style={styles.imagesSection}>
            <h3 style={styles.sectionTitle}>Изображения ({post.imageUrls.length})</h3>
            <div style={styles.imagesGrid}>
              {post.imageUrls.map((img, idx) => (
                <div 
                  key={idx} 
                  style={styles.imageWrapper} 
                  className="post-image-wrapper"
                  onClick={() => window.open(img, '_blank')}
                >
                  <img 
                    src={img} 
                    alt={`Изображение ${idx + 1}`} 
                    style={styles.image}
                  />
                  <div className="img-overlay" style={styles.imageOverlay}>
                    <span style={{ color: '#fff', fontSize: '24px' }}>🔍</span>
                  </div>
                  {isAuthor && (
                    <button 
                      onClick={() => handleDeleteImageClick(img)}
                      style={styles.removeImageBtn}
                      onMouseEnter={(e) => e.stopPropagation()}
                      onMouseLeave={(e) => e.stopPropagation()}
                    >
                      ×
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        ) : null}
        
        <div style={styles.meta}>
          <span style={styles.metaItem}><strong>Категория:</strong> {post.category}</span>
          <span style={{ ...styles.statusBadge, backgroundColor: getStatusColor(post.status) }}>
            {getStatusLabel(post.status)}
          </span>
          <span style={styles.metaItem}><strong>Автор:</strong> <Link to={`/profile/${post.userId}`} style={styles.authorLink}>{post.authorName}</Link></span>
        </div>

        {isAuthor && (
          <div style={styles.authorActions}>
            <Button onClick={startEdit} style={{ marginRight: 10 }}>Редактировать</Button>
            <Button variant="danger" onClick={handleDeletePostClick}>Удалить пост</Button>
          </div>
        )}
      </Card>

      <Modal
        isOpen={showDeletePostModal}
        onClose={() => setShowDeletePostModal(false)}
        onConfirm={handleDeletePost}
        title="Удаление поста"
        message="Вы уверены, что хотите удалить этот пост? Это действие нельзя отменить."
        confirmText="Удалить"
        cancelText="Отмена"
      />

      <Modal
        isOpen={!!showDeleteImageModal}
        onClose={() => setShowDeleteImageModal(null)}
        onConfirm={handleDeleteImageConfirm}
        title="Удаление изображения"
        message="Вы уверены, что хотите удалить это изображение?"
        confirmText="Удалить"
        cancelText="Отмена"
      />

      {canOfferHelp && (
        <Button onClick={handleOfferHelp} style={{ marginBottom: 20 }}>
          Откликнуться на пост
        </Button>
      )}

      {helps.length > 0 && (
        <Card style={{ marginBottom: 20 }}>
          <h3 style={styles.sectionTitle}>Отклики</h3>
          {helps.map((help) => (
            <div key={help.id} style={styles.helpItem}>
              <strong>{help.helperName}</strong> — 
              <span style={{ ...styles.statusBadge, backgroundColor: getStatusColor(help.status), marginLeft: 8 }}>
                {getStatusLabel(help.status)}
              </span>
              {help.status === 'ACCEPTED' && !isAuthor && help.helperId === user?.id && (
                <Button onClick={() => handleCompleteHelp(help.id)} style={{ marginLeft: 10 }}>Выполнено</Button>
              )}

              {help.status === 'COMPLETED' && isAuthor && (
                <Button onClick={() => handleConfirmHelp(help.id)} style={{ marginLeft: 10 }}>Подтвердить</Button>
              )}
              {(help.status === 'ACCEPTED' || help.status === 'PENDING') && !isAuthor && help.helperId === user?.id && (
                <Button variant="outline" onClick={() => handleCancelHelp(help.id)} style={{ marginLeft: 10 }}>Отменить</Button>
              )}
            </div>
          ))}
        </Card>
      )}

      {helps.some(h => h.status === 'CONFIRMED') && (
        <Card style={{ marginBottom: 20 }}>
          <h3 style={styles.sectionTitle}>Отзывы</h3>
          {reviews.length > 0 ? reviews.map((review) => (
            <div key={review.id} style={styles.reviewItem}>
              <strong>{review.fromUserName}</strong> оценил <strong>{review.toUserName}</strong> на {review.rating} звёзд
              {review.comment && <p style={styles.reviewComment}>{review.comment}</p>}
            </div>
          )) : <p style={styles.emptyText}>Отзывов пока нет</p>}
          
          {user && (
            <div style={styles.reviewForm}>
              <h4>Оставить отзыв</h4>
              {(() => {
                const confirmedHelps = helps.filter(h => h.status === 'CONFIRMED');
                if (confirmedHelps.length === 0) return <p>Нет подтверждённых услуг для отзыва</p>;
                
                return confirmedHelps.map(help => {
                  const existingReview = reviews.find(r => 
                    (user.id === post.userId && r.toUserId === help.helperId) ||
                    (user.id === help.helperId && r.toUserId === post.userId)
                  );
                  if (existingReview) return <p key={help.id}>Вы уже оставили отзыв для {user.id === post.userId ? help.helperName : post.authorName}</p>;
                  
                  const toUserId = user.id === post.userId ? help.helperId : post.userId;
                  const toUserName = user.id === post.userId ? help.helperName : post.authorName;
                  
                  return (
                    <div key={help.id} style={{ marginBottom: 12 }}>
                      <p>Оценить: <strong>{toUserName}</strong></p>
                      <select 
                        value={reviewRating} 
                        onChange={(e) => setReviewRating(Number(e.target.value))}
                        style={styles.select}
                      >
                        {[1,2,3,4,5].map(r => <option key={r} value={r}>{r} звёзд</option>)}
                      </select>
                      <input
                        type="text"
                        value={reviewComment}
                        onChange={(e) => setReviewComment(e.target.value)}
                        placeholder="Комментарий (необязательно)"
                        style={styles.inputSmall}
                      />
                      <Button onClick={() => handleSubmitReview(help.id, toUserId)}>Отправить</Button>
                    </div>
                  );
                });
              })()}
            </div>
          )}
        </Card>
      )}

      <div style={styles.divider} />

      <h3 style={styles.sectionTitle}>Комментарии ({comments.length})</h3>
      
      {user && (
        <Card style={{ marginBottom: 20 }}>
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="Написать комментарий..."
            style={styles.textarea}
          />
          <Button onClick={handleAddComment}>Отправить</Button>
        </Card>
      )}

      <div>
        {comments.map((comment) => (
          <div key={comment.id} style={styles.commentItem}>
            <div style={styles.commentHeader}>
              <strong>{comment.userName}</strong>
              <span style={styles.commentTime}> — {new Date(comment.createdAt).toLocaleString()}</span>
            </div>
            {user && (
              <Button variant="outline" onClick={() => setReplyingTo(comment.id)} style={styles.replyBtn}>Ответить</Button>
            )}
            {user?.id === comment.userId && (
              <Button variant="danger" onClick={() => handleDeleteComment(comment.id)} style={styles.deleteBtn}>Удалить</Button>
            )}
            <p style={styles.commentContent}>{comment.content}</p>
            {replyingTo === comment.id && (
              <div style={styles.replyForm}>
                <input
                  type="text"
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  placeholder="Написать ответ..."
                  style={styles.inputSmall}
                />
                <Button onClick={() => handleReply(comment.id)}>Отправить</Button>
                <Button variant="outline" onClick={() => { setReplyingTo(null); setReplyContent(''); }}>Отмена</Button>
              </div>
            )}
          </div>
        ))}
        {comments.length === 0 && <p style={styles.emptyText}>Комментариев пока нет</p>}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '24px',
    maxWidth: 900,
    margin: '0 auto',
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '200px',
    color: theme.colors.text,
    fontSize: '18px',
  },
  notFound: {
    textAlign: 'center',
    padding: '40px',
    color: theme.colors.textMuted,
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  mainCard: {
    marginTop: '16px',
    marginBottom: '24px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    marginBottom: '16px',
  },
  description: {
    color: theme.colors.textSecondary,
    fontSize: '16px',
    lineHeight: 1.6,
  },
  sectionTitle: {
    color: theme.colors.text,
    fontSize: '20px',
    marginBottom: '16px',
  },
  imagesSection: {
    marginTop: '24px',
    paddingTop: '24px',
    borderTop: `1px solid ${theme.colors.border}`,
  },
  imagesGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
    gap: '16px',
    marginTop: '16px',
  },
  imageWrapper: {
    position: 'relative',
    borderRadius: theme.borderRadius.lg,
    overflow: 'hidden',
    boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
    aspectRatio: '4/3',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
  },
  image: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    transition: 'transform 0.3s ease',
  },
  imageOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0,0,0,0.3)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0,
    transition: 'opacity 0.3s ease',
  },
  imageOverlayVisible: {
    opacity: 1,
  },
  removeImageBtn: {
    position: 'absolute',
    top: '8px',
    right: '8px',
    background: theme.colors.error,
    color: 'white',
    border: '2px solid rgba(255,255,255,0.8)',
    borderRadius: '50%',
    width: 28,
    height: 28,
    cursor: 'pointer',
    fontSize: '18px',
    fontWeight: 'bold',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
    transition: 'all 0.2s ease',
    boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
  },
  meta: {
    marginTop: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    flexWrap: 'wrap',
  },
  metaItem: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
  },
  authorLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
  },
  statusBadge: {
    padding: '4px 12px',
    borderRadius: 20,
    fontSize: '12px',
    fontWeight: 600,
    color: '#fff',
  },
  authorActions: {
    marginTop: '16px',
    display: 'flex',
    gap: '12px',
  },
  input: {
    width: '100%',
    padding: '12px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    marginBottom: '12px',
  },
  inputSmall: {
    padding: '8px 12px',
    fontSize: '14px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    marginRight: '8px',
  },
  buttonGroup: {
    display: 'flex',
    gap: '12px',
    marginTop: '12px',
  },
  textarea: {
    width: '100%',
    height: '80px',
    padding: '12px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    marginBottom: '12px',
    resize: 'vertical',
  },
  select: {
    padding: '8px 12px',
    fontSize: '14px',
    backgroundColor: theme.select.backgroundColor,
    border: theme.select.border,
    borderRadius: theme.borderRadius.md,
    color: theme.select.color,
    outline: 'none',
    marginRight: '8px',
    cursor: 'pointer',
  },
  helpItem: {
    padding: '12px',
    marginBottom: '12px',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  reviewItem: {
    padding: '12px',
    marginBottom: '12px',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  reviewComment: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginTop: '8px',
  },
  reviewForm: {
    marginTop: '16px',
    paddingTop: '16px',
    borderTop: `1px solid ${theme.colors.border}`,
  },
  divider: {
    height: '1px',
    backgroundColor: theme.colors.border,
    margin: '24px 0',
  },
  commentItem: {
    padding: '16px',
    marginBottom: '12px',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  commentHeader: {
    marginBottom: '8px',
  },
  commentTime: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    marginLeft: '8px',
  },
  commentContent: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginTop: '8px',
  },
  replyBtn: {
    padding: '4px 10px',
    fontSize: '12px',
    marginLeft: '8px',
  },
  deleteBtn: {
    padding: '4px 10px',
    fontSize: '12px',
    marginLeft: '8px',
  },
  replyForm: {
    marginTop: '12px',
    marginLeft: '20px',
    display: 'flex',
    gap: '8px',
    alignItems: 'center',
  },
  emptyText: {
    color: theme.colors.textMuted,
    textAlign: 'center',
  },
};
