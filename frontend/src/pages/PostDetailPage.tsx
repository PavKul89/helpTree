import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { reviewApi } from '../api/reviewApi';
import { imagesApi } from '../api/imagesApi';
import type { Post, Comment, Help, Review } from '../types';
import { useAuth } from '../context/AuthContext';

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
    if (!window.confirm('Вы уверены, что хотите удалить пост?')) return;
    try {
      await postsApi.delete(Number(id));
      navigate('/');
    } catch (err) {
      console.error(err);
    }
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

  const handleDeleteImage = async (url: string) => {
    if (!window.confirm('Удалить это изображение?')) return;
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

  if (loading) return <div>Загрузка...</div>;
  if (!post) return <div>Пост не найден</div>;

  const isAuthor = user?.id === post.userId;
  const canOfferHelp = user && !isAuthor && post.status === 'OPEN';

  return (
    <div style={{ padding: 20 }}>
      <Link to="/">← Назад</Link>
      
      {isEditing ? (
        <div style={{ marginTop: 20 }}>
          <input
            type="text"
            value={editForm.title}
            onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
            style={{ width: '100%', padding: 8, marginBottom: 10 }}
          />
          <textarea
            value={editForm.description}
            onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
            style={{ width: '100%', height: 100, padding: 8 }}
          />
          <button onClick={handleEditPost} style={{ marginRight: 10 }}>Сохранить</button>
          <button onClick={() => setIsEditing(false)}>Отмена</button>
        </div>
      ) : (
        <>
          <h1>{post.title}</h1>
          <p>{post.description}</p>
        </>
      )}
      
      {post.imageUrls && post.imageUrls.length > 0 ? (
        <div style={{ marginTop: 15 }}>
          <h3>Изображения ({post.imageUrls.length})</h3>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {post.imageUrls.map((img, idx) => (
              <div key={idx} style={{ position: 'relative' }}>
                <img 
                  src={img} 
                  alt={`Изображение ${idx + 1}`} 
                  style={{ maxWidth: 200, maxHeight: 200, borderRadius: 8, cursor: 'pointer' }}
                  onClick={() => window.open(img, '_blank')}
                />
                {isAuthor && (
                  <button 
                    onClick={() => handleDeleteImage(img)}
                    style={{
                      position: 'absolute',
                      top: -5,
                      right: -5,
                      background: 'red',
                      color: 'white',
                      border: 'none',
                      borderRadius: '50%',
                      width: 20,
                      height: 20,
                      cursor: 'pointer',
                    }}
                  >
                    ×
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      ) : null}
      
      <div style={{ margin: '15px 0' }}>
        <strong>Категория:</strong> {post.category} |
        <strong> Статус:</strong> {post.status} |
        <strong> Автор:</strong> <Link to={`/profile/${post.userId}`}>{post.authorName}</Link>
        {isAuthor && (
          <>
            <button onClick={startEdit} style={{ marginLeft: 10, backgroundColor: '#2196F3', color: 'white', border: 'none', padding: '5px 10px', borderRadius: 4, cursor: 'pointer' }}>
              Редактировать
            </button>
            <button onClick={handleDeletePost} style={{ marginLeft: 10, backgroundColor: '#f44336', color: 'white', border: 'none', padding: '5px 10px', borderRadius: 4, cursor: 'pointer' }}>
              Удалить пост
            </button>
          </>
        )}
      </div>

      {canOfferHelp && (
        <button onClick={handleOfferHelp} style={{ marginBottom: 20, padding: '10px 20px' }}>
          Откликнуться на пост
        </button>
      )}

      {helps.length > 0 && (
        <div style={{ marginBottom: 20 }}>
          <h3>Отклики</h3>
          {helps.map((help) => (
            <div key={help.id} style={{ border: '1px solid #ddd', padding: 10, margin: '10px 0', borderRadius: 4 }}>
              <strong>{help.helperName}</strong> — статус: {help.status}
              {help.status === 'ACCEPTED' && !isAuthor && help.helperId === user?.id && (
                <button onClick={() => handleCompleteHelp(help.id)} style={{ marginLeft: 10 }}>
                  Выполнено
                </button>
              )}

              {help.status === 'COMPLETED' && isAuthor && (
                <button onClick={() => handleConfirmHelp(help.id)} style={{ marginLeft: 10 }}>
                  Подтвердить выполнение
                </button>
              )}
              {(help.status === 'ACCEPTED' || help.status === 'PENDING') && !isAuthor && help.helperId === user?.id && (
                <button onClick={() => handleCancelHelp(help.id)} style={{ marginLeft: 10 }}>
                  Отменить
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {helps.some(h => h.status === 'CONFIRMED') && (
        <div style={{ marginBottom: 20, padding: 15, backgroundColor: '#e8f5e9', borderRadius: 8 }}>
          <h3>Отзывы</h3>
          {reviews.length > 0 ? reviews.map((review) => (
            <div key={review.id} style={{ border: '1px solid #ddd', padding: 10, margin: '10px 0', borderRadius: 4 }}>
              <strong>{review.fromUserName}</strong> оценил <strong>{review.toUserName}</strong> на {review.rating} звёзд
              {review.comment && <p>{review.comment}</p>}
            </div>
          )) : <p>Отзывов пока нет</p>}
          
          {user && (
            <div style={{ marginTop: 15, padding: 15, backgroundColor: '#f5f5f5', borderRadius: 8 }}>
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
                    <div key={help.id} style={{ marginBottom: 10 }}>
                      <p>Оценить пользователя: <strong>{toUserName}</strong></p>
                      <select 
                        value={reviewRating} 
                        onChange={(e) => setReviewRating(Number(e.target.value))}
                        style={{ marginRight: 10 }}
                      >
                        {[1,2,3,4,5].map(r => <option key={r} value={r}>{r} звёзд</option>)}
                      </select>
                      <input
                        type="text"
                        value={reviewComment}
                        onChange={(e) => setReviewComment(e.target.value)}
                        placeholder="Комментарий (необязательно)"
                        style={{ marginRight: 10, width: 200 }}
                      />
                      <button onClick={() => handleSubmitReview(help.id, toUserId)}>
                        Отправить отзыв
                      </button>
                    </div>
                  );
                });
              })()}
            </div>
          )}
        </div>
      )}

      <hr />

      <h3>Комментарии ({comments.length})</h3>
      
      {user && (
        <div style={{ marginBottom: 20 }}>
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="Написать комментарий..."
            style={{ width: '100%', height: 80 }}
          />
          <button onClick={handleAddComment}>Отправить</button>
        </div>
      )}

      <div>
        {comments.map((comment) => (
          <div key={comment.id} style={{ borderBottom: '1px solid #eee', padding: '10px 0', marginLeft: comment.parentCommentId ? 20 : 0 }}>
            <strong>{comment.userName}</strong>
            <span style={{ color: '#666', fontSize: 12 }}> — {new Date(comment.createdAt).toLocaleString()}</span>
            {user && (
              <button onClick={() => setReplyingTo(comment.id)} style={{ marginLeft: 10, backgroundColor: '#2196F3', color: 'white', border: 'none', padding: '2px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
                Ответить
              </button>
            )}
            {user?.id === comment.userId && (
              <button onClick={() => handleDeleteComment(comment.id)} style={{ marginLeft: 5, backgroundColor: '#f44336', color: 'white', border: 'none', padding: '2px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
                Удалить
              </button>
            )}
            <p>{comment.content}</p>
            {replyingTo === comment.id && (
              <div style={{ marginTop: 5, marginLeft: 20 }}>
                <input
                  type="text"
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  placeholder="Написать ответ..."
                  style={{ width: '70%', padding: 5, marginRight: 5 }}
                />
                <button onClick={() => handleReply(comment.id)} style={{ padding: '5px 10px' }}>Отправить</button>
                <button onClick={() => { setReplyingTo(null); setReplyContent(''); }} style={{ marginLeft: 5, padding: '5px 10px' }}>Отмена</button>
              </div>
            )}
          </div>
        ))}
        {comments.length === 0 && <p>Комментариев пока нет</p>}
      </div>
    </div>
  );
};
