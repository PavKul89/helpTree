import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bookmark } from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { Card, Button, Spinner, EmptyState, Avatar, Modal } from '../components';
import { getStatusColor, getStatusLabel } from '../components/StatusBadge';
import { getRelativeTime } from '../utils/dateUtils';
import './FavoritesPage.css';

export const FavoritesPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [favorites, setFavorites] = useState<number[]>([]);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [showClearModal, setShowClearModal] = useState(false);

  useEffect(() => {
    authApi.getCurrentUser()
      .then(user => {
        setCurrentUserId(user.id);
        return authApi.getFavorites(user.id);
      })
      .then(favs => {
        setFavorites(favs);
        if (favs.length > 0) {
          return postsApi.getByIds(favs);
        }
        return [];
      })
      .then(data => setPosts(data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const toggleFavorite = async (postId: number, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!currentUserId) return;
    
    try {
      if (favorites.includes(postId)) {
        await authApi.removeFavorite(currentUserId, postId);
        setFavorites(favorites.filter(id => id !== postId));
        setPosts(posts.filter(p => p.id !== postId));
      }
    } catch (err) {
      console.error('Error toggling favorite:', err);
    }
  };

  const clearAllFavorites = async () => {
    setShowClearModal(true);
  };

  const handleClearConfirm = async () => {
    if (!currentUserId || favorites.length === 0) return;
    
    try {
      for (const postId of favorites) {
        await authApi.removeFavorite(currentUserId, postId);
      }
      setFavorites([]);
      setPosts([]);
    } catch (err) {
      console.error('Error clearing favorites:', err);
    } finally {
      setShowClearModal(false);
    }
  };

  if (loading) return <Spinner message="Загрузка избранного..." />;

  return (
    <div className="favorites-page">
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 className="favorites-title">Избранное</h1>

      <div style={styles.headerRow}>
        <div style={styles.info}>
          У вас {posts.length} избранных постов
        </div>
        {posts.length > 0 && (
          <Button
            onClick={clearAllFavorites}
            variant="danger"
            className="favorites-clear-button"
          >
            Удалить все
          </Button>
        )}
      </div>

      <div style={styles.grid}>
        {posts.map((post) => (
          <article key={post.id} style={styles.cardLink}>
            <Card style={styles.postCard}>
              <div style={styles.postHeader}>
                <div style={styles.statusChip}>
                  <span style={{
                    ...styles.statusDot,
                    backgroundColor: getStatusColor(post.status)
                  }} />
                  <span style={styles.statusText}>{getStatusLabel(post.status)}</span>
                  <span style={styles.category}>{post.category}</span>
                </div>
                <button
                  type="button"
                  onClick={(e) => toggleFavorite(post.id, e)}
                  style={styles.favoriteBtn}
                  title="Убрать из избранного"
                  aria-label={`Убрать заявку «${post.title}» из избранного`}
                >
                  <Bookmark size={16} fill="currentColor" aria-hidden="true" />
                </button>
              </div>
              <Link to={`/posts/${post.id}`} style={styles.postTitleLink}>
                <h3 style={styles.postTitle}>{post.title}</h3>
              </Link>
              <p style={styles.postDescription}>
                {post.description.length > 100 
                  ? post.description.substring(0, 100) + '...' 
                  : post.description}
              </p>
              <div style={styles.postFooter}>
                <div style={styles.author}>
                  <Avatar
                    name={post.authorName}
                    avatarUrl={post.authorAvatarUrl}
                    size="small"
                    showName
                    withRating={post.authorRating}
                    clickable
                    userId={post.userId}
                    tone="forest"
                  />
                </div>
                <span style={styles.date}>
                  {getRelativeTime(post.createdAt)}
                </span>
              </div>
            </Card>
          </article>
        ))}
      </div>

      {posts.length === 0 && (
        <EmptyState 
          variant="posts"
          title="Избранное пусто" 
          description="Добавьте посты в избранное с помощью значка закладки"
          action={
            <Link to="/">
              <Button>К постам</Button>
            </Link>
          }
        />
      )}

      <Modal
        isOpen={showClearModal}
        onClose={() => setShowClearModal(false)}
        onConfirm={handleClearConfirm}
        title="Удалить все из избранного?"
        message="Вы уверены, что хотите удалить все посты из избранного? Это действие нельзя отменить."
        confirmText="Удалить все"
        cancelText="Отмена"
        variant="danger"
      />
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  backLink: {
    color: '#dfeee0',
    textDecoration: 'none',
    fontSize: '15px',
    display: 'inline-block',
    marginBottom: '16px',
    opacity: 0.9,
  },
  info: {
    color: '#b7c5b8',
    fontSize: '15px',
    margin: 0,
  },
  headerRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '26px',
    flexWrap: 'wrap',
    gap: '12px',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
    gap: '22px',
    alignItems: 'stretch',
  },
  cardLink: {
    minWidth: 0,
    height: '100%',
  },
  postCard: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    background: 'rgba(12, 35, 27, 0.84)',
    border: '1px solid rgba(160, 202, 160, 0.16)',
    borderRadius: '28px',
    boxShadow: '0 10px 30px rgba(0, 0, 0, 0.18)',
    padding: '16px 18px 18px',
  },
  postHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '8px',
    marginBottom: '14px',
  },
  statusChip: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    minHeight: '36px',
    padding: '6px 10px',
    borderRadius: '12px',
    background: 'rgba(178, 232, 134, 0.05)',
    border: '1px solid rgba(178, 232, 134, 0.08)',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    boxShadow: '0 0 10px rgba(200, 245, 106, 0.35)',
  },
  statusText: {
    color: '#dfeee0',
    fontSize: '13px',
    fontWeight: 600,
  },
  category: {
    color: '#c8f56a',
    fontSize: '13px',
    fontWeight: 600,
    background: 'rgba(200, 245, 106, 0.07)',
    padding: '4px 8px',
    borderRadius: '8px',
    marginLeft: '2px',
  },
  favoriteBtn: {
    display: 'grid',
    width: 36,
    height: 36,
    flex: '0 0 36px',
    placeItems: 'center',
    background: 'rgba(200, 245, 106, 0.06)',
    border: '1px solid rgba(178, 232, 134, 0.12)',
    borderRadius: '10px',
    color: '#dffb8a',
    fontSize: '18px',
    padding: 0,
    transition: 'transform 0.18s ease, background 0.18s ease',
  },
  postTitleLink: {
    color: 'inherit',
    textDecoration: 'none',
  },
  postTitle: {
    color: '#f3f1e8',
    fontSize: 'clamp(1.3rem, 2vw, 2.1rem)',
    fontWeight: 700,
    margin: '0 0 10px 0',
    letterSpacing: '-0.05em',
    lineHeight: 1.2,
  },
  postDescription: {
    color: '#c9d8cb',
    fontSize: '14px',
    flex: 1,
    margin: '0 0 14px 0',
    lineHeight: 1.55,
    minHeight: '44px',
  },
  postFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 'auto',
    gap: '10px',
  },
  author: {
    display: 'flex',
    alignItems: 'center',
    minWidth: 0,
  },
  date: {
    color: '#b7c5b8',
    fontSize: '12px',
    whiteSpace: 'nowrap',
  },
};