import { useEffect, useState, useCallback } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { Card, Button, Spinner, EmptyState, Avatar } from '../components';
import { useToast } from '../components/Toast';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';

type PostStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

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

const CATEGORY_ICONS: Record<string, string> = {
  'Дрова': '🪓',
  'Уборка': '🧹',
  'Ремонт': '🔧',
  'Доставка': '📦',
  'Покупки': '🛒',
  'Готовка': '🍳',
  'Садоводство': '🌱',
  'Перевозка': '🚚',
  'Уход за животными': '🐾',
  'Помощь с детьми': '👶',
  'Компьютерная помощь': '💻',
  'Стрижка': '✂️',
  'Медицинская помощь': '🏥',
  'Юридическая консультация': '⚖️',
  'Обучение': '📚',
  'Репетитор': '🎓',
  'Транспорт': '🚗',
  'Строительство': '🏠',
  'Клининг': '🧽',
  'Курьер': '🏃',
  'Волонтёрство': '❤️',
  'Психологическая помощь': '🧠',
  'Интернет и связь': '📡',
  'Фото и видео': '📷',
  'Музыка': '🎵',
  'Искусство': '🎨',
  'Спорт': '⚽',
  'Путешествия': '✈️',
  'Питомцы': '🐕',
  'Бытовая техника': '🔌',
  'Одежда и обувь': '👕',
  'Продукты': '🥬',
  'Аптека': '💊',
  'Банковские услуги': '🏦',
  'Страхование': '📋',
  'Недвижимость': '🏘️',
  'Другое': '📌',
};
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
                  <option key={cat} value={cat}>
                    {cat === 'Все' ? cat : `${CATEGORY_ICONS[cat] || '📌'} ${cat}`}
                  </option>
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
              <span style={styles.filterChip}>{CATEGORY_ICONS[category] || '📁'} {category}</span>
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

      <div style={styles.masonry}>
        {posts.map((post) => (
          <Link key={post.id} to={`/posts/${post.id}`} style={styles.cardLink}>
            <Card hoverable style={styles.postCard}>
              {post.imageUrls && post.imageUrls.length > 0 && (
                <div style={styles.imageContainer}>
                  <img 
                    src={post.imageUrls[0]} 
                    alt={post.title}
                    style={styles.postImage}
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                  <div style={styles.imageOverlay}>
                    {post.imageUrls.length > 1 && (
                      <span style={styles.imageCount}>+{post.imageUrls.length - 1}</span>
                    )}
                  </div>
                </div>
              )}
              <div style={styles.cardContent}>
                <div style={styles.postHeader}>
                  <div style={statusBadgeStyles[post.status]}>
                    <span style={statusDotStyles[post.status]}>●</span>
                    {getStatusLabel(post.status)}
                  </div>
                  <button 
                    onClick={(e) => toggleFavorite(post.id, e, post.status)}
                    style={{
                      ...styles.favoriteBtn,
                      color: favorites.includes(post.id) ? '#fbbf24' : 'rgba(255,255,255,0.5)',
                    }}
                    title={favorites.includes(post.id) ? "Убрать из избранного" : "В избранное"}
                  >
                    {favorites.includes(post.id) ? '★' : '☆'}
                  </button>
                </div>
                <div style={styles.categoryRow}>
                  <span style={styles.categoryIcon}>{CATEGORY_ICONS[post.category] || '📌'}</span>
                  <span style={styles.category}>{post.category}</span>
                </div>
                <h3 style={styles.postTitle}>{post.title}</h3>
                <p style={styles.postDescription}>
                  {post.description.length > 120 
                    ? post.description.substring(0, 120) + '...' 
                    : post.description}
                </p>
                <div style={styles.postFooter}>
                  <div style={styles.author}>
                    <Avatar name={post.authorName} avatarUrl={post.authorAvatarUrl} size="small" showName withRating={post.authorRating} clickable userId={post.userId} />
                  </div>
                  <div style={styles.metaRight}>
                    {post.userCity && <span style={styles.city}>📍 {post.userCity}</span>}
                    <span style={styles.date}>
                      {getRelativeTime(post.createdAt)}
                    </span>
                  </div>
                </div>
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

const statusBadgeStyles: Record<PostStatus, React.CSSProperties> = {
  OPEN: {
    background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.2) 0%, rgba(5, 150, 105, 0.3) 100%)',
    border: '1px solid rgba(16, 185, 129, 0.5)',
    color: '#34d399',
    fontSize: '11px',
    fontWeight: 600,
    padding: '4px 10px',
    borderRadius: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  IN_PROGRESS: {
    background: 'linear-gradient(135deg, rgba(56, 189, 248, 0.2) 0%, rgba(14, 165, 233, 0.3) 100%)',
    border: '1px solid rgba(56, 189, 248, 0.5)',
    color: '#38bdf8',
    fontSize: '11px',
    fontWeight: 600,
    padding: '4px 10px',
    borderRadius: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  COMPLETED: {
    background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.2) 0%, rgba(217, 119, 6, 0.3) 100%)',
    border: '1px solid rgba(245, 158, 11, 0.5)',
    color: '#fbbf24',
    fontSize: '11px',
    fontWeight: 600,
    padding: '4px 10px',
    borderRadius: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  CANCELLED: {
    background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.2) 0%, rgba(220, 38, 38, 0.3) 100%)',
    border: '1px solid rgba(239, 68, 68, 0.5)',
    color: '#f87171',
    fontSize: '11px',
    fontWeight: 600,
    padding: '4px 10px',
    borderRadius: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
};

const statusDotStyles: Record<PostStatus, React.CSSProperties> = {
  OPEN: { color: '#34d399', fontSize: '10px' },
  IN_PROGRESS: { color: '#38bdf8', fontSize: '10px' },
  COMPLETED: { color: '#fbbf24', fontSize: '10px' },
  CANCELLED: { color: '#f87171', fontSize: '10px' },
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
  masonry: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
    gap: '20px',
    gridAutoRows: 'auto',
  },
  cardLink: {
    textDecoration: 'none',
    display: 'block',
  },
  postCard: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    padding: 0,
  },
  imageContainer: {
    position: 'relative',
    width: '100%',
    height: '180px',
    overflow: 'hidden',
    borderRadius: '16px 16px 0 0',
  },
  postImage: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    transition: 'transform 0.3s ease',
  },
  imageOverlay: {
    position: 'absolute',
    bottom: '8px',
    right: '8px',
    background: 'rgba(0,0,0,0.7)',
    borderRadius: '8px',
    padding: '4px 8px',
  },
  imageCount: {
    color: '#fff',
    fontSize: '12px',
    fontWeight: 600,
  },
  cardContent: {
    padding: '16px',
    display: 'flex',
    flexDirection: 'column',
    flex: 1,
  },
  postHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '8px',
    marginBottom: '10px',
  },
  categoryRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    marginBottom: '10px',
  },
  categoryIcon: {
    fontSize: '16px',
  },
  category: {
    color: theme.colors.accentLight,
    fontSize: '13px',
    fontWeight: 500,
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
    fontSize: '17px',
    fontWeight: 600,
    margin: '0 0 10px 0',
    lineHeight: 1.3,
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
    marginTop: '14px',
    paddingTop: '14px',
    borderTop: `1px solid ${theme.colors.border}`,
    flexWrap: 'wrap',
    gap: '8px',
  },
  author: {
    display: 'flex',
    alignItems: 'center',
  },
  metaRight: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  city: {
    color: theme.colors.accentLight,
    fontSize: '12px',
    fontWeight: 500,
  },
  date: {
    color: theme.colors.textMuted,
    fontSize: '12px',
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