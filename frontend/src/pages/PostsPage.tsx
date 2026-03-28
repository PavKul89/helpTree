import { useEffect, useState, useCallback } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { Card, Button, Spinner, EmptyState, Avatar } from '../components';
import { useToast } from '../components/Toast';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';

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
const STATUSES = ['Все', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'Избранное'];

export const PostsPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [category, setCategory] = useState('Все');
  const [status, setStatus] = useState('Все');
  const [city, setCity] = useState('');
  const [cityInput, setCityInput] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [favorites, setFavorites] = useState<number[]>([]);
  const location = useLocation();
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [currentUserCity, setCurrentUserCity] = useState<string>('');
  const [showFilters, setShowFilters] = useState(false);
  const { showToast } = useToast();

  const loadPosts = useCallback(async (pageNum = 0, searchTerm?: string) => {
    try {
      const searchToUse = searchTerm !== undefined ? searchTerm : search;
      const params: any = { page: pageNum, size: 10 };
      if (searchToUse) params.title = searchToUse;
      if (category !== 'Все') params.category = category;
      if (status !== 'Все') params.status = status;
      if (city) params.city = city;
      const data = await postsApi.getAll(params);
      setPosts(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [search, category, status, city]);

  useEffect(() => {
    setSearch('');
    setSearchInput('');
    setCategory('Все');
    setStatus('Все');
    setCity('');
    setCityInput('');
    setPage(0);
    loadPosts(0, '');
    
    authApi.getCurrentUser()
      .then(user => {
        setCurrentUserId(user.id);
        if ('city' in user && user.city) {
          setCurrentUserCity(user.city);
        }
        return authApi.getFavorites(user.id);
      })
      .then(favs => setFavorites(favs))
      .catch(console.error);
  }, [location.key]);

  useEffect(() => {
    if (status === 'Избранное' && favorites.length > 0) {
      setLoading(true);
      postsApi.getByIds(favorites)
        .then(data => setPosts(data))
        .catch(console.error)
        .finally(() => setLoading(false));
    } else if (status !== 'Избранное') {
      loadPosts(page);
    }
  }, [status, page]);

  const handleStatusChange = (value: string) => {
    setStatus(value);
    if (value !== 'Избранное') {
      setLoading(true);
      loadPosts(0);
    }
  };

  const toggleFavorite = async (postId: number, e: React.MouseEvent, status: string) => {
    e.preventDefault();
    e.stopPropagation();
    if (!currentUserId) return;
    
    if (status !== 'OPEN') {
      showToast('Нельзя добавить в избранное пост, который уже в работе или завершён', 'info');
      return;
    }
    
    try {
      if (favorites.includes(postId)) {
        await authApi.removeFavorite(currentUserId, postId);
        setFavorites(favorites.filter(id => id !== postId));
      } else {
        await authApi.addFavorite(currentUserId, postId);
        setFavorites([...favorites, postId]);
      }
    } catch (err) {
      console.error('Error toggling favorite:', err);
    }
  };

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

  const handleCityChange = (value: string) => {
    setCity(value);
    setLoading(true);
    loadPosts(0);
  };

  const handleCityInputChange = (value: string) => {
    setCityInput(value);
  };

  const handleCitySearch = () => {
    setCity(cityInput);
    setLoading(true);
    loadPosts(0);
  };

  const handleMyCity = () => {
    setCity(currentUserCity);
    setCityInput(currentUserCity);
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
      <div style={styles.stickyContainer}>
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
          <button 
            onClick={() => setShowFilters(!showFilters)}
            style={{
              ...styles.filterToggle,
              ...(showFilters ? styles.filterToggleActive : {}),
            }}
          >
            ⚙️ Фильтры
          </button>
        </div>

        <div style={{
          ...styles.filterPanelWrapper,
          maxHeight: showFilters ? '500px' : '0',
          opacity: showFilters ? 1 : 0,
          marginTop: showFilters ? '12px' : '0',
          overflow: 'hidden',
          transition: 'all 0.3s ease',
        }}>
          <div style={styles.filterPanel}>
            <div style={styles.filterGroup}>
              <label style={styles.filterLabel}>Категория</label>
              <select
                value={category}
                onChange={(e) => handleCategoryChange(e.target.value)}
                style={styles.filterSelect}
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>{cat}</option>
                ))}
              </select>
            </div>
            <div style={styles.filterGroup}>
              <label style={styles.filterLabel}>Статус</label>
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
            <div style={styles.filterGroup}>
              <label style={styles.filterLabel}>Город</label>
              <div style={styles.cityFilter}>
                <input
                  type="text"
                  placeholder="Город..."
                  value={cityInput}
                  onChange={(e) => handleCityInputChange(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleCitySearch()}
                  style={styles.cityInput}
                />
                <Button onClick={handleCitySearch} style={styles.cityBtn}>ОК</Button>
                {currentUserCity && (
                  <Button onClick={handleMyCity} style={styles.myCityBtn}>Мой</Button>
                )}
              </div>
            </div>
            {(category !== 'Все' || status !== 'Все' || city) && (
              <button 
                onClick={() => {
                  setCategory('Все');
                  setStatus('Все');
                  setCity('');
                  setCityInput('');
                  loadPosts(0);
                }}
                style={styles.clearBtn}
              >
                ✕ Очистить
              </button>
            )}
          </div>
        </div>

        {(category !== 'Все' || status !== 'Все' || city) && !showFilters && (
          <div style={styles.activeFilters}>
            {category !== 'Все' && (
              <span style={styles.filterChip}>📁 {category}</span>
            )}
            {status !== 'Все' && (
              <span style={styles.filterChip}>● {getStatusLabel(status)}</span>
            )}
            {city && (
              <span style={styles.filterChip}>📍 {city}</span>
            )}
          </div>
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
                  {post.userCity && <span style={styles.city}>📍 {post.userCity}</span>}
                </div>
                <button 
                  onClick={(e) => toggleFavorite(post.id, e, post.status)}
                  style={{
                    ...styles.favoriteBtn,
                    color: favorites.includes(post.id) ? '#fbbf24' : 'rgba(255,255,255,0.5)',
                  }}
                  title={favorites.includes(post.id) ? "Убрать из избранного" : "В избранное"}
                >
                  {favorites.includes(post.id) ? '⭐' : '☆'}
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
  stickyContainer: {
    position: 'sticky',
    top: 0,
    zIndex: 100,
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.98) 0%, rgba(2, 44, 34, 0.95) 100%)',
    backdropFilter: 'blur(12px)',
    padding: '24px 24px 16px',
    margin: '-24px -24px 16px',
    borderBottom: '1px solid rgba(34, 211, 238, 0.2)',
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
  filterToggle: {
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.textSecondary,
    padding: '12px 20px',
    fontSize: '14px',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  filterToggleActive: {
    background: 'rgba(6, 182, 212, 0.2)',
    borderColor: 'rgba(6, 182, 212, 0.5)',
    color: theme.colors.accentLight,
  },
  filterPanelWrapper: {
    background: 'rgba(255,255,255,0.02)',
    borderRadius: theme.borderRadius.md,
  },
  filterPanel: {
    display: 'flex',
    gap: '16px',
    flexWrap: 'wrap',
    padding: '16px',
    background: 'rgba(255,255,255,0.04)',
    borderRadius: theme.borderRadius.md,
    alignItems: 'flex-end',
  },
  filterGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  filterLabel: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    fontWeight: 500,
    textTransform: 'uppercase',
  },
  filterSelect: {
    padding: '10px 36px 10px 14px',
    fontSize: '14px',
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    cursor: 'pointer',
    minWidth: '140px',
    appearance: 'none',
    backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2322d3ee' d='M6 8L1 3h10z'/%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 10px center',
  },
  clearBtn: {
    background: 'transparent',
    border: '1px solid rgba(239, 68, 68, 0.5)',
    borderRadius: theme.borderRadius.md,
    color: '#ef4444',
    padding: '10px 16px',
    fontSize: '14px',
    cursor: 'pointer',
    transition: 'all 0.2s',
    alignSelf: 'flex-end',
  },
  activeFilters: {
    display: 'flex',
    gap: '8px',
    flexWrap: 'wrap',
    marginTop: '12px',
  },
  filterChip: {
    background: 'rgba(6, 182, 212, 0.2)',
    border: '1px solid rgba(6, 182, 212, 0.4)',
    borderRadius: '20px',
    padding: '6px 14px',
    fontSize: '13px',
    color: theme.colors.accentLight,
  },
  cityFilter: {
    display: 'flex',
    gap: '8px',
    alignItems: 'center',
  },
  cityInput: {
    padding: '12px 16px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    width: '150px',
  },
  cityBtn: {
    padding: '12px 16px',
    whiteSpace: 'nowrap',
  },
  myCityBtn: {
    padding: '12px 16px',
    whiteSpace: 'nowrap',
    background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.2) 0%, rgba(5, 150, 105, 0.2) 100%)',
    border: '1px solid rgba(16, 185, 129, 0.4)',
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
  city: {
    color: theme.colors.accentLight,
    fontSize: '12px',
    fontWeight: 500,
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
    fontSize: '18px',
    cursor: 'pointer',
    padding: '4px',
    marginLeft: '8px',
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