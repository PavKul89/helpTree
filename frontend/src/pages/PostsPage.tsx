import { useEffect, useState, useCallback } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { 
  MapPin, Star, Settings, Search, X, Axe, Trash2, Wrench, Truck, ShoppingCart, 
  ChefHat, Flower2, Car, Dog, Baby, Laptop, Scissors, Pill, Scale, BookOpen, 
  GraduationCap, CarFront, Home, Sparkles, Package, Heart, Brain, Wifi, Camera, 
  Music, Palette, Trophy, Plane, Bird, Plug, Shirt, Apple, Syringe, CreditCard,
  Shield, Building, Pin, CircleDot, User, Zap
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { Card, Button, Spinner, EmptyState, Avatar, PostCardSkeleton } from '../components';
import { useToast } from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';

type PostStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

const FavoriteButton = ({ 
  postId, 
  isFavorite, 
  onToggle,
  disabled 
}: { 
  postId: number; 
  isFavorite: boolean; 
  onToggle: (postId: number, e: React.MouseEvent) => void;
  disabled?: boolean;
}) => {
  if (disabled) return null;
  
  return (
    <span 
      onClick={(e) => onToggle(postId, e)}
      className={isFavorite ? 'favorite-active' : ''}
      style={{
        fontSize: '20px',
        cursor: 'pointer',
        padding: '4px',
        color: isFavorite ? '#FFD700' : 'rgba(255,255,255,0.5)',
        textShadow: isFavorite ? '0 0 8px rgba(255, 215, 0, 0.8)' : 'none',
        transition: 'all 0.3s ease',
        display: 'inline-block',
      }}
      title={isFavorite ? "Убрать из избранного" : "В избранное"}
    >
      {isFavorite ? '★' : '☆'}
    </span>
  );
};

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

const CATEGORY_ICON_COMPONENTS: Record<string, React.ElementType> = {
  'Дрова': Axe,
  'Уборка': Trash2,
  'Ремонт': Wrench,
  'Доставка': Truck,
  'Покупки': ShoppingCart,
  'Готовка': ChefHat,
  'Садоводство': Flower2,
  'Перевозка': Car,
  'Уход за животными': Dog,
  'Помощь с детьми': Baby,
  'Компьютерная помощь': Laptop,
  'Стрижка': Scissors,
  'Медицинская помощь': Pill,
  'Юридическая консультация': Scale,
  'Обучение': BookOpen,
  'Репетитор': GraduationCap,
  'Транспорт': CarFront,
  'Строительство': Home,
  'Клининг': Sparkles,
  'Курьер': Package,
  'Волонтёрство': Heart,
  'Психологическая помощь': Brain,
  'Интернет и связь': Wifi,
  'Фото и видео': Camera,
  'Музыка': Music,
  'Искусство': Palette,
  'Спорт': Trophy,
  'Путешествия': Plane,
  'Питомцы': Bird,
  'Бытовая техника': Plug,
  'Одежда и обувь': Shirt,
  'Продукты': Apple,
  'Аптека': Syringe,
  'Банковские услуги': CreditCard,
  'Страхование': Shield,
  'Недвижимость': Building,
  'Другое': Pin,
};
const STATUSES = ['Все', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'Избранное'];

export const PostsPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [authorSearch, setAuthorSearch] = useState('');
  const [authorInput, setAuthorInput] = useState('');
  const [category, setCategory] = useState('Все');
  const [status, setStatus] = useState('Все');
  const [city, setCity] = useState('');
  const [cityInput, setCityInput] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [favorites, setFavorites] = useState<number[]>([]);
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [currentUserCity, setCurrentUserCity] = useState<string>('');
  const [showFilters, setShowFilters] = useState(false);
  const { showToast } = useToast();

  const hasActiveFilters = category !== 'Все' || status !== 'Все' || city || authorSearch;
  const activeFilterCount = [category !== 'Все', status !== 'Все', !!city, !!authorSearch].filter(Boolean).length;

  const loadPosts = useCallback(async (pageNum = 0, searchTerm?: string, newCategory?: string, newStatus?: string, newCity?: string, newAuthorSearch?: string) => {
    try {
      const searchToUse = searchTerm !== undefined ? searchTerm : search;
      const cat = newCategory !== undefined ? newCategory : category;
      const stat = newStatus !== undefined ? newStatus : status;
      const cit = newCity !== undefined ? newCity : city;
      const author = newAuthorSearch !== undefined ? newAuthorSearch : authorSearch;
      const params: any = { page: pageNum, size: 10 };
      if (searchToUse) params.title = searchToUse;
      if (author) params.authorName = author;
      if (cat !== 'Все') params.category = cat;
      if (stat !== 'Все') params.status = stat;
      if (cit) params.city = cit;
      const data = await postsApi.getAll(params);
      setPosts(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [search, authorSearch, category, status, city]);

  useEffect(() => {
    setSearch('');
    setSearchInput('');
    setAuthorSearch('');
    setAuthorInput('');
    setCategory('Все');
    setStatus('Все');
    setCity('');
    setCityInput('');
    setPage(0);
    loadPosts(0, '');
    
    authApi.getCurrentUser()
      .then(user => {
        console.log('Current user:', user);
        setCurrentUserId(user.id);
        if ('city' in user && user.city) {
          setCurrentUserCity(user.city);
        }
        return authApi.getFavorites(user.id);
      })
      .then(favs => {
        console.log('Favorites loaded:', favs);
        setFavorites(favs);
      })
      .catch(err => console.error('Error loading user:', err));
  }, [location.key]);

  useEffect(() => {
    console.log('User changed:', user);
    if (user) {
      authApi.getFavorites(user.id)
        .then(favs => {
          console.log('Favorites loaded (user effect):', favs);
          setFavorites(favs);
        })
        .catch(err => console.error('Error loading favorites:', err));
    }
  }, [user]);

  useEffect(() => {
    console.log('Favorites state changed:', favorites);
  }, [favorites]);

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
      loadPosts(0, undefined, undefined, value);
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
    
    const currentFavorites = favorites;
    const isFav = currentFavorites.includes(postId);
    const newFavorites = isFav 
      ? currentFavorites.filter(id => id !== postId)
      : [...currentFavorites, postId];
    
    console.log('Toggle favorite:', postId, 'isFav:', isFav, 'newFavorites:', newFavorites);
    setFavorites(newFavorites);
    
    try {
      if (isFav) {
        await authApi.removeFavorite(currentUserId, postId);
      } else {
        await authApi.addFavorite(currentUserId, postId);
      }
    } catch (err) {
      console.error('Favorite toggle failed:', err);
      setFavorites(currentFavorites);
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
    loadPosts(0, undefined, value);
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
    loadPosts(0, undefined, undefined, undefined, cityInput);
  };

  const handleMyCity = () => {
    setCity(currentUserCity);
    setCityInput(currentUserCity);
    setLoading(true);
    loadPosts(0, undefined, undefined, undefined, currentUserCity);
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

  if (loading) return (
    <>
      <div style={styles.container} className="page-content">
        <div style={styles.stickyContainer}>
          <header className="page-header" style={styles.header}>
            <h1 className="page-title" style={styles.title}>Посты о помощи</h1>
          </header>
        </div>
        <div style={styles.masonry}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <PostCardSkeleton key={i} />
          ))}
        </div>
      </div>
    </>
  );

  return (
    <div style={styles.container} className="page-content">
      <div style={styles.stickyContainer}>
        <header className="page-header" style={styles.header}>
          <h1 className="page-title" style={styles.title}>Посты о помощи</h1>
          {user && 'blockedAt' in user && user.blockedAt ? (
            <Button onClick={() => showToast('Ваш аккаунт заблокирован за долг. Помогите другим пользователям!', 'error')} disabled>
              + Создать пост
            </Button>
          ) : (
            <Link to="/posts/new">
              <Button>+ Создать пост</Button>
            </Link>
          )}
        </header>

        <div style={styles.filters}>
          <div style={styles.searchContainer}>
            <div style={styles.searchForm}>
              <input
                type="text"
                placeholder="Поиск постов..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                style={styles.searchInput}
              />
              <Button onClick={handleSearch} style={styles.searchBtn}>
                <Search size={16} />
              </Button>
            </div>
          </div>
          <button 
            onClick={() => setShowFilters(!showFilters)}
            style={{
              ...styles.filterToggle,
              ...(showFilters || hasActiveFilters ? styles.filterToggleActive : {}),
            }}
          >
            <Settings size={16} style={{marginRight: 6}} /> 
            Фильтры
            {hasActiveFilters && <span style={styles.filterCount}>{activeFilterCount}</span>}
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
          <div style={styles.filterSection}>
            <div style={styles.filterSectionHeader}>
              <Sparkles size={14} style={{marginRight: 6}} />
              Категория
            </div>
            <div style={styles.chipContainer}>
              {CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  onClick={() => handleCategoryChange(cat)}
                  style={{
                    ...styles.chip,
                    ...(category === cat ? styles.chipActive : {}),
                  }}
                >
                  {cat === 'Все' ? 'Все' : cat}
                </button>
              ))}
            </div>
          </div>

          <div style={styles.filterSection}>
            <div style={styles.filterSectionHeader}>
              <CircleDot size={14} style={{marginRight: 6}} />
              Статус
            </div>
            <div style={styles.chipContainer}>
              {STATUSES.map((s) => (
                <button
                  key={s}
                  onClick={() => handleStatusChange(s)}
                  style={{
                    ...styles.chip,
                    ...(status === s ? styles.chipActive : {}),
                  }}
                >
                  {s === 'Все' ? 'Все' : getStatusLabel(s)}
                </button>
              ))}
            </div>
          </div>

          <div style={styles.filterSection}>
            <div style={styles.filterSectionHeader}>
              <User size={14} style={{marginRight: 6}} />
              Автор
            </div>
            <div style={styles.cityFilter}>
              <input
                type="text"
                placeholder="Имя автора..."
                value={authorInput}
                onChange={(e) => setAuthorInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setAuthorSearch(authorInput);
                    loadPosts(0, undefined, undefined, undefined, undefined, authorInput);
                  }
                }}
                style={styles.cityInput}
              />
              <Button onClick={() => {
                setAuthorSearch(authorInput);
                loadPosts(0, undefined, undefined, undefined, undefined, authorInput);
              }} style={styles.cityBtn}>
                <Search size={14} />
              </Button>
            </div>
          </div>

          <div style={styles.filterSection}>
            <div style={styles.filterSectionHeader}>
              <MapPin size={14} style={{marginRight: 6}} />
              Город
            </div>
            <div style={styles.cityFilter}>
              <input
                type="text"
                placeholder="Введите город..."
                value={cityInput}
                onChange={(e) => handleCityInputChange(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleCitySearch()}
                style={styles.cityInput}
              />
              <Button onClick={handleCitySearch} style={styles.cityBtn}>
                <Search size={14} />
              </Button>
              {currentUserCity && (
                <Button onClick={handleMyCity} style={styles.myCityBtn}>
                  <MapPin size={14} />
                </Button>
              )}
            </div>
          </div>

          {hasActiveFilters && (
            <button 
              onClick={() => {
                setCategory('Все');
                setStatus('Все');
                setCity('');
                setCityInput('');
                setAuthorSearch('');
                setAuthorInput('');
                loadPosts(0, '', 'Все', 'Все', '', '');
              }}
              style={styles.clearBtn}
            >
              <X size={14} style={{marginRight: 4}} /> Очистить все
            </button>
          )}
        </div>
      </div>

      <div style={styles.activeFilters}>
        {authorSearch && (
          <span style={styles.filterChip}>
            <User size={12} style={{marginRight: 4}} />
            Автор: {authorSearch}
            <button 
              onClick={() => { setAuthorSearch(''); setAuthorInput(''); loadPosts(0, undefined, undefined, undefined, undefined, ''); }}
              style={styles.chipRemove}
            >
              <X size={12} />
            </button>
          </span>
        )}
        {category !== 'Все' && (
          <span style={styles.filterChip}>
            {category}
            <button 
              onClick={() => { setCategory('Все'); loadPosts(0, undefined, 'Все'); }}
              style={styles.chipRemove}
            >
              <X size={12} />
            </button>
          </span>
        )}
        {status !== 'Все' && (
          <span style={styles.filterChip}>
            {getStatusLabel(status)}
            <button 
              onClick={() => { setStatus('Все'); loadPosts(0, undefined, undefined, 'Все'); }}
              style={styles.chipRemove}
            >
              <X size={12} />
            </button>
          </span>
        )}
        {city && (
          <span style={styles.filterChip}>
            <MapPin size={12} style={{marginRight: 4}} />
            {city}
            <button 
              onClick={() => { setCity(''); setCityInput(''); loadPosts(0, undefined, undefined, undefined, ''); }}
              style={styles.chipRemove}
            >
              <X size={12} />
            </button>
          </span>
        )}
      </div>
      </div>

      <div style={styles.resultsInfo}>
        Найдено постов: <span style={styles.resultsCount}>{posts.length}</span>
      </div>

      <div style={styles.masonry}>
        {posts.map((post) => (
          <Link key={post.id} to={`/posts/${post.id}`} style={styles.cardLink}>
            <Card hoverable style={styles.postCard}>
              {post.imageUrls && post.imageUrls.length > 0 ? (
                <div style={styles.imageContainer}>
                  <img 
                    src={post.imageUrls[0]} 
                    alt={post.title}
                    style={styles.postImage}
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                  <div style={{ ...styles.imageStatusBadge, ...statusBadgeStyles[post.status] }}>
                    <span style={statusDotStyles[post.status]}>●</span>
                    {getStatusLabel(post.status)}
                  </div>
                  {post.imageUrls.length > 1 && (
                    <div style={styles.imageOverlay}>
                      <span style={styles.imageCount}>+{post.imageUrls.length - 1}</span>
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ ...styles.imageContainer, background: 'rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <div style={{ ...styles.imageStatusBadge, ...statusBadgeStyles[post.status] }}>
                    <span style={statusDotStyles[post.status]}>●</span>
                    {getStatusLabel(post.status)}
                  </div>
                  {(() => {
                    const IconComp = CATEGORY_ICON_COMPONENTS[post.category];
                    return IconComp ? <IconComp size={48} color={theme.colors.accent} style={{ opacity: 0.3 }} /> : <Pin size={48} color={theme.colors.accent} style={{ opacity: 0.3 }} />;
                  })()}
                </div>
              )}
              <div style={styles.cardContent}>
                <div style={styles.postHeader}>
                  <div style={styles.categoryRow}>
                    {(() => {
                      const IconComp = CATEGORY_ICON_COMPONENTS[post.category];
                      return IconComp ? <IconComp size={16} color={theme.colors.accent} /> : <Pin size={16} color={theme.colors.accent} />;
                    })()}
                    <span style={styles.category}>{post.category}</span>
                  </div>
                  <FavoriteButton 
                    postId={post.id} 
                    isFavorite={favorites.includes(post.id)} 
                    onToggle={(postId, e) => toggleFavorite(postId, e, post.status)}
                    disabled={user?.id === post.userId}
                  />
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
                    {post.userCity && <span style={styles.city}><MapPin size={12} style={{marginRight: 4}} />{post.userCity}</span>}
                    <span style={post.boosted ? styles.boostedDate : styles.date}>
                      {post.boosted && <Zap size={12} style={{marginRight: 4}} />}
                      {post.boosted ? 'В топе' : getRelativeTime(post.createdAt)}
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
    padding: '24px',
  },
  stickyContainer: {
    position: 'sticky',
    top: 0,
    zIndex: 100,
    background: 'linear-gradient(180deg, rgba(6, 95, 70, 0.97) 0%, rgba(2, 44, 34, 0.98) 100%)',
    backdropFilter: 'blur(20px)',
    padding: '20px 24px 16px',
    margin: '0 0 24px',
    borderRadius: theme.borderRadius.lg,
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(6, 182, 212, 0.1)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '20px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '26px',
    fontWeight: 700,
    margin: 0,
    letterSpacing: '-0.5px',
  },
  filters: {
    display: 'flex',
    gap: '16px',
    marginBottom: '16px',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  searchContainer: {
    flex: 1,
    minWidth: '300px',
    maxWidth: '480px',
  },
  searchForm: {
    display: 'flex',
    gap: '10px',
  },
  searchInput: {
    flex: 1,
    padding: '12px 18px',
    fontSize: '15px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: `1px solid rgba(255,255,255,0.1)`,
    borderRadius: '50px',
    color: theme.colors.text,
    outline: 'none',
    transition: 'all 0.3s ease',
  },
  searchBtn: {
    padding: '12px 18px',
    whiteSpace: 'nowrap',
    borderRadius: '50px',
  },
  filterToggle: {
    background: 'rgba(0, 0, 0, 0.2)',
    border: `1px solid rgba(255,255,255,0.1)`,
    borderRadius: '50px',
    color: theme.colors.textSecondary,
    padding: '12px 20px',
    fontSize: '14px',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  filterToggleActive: {
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.3) 0%, rgba(6, 182, 212, 0.2) 100%)',
    borderColor: theme.colors.accent,
    color: theme.colors.accentLight,
    boxShadow: '0 0 20px rgba(6, 182, 212, 0.2)',
  },
  filterCount: {
    background: theme.colors.accent,
    color: '#fff',
    fontSize: '11px',
    fontWeight: 700,
    padding: '3px 8px',
    borderRadius: '12px',
    marginLeft: '6px',
    boxShadow: '0 2px 8px rgba(6, 182, 212, 0.4)',
  },
  filterPanelWrapper: {
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.08) 0%, rgba(2, 44, 34, 0.15) 100%)',
    borderRadius: '20px',
    border: `1px solid rgba(6, 182, 212, 0.2)`,
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3), 0 0 40px rgba(6, 182, 212, 0.05)',
  },
  filterPanel: {
    display: 'flex',
    gap: '28px',
    flexWrap: 'wrap',
    padding: '24px',
    background: 'linear-gradient(135deg, rgba(255,255,255,0.08) 0%, rgba(255,255,255,0.04) 100%)',
    borderRadius: '16px',
    alignItems: 'flex-start',
    border: `1px solid rgba(255,255,255,0.08)`,
    boxShadow: '0 4px 24px rgba(0, 0, 0, 0.2) inset',
  },
  filterSection: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    minWidth: '220px',
  },
  filterSectionHeader: {
    color: theme.colors.accentLight,
    fontSize: '11px',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '1px',
    display: 'flex',
    alignItems: 'center',
  },
  chipContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '10px',
  },
  chip: {
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '50px',
    padding: '10px 18px',
    fontSize: '13px',
    color: theme.colors.textSecondary,
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    fontWeight: 500,
  },
  chipActive: {
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.35) 0%, rgba(6, 182, 212, 0.25) 100%)',
    borderColor: theme.colors.accent,
    color: theme.colors.accentLight,
    fontWeight: 600,
    boxShadow: '0 4px 15px rgba(6, 182, 212, 0.25)',
  },
  clearBtn: {
    background: 'rgba(239, 68, 68, 0.15)',
    border: '1px solid rgba(239, 68, 68, 0.4)',
    borderRadius: '50px',
    color: '#fca5a5',
    padding: '10px 18px',
    fontSize: '14px',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    display: 'flex',
    alignItems: 'center',
    alignSelf: 'flex-start',
    marginTop: 'auto',
  },
  activeFilters: {
    display: 'flex',
    gap: '10px',
    flexWrap: 'wrap',
    marginTop: '16px',
  },
  filterChip: {
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.25) 0%, rgba(6, 182, 212, 0.15) 100%)',
    border: '1px solid rgba(6, 182, 212, 0.4)',
    borderRadius: '50px',
    padding: '8px 12px 8px 16px',
    fontSize: '13px',
    color: theme.colors.accentLight,
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    boxShadow: '0 2px 10px rgba(6, 182, 212, 0.15)',
  },
  chipRemove: {
    background: 'rgba(255,255,255,0.1)',
    border: 'none',
    borderRadius: '50%',
    width: '20px',
    height: '20px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    padding: 0,
    color: theme.colors.accentLight,
    transition: 'all 0.2s',
  },
  cityFilter: {
    display: 'flex',
    gap: '10px',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  cityInput: {
    padding: '10px 16px',
    fontSize: '14px',
    background: 'rgba(0, 0, 0, 0.2)',
    border: `1px solid rgba(255,255,255,0.1)`,
    borderRadius: '50px',
    color: theme.colors.text,
    outline: 'none',
    width: '180px',
  },
  cityBtn: {
    padding: '10px 16px',
    whiteSpace: 'nowrap',
    borderRadius: '50px',
  },
  myCityBtn: {
    padding: '10px 16px',
    whiteSpace: 'nowrap',
    background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.25) 0%, rgba(5, 150, 105, 0.25) 100%)',
    border: '1px solid rgba(16, 185, 129, 0.5)',
    borderRadius: '50px',
  },
  resultsInfo: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginBottom: '16px',
    padding: '12px 16px',
    background: 'rgba(0, 0, 0, 0.15)',
    borderRadius: theme.borderRadius.md,
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  resultsCount: {
    color: theme.colors.accentLight,
    fontWeight: 700,
    fontSize: '16px',
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
    height: '220px',
    overflow: 'hidden',
    borderRadius: '16px 16px 0 0',
  },
  postImage: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    transition: 'transform 0.4s ease',
  },
  imageStatusBadge: {
    position: 'absolute',
    top: '12px',
    left: '12px',
    padding: '6px 12px',
    borderRadius: '20px',
    fontSize: '11px',
    fontWeight: 600,
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    backdropFilter: 'blur(8px)',
    zIndex: 2,
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
    color: 'rgba(255,255,255,0.5)',
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
  boostedDate: {
    color: '#f59e0b',
    fontSize: '12px',
    fontWeight: 600,
    display: 'flex',
    alignItems: 'center',
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