import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';
import type { Post, Help } from '../types';

export const MyOrdersPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [myHelps, setMyHelps] = useState<Help[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'posts' | 'helps'>('posts');

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [user]);

  const loadData = async () => {
    if (!user) return;
    try {
      const posts = await postsApi.getByUser(user.id);
      setMyPosts(posts);
      const helps = await helpApi.getHelpsByHelper(user.id);
      setMyHelps(helps);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletePost = async (postId: number) => {
    if (!window.confirm('Вы уверены, что хотите удалить пост?')) return;
    try {
      await postsApi.delete(postId);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const getStatusColor = (status: string) => {
    const colors: Record<string, string> = {
      OPEN: '#10B981',
      ACCEPTED: '#38bdf8',
      COMPLETED: '#F59E0B',
      CONFIRMED: '#8B5CF6',
      CANCELLED: '#EF4444',
    };
    return colors[status] || '#6B7280';
  };

  const getStatusLabel = (status: string) => {
    const labels: Record<string, string> = {
      OPEN: 'Открыт',
      ACCEPTED: 'Принят',
      COMPLETED: 'Завершён',
      CONFIRMED: 'Подтверждён',
      CANCELLED: 'Отменён',
    };
    return labels[status] || status;
  };

  if (loading) return <div style={styles.loading}>Загрузка...</div>;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 style={styles.title}>Мои заказы</h1>

      <div style={styles.tabs}>
        <button
          onClick={() => setActiveTab('posts')}
          style={{
            ...styles.tab,
            ...(activeTab === 'posts' ? styles.tabActive : {}),
          }}
        >
          Мои посты ({myPosts.length})
        </button>
        <button
          onClick={() => setActiveTab('helps')}
          style={{
            ...styles.tab,
            ...(activeTab === 'helps' ? styles.tabActive : {}),
          }}
        >
          Мои отклики ({myHelps.length})
        </button>
      </div>

      {activeTab === 'posts' && (
        <div>
          {myPosts.length === 0 ? (
            <Card><p style={styles.emptyText}>У вас пока нет постов</p></Card>
          ) : (
            myPosts.map((post) => (
              <Card key={post.id} style={styles.itemCard}>
                <div style={styles.itemHeader}>
                  <Link to={`/posts/${post.id}`} style={styles.itemTitle}>{post.title}</Link>
                  <Button variant="danger" onClick={() => handleDeletePost(post.id)} style={styles.deleteBtn}>
                    Удалить
                  </Button>
                </div>
                <p style={styles.itemDesc}>{post.description}</p>
                <div style={styles.itemMeta}>
                  <span style={{ ...styles.statusBadge, backgroundColor: getStatusColor(post.status) }}>
                    {getStatusLabel(post.status)}
                  </span>
                  <span style={styles.category}>{post.category}</span>
                </div>
              </Card>
            ))
          )}
        </div>
      )}

      {activeTab === 'helps' && (
        <div>
          {myHelps.length === 0 ? (
            <Card><p style={styles.emptyText}>У вас пока нет откликов</p></Card>
          ) : (
            myHelps.map((help) => (
              <Card key={help.id} style={styles.itemCard}>
                <Link to={`/posts/${help.postId}`} style={styles.itemTitle}>{help.postTitle}</Link>
                <p style={styles.itemMeta}>Автор поста: {help.receiverName}</p>
                <span style={{ ...styles.statusBadge, backgroundColor: getStatusColor(help.status) }}>
                  {getStatusLabel(help.status)}
                </span>
              </Card>
            ))
          )}
        </div>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '24px',
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '200px',
    color: theme.colors.text,
    fontSize: '18px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    marginBottom: '24px',
  },
  tabs: {
    display: 'flex',
    gap: '12px',
    marginBottom: '24px',
  },
  tab: {
    padding: '12px 24px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    color: theme.colors.textSecondary,
    border: 'none',
    borderRadius: theme.borderRadius.md,
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 500,
    transition: 'all 0.2s',
  },
  tabActive: {
    backgroundColor: theme.colors.accent,
    color: '#fff',
  },
  itemCard: {
    marginBottom: '12px',
  },
  itemHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '8px',
  },
  itemTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    textDecoration: 'none',
  } as React.CSSProperties,
  itemDesc: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginBottom: '12px',
  },
  itemMeta: {
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  statusBadge: {
    padding: '4px 10px',
    borderRadius: 20,
    fontSize: '12px',
    fontWeight: 600,
    color: '#fff',
    marginRight: '8px',
  },
  category: {
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  deleteBtn: {
    padding: '6px 12px',
    fontSize: '12px',
  },
  emptyText: {
    color: theme.colors.textMuted,
    textAlign: 'center',
    margin: 0,
  },
};
