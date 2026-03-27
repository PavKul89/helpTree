import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { Card, Button, Spinner, EmptyState, Avatar, Modal } from '../components';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';

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

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'OPEN': return '#22c55e';
      case 'IN_PROGRESS': return '#f59e0b';
      case 'COMPLETED': return '#06b6d4';
      case 'CANCELLED': return '#ef4444';
      default: return '#6b7280';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'OPEN': return 'Открыт';
      case 'IN_PROGRESS': return 'В работе';
      case 'COMPLETED': return 'Завершён';
      case 'CANCELLED': return 'Отменён';
      default: return status;
    }
  };

  if (loading) return <Spinner message="Загрузка избранного..." />;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 className="page-title" style={styles.title}>Избранное</h1>

      <div style={styles.headerRow}>
        <div style={styles.info}>
          У вас {posts.length} избранных постов
        </div>
        {posts.length > 0 && (
          <Button onClick={clearAllFavorites} variant="danger" style={styles.clearBtn}>
            Удалить все
          </Button>
        )}
      </div>

      <div style={styles.grid}>
        {posts.map((post) => (
          <Link key={post.id} to={`/posts/${post.id}`} style={styles.cardLink}>
            <Card hoverable style={styles.postCard}>
              <div style={styles.postHeader}>
                <div style={styles.postHeaderLeft}>
                  <span style={{
                    ...styles.statusDot,
                    backgroundColor: getStatusColor(post.status)
                  }} />
                  <span style={styles.statusText}>{getStatusLabel(post.status)}</span>
                  <span style={styles.category}>{post.category}</span>
                </div>
                <button 
                  onClick={(e) => toggleFavorite(post.id, e)}
                  style={{
                    ...styles.favoriteBtn,
                    color: '#fbbf24',
                  }}
                  title="Убрать из избранного"
                >
                  ⭐
                </button>
              </div>
              <h3 style={styles.postTitle}>{post.title}</h3>
              <p style={styles.postDescription}>
                {post.description.length > 100 
                  ? post.description.substring(0, 100) + '...' 
                  : post.description}
              </p>
              <div style={styles.postFooter}>
                <div style={styles.author}>
                  <Avatar name={post.authorName} avatarUrl={post.authorAvatarUrl} size="small" showName withRating={post.authorRating} clickable userId={post.userId} />
                </div>
                <span style={styles.date}>
                  {getRelativeTime(post.createdAt)}
                </span>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      {posts.length === 0 && (
        <EmptyState 
          variant="posts"
          title="Избранное пусто" 
          description="Добавьте посты в избранное, нажав на ☆"
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
  container: {
    maxWidth: 1200,
    margin: '0 auto',
    padding: '24px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
    display: 'inline-block',
    marginBottom: '16px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
    margin: '0 0 16px 0',
  },
  info: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  headerRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '24px',
    flexWrap: 'wrap',
    gap: '12px',
  },
  clearBtn: {
    fontSize: '13px',
    padding: '8px 16px',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
    gap: '20px',
  },
  cardLink: {
    textDecoration: 'none',
  },
  postCard: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  },
  postHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '8px',
    marginBottom: '12px',
    flexWrap: 'wrap',
  },
  postHeaderLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  statusText: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  category: {
    color: theme.colors.accent,
    fontSize: '13px',
    background: 'rgba(34, 211, 238, 0.15)',
    padding: '4px 10px',
    borderRadius: '4px',
  },
  favoriteBtn: {
    background: 'transparent',
    border: 'none',
    fontSize: '20px',
    cursor: 'pointer',
    padding: '4px',
    transition: 'transform 0.2s',
  },
  postTitle: {
    color: theme.colors.text,
    fontSize: '18px',
    fontWeight: 600,
    margin: '0 0 10px 0',
  },
  postDescription: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    flex: 1,
    margin: '0 0 12px 0',
    lineHeight: 1.5,
  },
  postFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 'auto',
  },
  author: {
    display: 'flex',
    alignItems: 'center',
  },
  date: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
};