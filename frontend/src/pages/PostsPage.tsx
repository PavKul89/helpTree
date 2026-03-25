import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import type { Post } from '../types';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';

const CATEGORIES = ['Все', 'Дрова', 'Уборка', 'Ремонт', 'Доставка', 'Покупки', 'Другое'];
const STATUSES = ['Все', 'OPEN', 'ACCEPTED', 'COMPLETED', 'CONFIRMED', 'CANCELLED'];

export const PostsPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('Все');
  const [status, setStatus] = useState('Все');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    loadPosts();
  }, []);

  const loadPosts = async (pageNum = 0) => {
    try {
      const params: any = { page: pageNum, size: 10 };
      if (search) params.title = search;
      if (category !== 'Все') params.category = category;
      if (status !== 'Все') params.status = status;
      const data = await postsApi.getAll(params);
      setPosts(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    loadPosts(0);
  };

  const handleCategoryChange = (value: string) => {
    setCategory(value);
    setLoading(true);
    loadPosts(0);
  };

  const handleStatusChange = (value: string) => {
    setStatus(value);
    setLoading(true);
    loadPosts(0);
  };

  const handlePageChange = (newPage: number) => {
    setLoading(true);
    loadPosts(newPage);
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
      <header style={styles.header}>
        <h1 style={styles.title}>Посты о помощи</h1>
        <div style={styles.headerActions}>
          <Link to="/posts/new">
            <Button>+ Создать пост</Button>
          </Link>
        </div>
      </header>

      <Card style={styles.filterCard}>
        <form onSubmit={handleSearch} style={styles.filterForm}>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Поиск по названию..."
            style={styles.searchInput}
          />
          <select 
            value={category} 
            onChange={(e) => handleCategoryChange(e.target.value)} 
            style={styles.select}
          >
            {CATEGORIES.map(cat => (
              <option key={cat} value={cat}>{cat === 'Все' ? 'Все категории' : cat}</option>
            ))}
          </select>
          <select 
            value={status} 
            onChange={(e) => handleStatusChange(e.target.value)} 
            style={styles.select}
          >
            {STATUSES.map(s => (
              <option key={s} value={s}>{s === 'Все' ? 'Все статусы' : getStatusLabel(s)}</option>
            ))}
          </select>
          <Button type="submit">Найти</Button>
        </form>
      </Card>

      <div style={styles.grid}>
        {posts.map((post) => (
          <Link key={post.id} to={`/posts/${post.id}`} style={styles.cardLink}>
            <Card hoverable style={styles.postCard}>
              <div style={styles.postHeader}>
                <span 
                  style={{
                    ...styles.status,
                    backgroundColor: getStatusColor(post.status),
                  }}
                >
                  {getStatusLabel(post.status)}
                </span>
                <span style={styles.category}>{post.category}</span>
              </div>
              <h3 style={styles.postTitle}>{post.title}</h3>
              <p style={styles.postDescription}>
                {post.description.length > 100 
                  ? post.description.substring(0, 100) + '...' 
                  : post.description}
              </p>
              <div style={styles.postFooter}>
                <span style={styles.author}>
                  👤 {post.authorName}
                  <span style={styles.rating}>★ {post.authorRating}</span>
                </span>
                <span style={styles.date}>
                  {new Date(post.createdAt).toLocaleDateString('ru-RU')}
                </span>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      {posts.length === 0 && (
        <Card style={styles.empty}>
          <p style={styles.emptyText}>Постов не найдено</p>
        </Card>
      )}

      {totalPages > 1 && (
        <div style={styles.pagination}>
          <Button
            variant="outline"
            onClick={() => handlePageChange(page - 1)}
            disabled={page === 0}
          >
            ← Назад
          </Button>
          <span style={styles.pageInfo}>
            Страница {page + 1} из {totalPages}
          </span>
          <Button
            variant="outline"
            onClick={() => handlePageChange(page + 1)}
            disabled={page >= totalPages - 1}
          >
            Вперёд →
          </Button>
        </div>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '24px',
    position: 'relative',
    zIndex: 1,
  },
  loading: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '12px',
    height: '200px',
    color: theme.colors.text,
    fontSize: '16px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '24px',
    flexWrap: 'wrap',
    gap: '16px',
  },
  title: {
    fontSize: '28px',
    fontWeight: 700,
    color: theme.colors.text,
    margin: 0,
  },
  headerActions: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  filterCard: {
    marginBottom: '24px',
  },
  filterForm: {
    display: 'flex',
    gap: '12px',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  searchInput: {
    flex: '1',
    minWidth: '200px',
    padding: '12px 16px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
  },
  select: {
    padding: '12px 16px',
    fontSize: '15px',
    backgroundColor: theme.select.backgroundColor,
    border: theme.select.border,
    borderRadius: theme.borderRadius.md,
    color: theme.select.color,
    outline: 'none',
    cursor: 'pointer',
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
    transition: 'all 0.3s ease',
  },
  postHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '12px',
  },
  status: {
    padding: '4px 10px',
    borderRadius: '20px',
    fontSize: '12px',
    fontWeight: 600,
    color: '#fff',
  },
  category: {
    fontSize: '13px',
    color: theme.colors.textMuted,
    fontWeight: 500,
  },
  postTitle: {
    fontSize: '18px',
    fontWeight: 600,
    color: theme.colors.text,
    margin: '0 0 8px 0',
  },
  postDescription: {
    fontSize: '14px',
    color: theme.colors.textSecondary,
    lineHeight: 1.5,
    margin: '0 0 16px 0',
    flex: 1,
  },
  postFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: '12px',
    borderTop: `1px solid ${theme.colors.border}`,
  },
  author: {
    fontSize: '13px',
    color: theme.colors.textSecondary,
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  rating: {
    color: '#FBBF24',
    fontWeight: 600,
  },
  date: {
    fontSize: '12px',
    color: theme.colors.textMuted,
  },
  empty: {
    textAlign: 'center',
    padding: '40px',
  },
  emptyText: {
    color: theme.colors.textMuted,
    fontSize: '16px',
    margin: 0,
  },
  pagination: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    gap: '16px',
    marginTop: '32px',
  },
  pageInfo: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
  },
};
