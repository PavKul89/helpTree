import { useEffect, useState, useCallback } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import type { Post } from '../types';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { EmptyState } from '../components/EmptyState';
import { Avatar } from '../components/Avatar';
import { theme } from '../theme';

const CATEGORIES = [
  'Все',
  'Дрова',
  'Уборка',
  'Ремонт',
  'Доставка',
  'Покупки',
  'Готовка',
  'Садоводство',
  'Перевозка',
  'Уход за животными',
  'Помощь с детьми',
  'Компьютерная помощь',
  'Стрижка',
  'Медицинская помощь',
  'Юридическая консультация',
  'Обучение',
  'Репетитор',
  'Транспорт',
  'Строительство',
  'Клининг',
  'Курьер',
  'Волонтёрство',
  'Психологическая помощь',
  'Интернет и связь',
  'Фото и видео',
  'Музыка',
  'Искусство',
  'Спорт',
  'Путешествия',
  'Питомцы',
  'Бытовая техника',
  'Одежда и обувь',
  'Продукты',
  'Аптека',
  'Банковские услуги',
  'Страхование',
  'Недвижимость',
  'Другое',
];
const STATUSES = ['Все', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

export const PostsPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [category, setCategory] = useState('Все');
  const [status, setStatus] = useState('Все');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const location = useLocation();

  const loadPosts = useCallback(async (pageNum = 0, searchTerm?: string) => {
    try {
      const searchToUse = searchTerm !== undefined ? searchTerm : search;
      const params: any = { page: pageNum, size: 10 };
      if (searchToUse) params.title = searchToUse;
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
  }, [search, category, status]);

  useEffect(() => {
    setSearch('');
    setSearchInput('');
    setCategory('Все');
    setStatus('Все');
    setPage(0);
    loadPosts(0, '');
  }, [location.key]);

  const handleSearch = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setSearch(searchInput);
    setLoading(true);
    loadPosts(0, searchInput);
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

  if (loading) return <Spinner message="Загрузка постов..." />;

  return (
    <div style={styles.container}>
      <header className="page-header" style={styles.header}>
        <h1 className="page-title" style={styles.title}>Посты о помощи</h1>
        <Link to="/posts/new">
          <Button>+ Создать пост</Button>
        </Link>
      </header>

      <div style={styles.filters}>
        <div style={styles.searchForm}>
          <input
            type="text"
            placeholder="🔍 Поиск..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            style={styles.searchInput}
          />
          <Button onClick={handleSearch} style={styles.searchBtn}>Найти</Button>
        </div>
        <select
          value={category}
          onChange={(e) => handleCategoryChange(e.target.value)}
          style={styles.filterSelect}
        >
          {CATEGORIES.map((cat) => (
            <option key={cat} value={cat}>{cat}</option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => handleStatusChange(e.target.value)}
          style={styles.filterSelect}
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s === 'Все' ? 'Все статусы' : getStatusLabel(s)}</option>
          ))}
        </select>
      </div>

      <div style={styles.grid}>
        {posts.map((post) => (
          <Link key={post.id} to={`/posts/${post.id}`} style={styles.cardLink}>
            <Card hoverable style={styles.postCard}>
              <div style={styles.postHeader}>
                <span style={{
                  ...styles.statusDot,
                  backgroundColor: getStatusColor(post.status)
                }} />
                <span style={styles.statusText}>{getStatusLabel(post.status)}</span>
                <span style={styles.category}>{post.category}</span>
              </div>
              <h3 style={styles.postTitle}>{post.title}</h3>
              <p style={styles.postDescription}>
                {post.description.length > 100 
                  ? post.description.substring(0, 100) + '...' 
                  : post.description}
              </p>
              <div style={styles.postFooter}>
                <div style={styles.author}>
                  <Avatar name={post.authorName} avatarUrl={post.authorAvatarUrl} size="small" showName withRating={post.authorRating} />
                </div>
                <span style={styles.date}>
                  {new Date(post.createdAt).toLocaleDateString('ru-RU')}
                </span>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      {posts.length === 0 && (
        <EmptyState 
          variant="posts"
          title="Постов пока нет" 
          description="Будьте первым, кто создаст пост о помощи!"
          action={
            <Link to="/posts/new">
              <Button>+ Создать пост</Button>
            </Link>
          }
        />
      )}

      {totalPages > 1 && (
        <div style={styles.pagination}>
          <Button
            variant="outline"
            disabled={page === 0}
            onClick={() => handlePageChange(page - 1)}
          >
            ← Назад
          </Button>
          <span style={styles.pageInfo}>
            Страница {page + 1} из {totalPages}
          </span>
          <Button
            variant="outline"
            disabled={page >= totalPages - 1}
            onClick={() => handlePageChange(page + 1)}
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
    maxWidth: 1200,
    margin: '0 auto',
    padding: '24px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '24px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
    margin: 0,
  },
  filters: {
    display: 'flex',
    gap: '12px',
    marginBottom: '24px',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  searchForm: {
    display: 'flex',
    gap: '8px',
    flex: 1,
    order: 1,
    minWidth: '200px',
  },
  searchInput: {
    flex: 1,
    padding: '12px 16px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
  },
  searchBtn: {
    padding: '12px 20px',
    whiteSpace: 'nowrap',
  },
  filterSelect: {
    padding: '12px 40px 12px 16px',
    fontSize: '15px',
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.15) 0%, rgba(8, 145, 178, 0.15) 100%)',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    borderRadius: theme.borderRadius.lg,
    color: theme.colors.text,
    cursor: 'pointer',
    minWidth: '150px',
    appearance: 'none',
    backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2322d3ee' d='M6 8L1 3h10z'/%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 12px center',
    transition: 'all 0.2s ease',
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
    gap: '8px',
    marginBottom: '12px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  statusText: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    textTransform: 'uppercase',
    fontWeight: 500,
  },
  category: {
    marginLeft: 'auto',
    color: theme.colors.accent,
    fontSize: '13px',
    background: 'rgba(34, 211, 238, 0.15)',
    padding: '4px 10px',
    borderRadius: '4px',
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
    lineHeight: 1.5,
    flex: 1,
    margin: 0,
  },
  postFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: '16px',
    paddingTop: '16px',
    borderTop: `1px solid ${theme.colors.border}`,
  },
  author: {
    display: 'flex',
    alignItems: 'center',
  },
  date: {
    color: theme.colors.textMuted,
    fontSize: '13px',
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