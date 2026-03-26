import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { Modal } from '../components/Modal';
import { theme } from '../theme';
import type { Post, Help } from '../types';

export const MyOrdersPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [myHelps, setMyHelps] = useState<Help[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'posts' | 'helps'>('posts');
  const [deletePostId, setDeletePostId] = useState<number | null>(null);

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

  const handleDeletePost = async () => {
    if (!deletePostId) return;
    try {
      await postsApi.delete(deletePostId);
      setDeletePostId(null);
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ru-RU', { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric' 
    });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'OPEN': return '#22c55e';
      case 'IN_PROGRESS': return '#38bdf8';
      case 'COMPLETED': return '#f59e0b';
      case 'CANCELLED': return '#ef4444';
      default: return '#6b7280';
    }
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

  if (loading) return <Spinner message="Загрузка заказов..." />;

  const openPosts = myPosts.filter(p => p.status === 'OPEN').length;
  const inProgressPosts = myPosts.filter(p => p.status === 'IN_PROGRESS').length;
  const completedPosts = myPosts.filter(p => p.status === 'COMPLETED').length;
  const completedHelps = myHelps.filter(h => h.status === 'COMPLETED').length;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        <div style={styles.header}>
          <h1 className="page-title" style={styles.title}>Мои заказы</h1>
          <Link to="/posts/new">
            <Button>Создать пост</Button>
          </Link>
        </div>

        <div style={styles.statsRow}>
          <div style={styles.statBox}>
            <div style={styles.statValue}>{myPosts.length}</div>
            <div style={styles.statLabel}>Всего постов</div>
          </div>
          <div style={styles.statBox}>
            <div style={{ ...styles.statValue, color: '#22c55e' }}>{openPosts}</div>
            <div style={styles.statLabel}>Открытых</div>
          </div>
          <div style={styles.statBox}>
            <div style={{ ...styles.statValue, color: '#38bdf8' }}>{inProgressPosts}</div>
            <div style={styles.statLabel}>В работе</div>
          </div>
          <div style={styles.statBox}>
            <div style={{ ...styles.statValue, color: '#f59e0b' }}>{completedPosts}</div>
            <div style={styles.statLabel}>Завершено</div>
          </div>
          <div style={styles.statBox}>
            <div style={{ ...styles.statValue, color: '#22d3ee' }}>{completedHelps}</div>
            <div style={styles.statLabel}>Помог</div>
          </div>
        </div>

        <div style={styles.tabs}>
          <button 
            style={activeTab === 'posts' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('posts')}
          >
            Мои посты ({myPosts.length})
          </button>
          <button 
            style={activeTab === 'helps' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('helps')}
          >
            Мои отклики ({myHelps.length})
          </button>
        </div>

        <div style={styles.content}>
          {activeTab === 'posts' && (
            myPosts.length === 0 ? (
              <div style={styles.emptyState}>
                <div style={styles.emptyIcon}>📝</div>
                <div style={styles.emptyTitle}>У вас пока нет постов</div>
                <div style={styles.emptyDesc}>Создайте первый пост о помощи</div>
                <Link to="/posts/new" style={{ marginTop: '16px', display: 'inline-block' }}>
                  <Button>Создать пост</Button>
                </Link>
              </div>
            ) : (
              <div style={styles.list}>
                {myPosts.map((post) => (
                  <Link key={post.id} to={`/posts/${post.id}`} style={styles.listItem}>
                    <div style={styles.itemMain}>
                      <div style={styles.itemHeader}>
                        <span style={{
                          ...styles.statusDot,
                          backgroundColor: getStatusColor(post.status)
                        }} />
                        <span style={styles.statusText}>{getStatusLabel(post.status)}</span>
                      </div>
                      <div style={styles.itemTitle}>{post.title}</div>
                      <div style={styles.itemDesc}>
                        {post.description.length > 120 
                          ? post.description.slice(0, 120) + '...' 
                          : post.description}
                      </div>
                    </div>
                    <div style={styles.itemSide}>
                      <span style={styles.itemCategory}>{post.category}</span>
                      <span style={styles.itemDate}>{formatDate(post.createdAt)}</span>
                      <Button 
                        variant="danger" 
                        onClick={(e) => { e.preventDefault(); confirmDeletePost(post.id); }}
                        style={styles.deleteBtn}
                      >
                        Удалить
                      </Button>
                    </div>
                  </Link>
                ))}
              </div>
            )
          )}

          {activeTab === 'helps' && (
            myHelps.length === 0 ? (
              <div style={styles.emptyState}>
                <div style={styles.emptyIcon}>🤝</div>
                <div style={styles.emptyTitle}>У вас пока нет откликов</div>
                <div style={styles.emptyDesc}>Откликнитесь на пост другого пользователя</div>
                <Link to="/" style={{ marginTop: '16px', display: 'inline-block' }}>
                  <Button>Найти посты</Button>
                </Link>
              </div>
            ) : (
              <div style={styles.list}>
                {myHelps.map((help) => (
                  <Link 
                    key={help.id} 
                    to={`/posts/${help.postId}`}
                    style={styles.listItem}
                  >
                    <div style={styles.itemMain}>
                      <div style={styles.itemHeader}>
                        <span style={{
                          ...styles.statusDot,
                          backgroundColor: getStatusColor(help.status)
                        }} />
                        <span style={styles.statusText}>{getStatusLabel(help.status)}</span>
                      </div>
                      <div style={styles.itemTitle}>{help.postTitle}</div>
                      <div style={styles.itemMeta}>
                        Автор: <span style={styles.metaValue}>{help.receiverName}</span>
                      </div>
                    </div>
                    <div style={styles.itemSide}>
                      <span style={styles.itemDate}>{formatDate(help.acceptedAt)}</span>
                    </div>
                  </Link>
                ))}
              </div>
            )
          )}
        </div>
      </Card>

      <Modal
        isOpen={!!deletePostId}
        onClose={() => setDeletePostId(null)}
        onConfirm={handleDeletePost}
        title="Удаление поста"
        message="Вы уверены, что хотите удалить этот пост? Это действие нельзя отменить."
        confirmText="Удалить"
        cancelText="Отмена"
      />
    </div>
  );

  function confirmDeletePost(postId: number) {
    setDeletePostId(postId);
  }
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 900,
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
  mainCard: {
    padding: '0',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '24px 32px',
    background: 'linear-gradient(180deg, rgba(34, 211, 238, 0.08) 0%, transparent 100%)',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  title: {
    color: theme.colors.text,
    fontSize: '24px',
    fontWeight: 700,
    margin: 0,
  },
  statsRow: {
    display: 'flex',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  statBox: {
    flex: 1,
    padding: '20px',
    textAlign: 'center',
    borderRight: `1px solid ${theme.colors.border}`,
  },
  statValue: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
  },
  statLabel: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    marginTop: '4px',
  },
  tabs: {
    display: 'flex',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  tab: {
    flex: 1,
    padding: '16px',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid transparent',
    color: theme.colors.textMuted,
    fontSize: '15px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  tabActive: {
    flex: 1,
    padding: '16px',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid theme.colors.accent',
    color: theme.colors.accent,
    fontSize: '15px',
    fontWeight: 600,
    cursor: 'pointer',
  },
  content: {
    padding: '24px 32px',
  },
  emptyState: {
    textAlign: 'center',
    padding: '48px 24px',
  },
  emptyIcon: {
    fontSize: '48px',
    marginBottom: '16px',
  },
  emptyTitle: {
    color: theme.colors.text,
    fontSize: '18px',
    fontWeight: 600,
    marginBottom: '8px',
  },
  emptyDesc: {
    color: theme.colors.textMuted,
    fontSize: '15px',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  listItem: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '20px',
    background: 'rgba(255,255,255,0.04)',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    textDecoration: 'none',
    transition: 'all 0.2s ease',
    position: 'relative',
  },
  itemMain: {
    flex: 1,
    minWidth: 0,
  },
  itemHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    marginBottom: '8px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  statusText: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    fontWeight: 500,
    textTransform: 'uppercase',
  },
  itemTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '6px',
  },
  itemDesc: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    lineHeight: 1.5,
  },
  itemMeta: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  metaValue: {
    color: theme.colors.accent,
    fontWeight: 500,
  },
  itemSide: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-end',
    gap: '8px',
    marginLeft: '24px',
    flexShrink: 0,
  },
  itemCategory: {
    color: theme.colors.accent,
    fontSize: '13px',
    fontWeight: 500,
    background: 'rgba(34, 211, 238, 0.15)',
    padding: '4px 10px',
    borderRadius: '4px',
  },
  itemDate: {
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  deleteBtn: {
    padding: '8px 14px',
    fontSize: '13px',
  },
};
