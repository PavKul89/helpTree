import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Circle } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Navigation, Target, X, ChevronRight, Clock, Filter, Zap, User } from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import type { Post } from '../types';
import { Button, Spinner } from '../components';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';
import { geocodeCity } from '../utils/geocoding';

interface PostWithDistance extends Post {
  distance?: number;
}

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const CATEGORY_COLORS: Record<string, string> = {
  'Дрова': '#f59e0b',
  'Уборка': '#8b5cf6',
  'Ремонт': '#ef4444',
  'Доставка': '#3b82f6',
  'Покупки': '#10b981',
  'Готовка': '#f97316',
  'Садоводство': '#22c55e',
  'Перевозка': '#6366f1',
  'Уход за животными': '#ec4899',
  'Помощь с детьми': '#14b8a6',
  'Компьютерная помощь': '#06b6d4',
  'Стрижка': '#a855f7',
  'Медицинская помощь': '#ef4444',
  'Юридическая консультация': '#3b82f6',
  'Обучение': '#8b5cf6',
  'Репетитор': '#14b8a6',
  'Транспорт': '#f59e0b',
  'Строительство': '#78716c',
  'Электрика': '#fbbf24',
  'Сантехника': '#38bdf8',
  'Погрузка/Разгрузка': '#84cc16',
};

const getCategoryColor = (category: string): string => CATEGORY_COLORS[category] || '#06b6d4';

const createCustomIcon = (category: string, status: string, isSelected: boolean) => {
  const color = getCategoryColor(category);
  const size = isSelected ? 48 : 40;
  
  const statusDot = status === 'OPEN' ? '' : `<div style="
    position: absolute;
    top: -2px;
    right: -2px;
    width: 14px;
    height: 14px;
    background: ${status === 'IN_PROGRESS' ? '#38bdf8' : status === 'COMPLETED' ? '#10b981' : '#6b7280'};
    border-radius: 50%;
    border: 2px solid #065f46;
  "></div>`;
  
  return L.divIcon({
    className: 'custom-marker',
    html: `
      <div style="
        width: ${size}px;
        height: ${size}px;
        background: linear-gradient(135deg, ${color}40 0%, ${color}80 100%);
        border: 3px solid ${isSelected ? '#22d3ee' : color};
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 4px 12px ${color}60, 0 0 20px ${color}30;
        transition: all 0.2s ease;
        ${isSelected ? 'transform: scale(1.2);' : ''}
      ">
        <span style="
          color: #fff;
          font-size: ${isSelected ? '14px' : '12px'};
          font-weight: 700;
          text-shadow: 0 1px 2px rgba(0,0,0,0.3);
        ">${category.substring(0, 2)}</span>
      </div>
      ${statusDot}
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
};

const RADIUS_OPTIONS = [5, 10, 25, 50];

const BELARUS_CITIES: Record<string, { lat: number; lng: number }> = {
  'гомель': { lat: 52.4415, lng: 30.9877 },
  'минск': { lat: 53.9045, lng: 27.5615 },
  'брест': { lat: 52.0976, lng: 23.6881 },
  'гродно': { lat: 53.6694, lng: 23.8131 },
  'витебск': { lat: 55.1909, lng: 30.2049 },
  'могилёв': { lat: 53.9008, lng: 30.3313 },
  'барановичи': { lat: 52.1333, lng: 26.0167 },
  'пинск': { lat: 52.1125, lng: 26.1017 },
  'орша': { lat: 54.5007, lng: 30.4178 },
  'мозырь': { lat: 51.5058, lng: 29.0413 },
  'лида': { lat: 53.8917, lng: 25.3026 },
  'новополоцк': { lat: 55.5317, lng: 28.6425 },
  'светлогорск': { lat: 52.6333, lng: 29.9500 },
  'калинковичи': { lat: 52.2431, lng: 29.3317 },
  'жлобин': { lat: 52.8875, lng: 29.7325 },
  'речица': { lat: 52.3567, lng: 30.4366 },
};

const getSimplifiedCity = (city: string): string => {
  const parts = city.split(',').map(p => p.trim().toLowerCase());
  for (let i = parts.length - 1; i >= 0; i--) {
    const part = parts[i].replace(/^г\.\s*/, '').replace(/^г\s*/, '');
    if (part.length > 2) {
      return part.charAt(0).toUpperCase() + part.slice(1);
    }
  }
  return parts[0];
};

const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
};

const tryGeocode = async (cityName: string): Promise<{ lat: number; lng: number } | null> => {
  const lowerCity = cityName.toLowerCase();
  for (const [key, coords] of Object.entries(BELARUS_CITIES)) {
    if (lowerCity.includes(key)) {
      return coords;
    }
  }
  let coords = await geocodeCity(cityName);
  if (coords) return coords;
  const simplified = getSimplifiedCity(cityName);
  if (simplified !== cityName) {
    coords = await geocodeCity(simplified);
    if (coords) return coords;
  }
  return null;
};

export const MapPage = () => {
  const { user } = useAuth();
  const [posts, setPosts] = useState<PostWithDistance[]>([]);
  const [loading, setLoading] = useState(true);
  const [radius, setRadius] = useState(10);
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [selectedPost, setSelectedPost] = useState<PostWithDistance | null>(null);
  const [userCity, setUserCity] = useState<string>('');
  const [geoLoading, setGeoLoading] = useState(false);

  const loadPosts = useCallback(async () => {
    if (!userLocation) return;
    setLoading(true);
    try {
      let data = await postsApi.getMapPosts();
      
      const postsWithCoords = await Promise.all(
        data.map(async (post) => {
          if (!post.latitude || !post.longitude) {
            if (post.userCity) {
              const coords = await tryGeocode(post.userCity);
              if (coords) {
                return { ...post, latitude: coords.lat, longitude: coords.lng };
              }
            }
          }
          return post;
        })
      );
      
      const filteredPosts: PostWithDistance[] = postsWithCoords
        .filter(p => p.latitude && p.longitude)
        .map(p => ({
          ...p,
          distance: calculateDistance(userLocation.lat, userLocation.lng, p.latitude!, p.longitude!)
        }))
        .filter(p => p.distance <= radius);
      
      setPosts(filteredPosts);
    } catch (err) {
      console.error('Error loading posts:', err);
    } finally {
      setLoading(false);
    }
  }, [userLocation, radius]);

  useEffect(() => {
    const loadUserData = async () => {
      if (user) {
        setGeoLoading(true);
        try {
          const userData = await authApi.getCurrentUser();
          if ('city' in userData && userData.city) {
            setUserCity(userData.city);
            const coords = await tryGeocode(userData.city);
            if (coords) {
              setUserLocation({ lat: coords.lat, lng: coords.lng });
            }
          }
        } catch (err) {
          console.error('Error loading user data:', err);
        } finally {
          setGeoLoading(false);
        }
      }
    };
    loadUserData();
  }, [user]);

  useEffect(() => {
    if (userLocation) {
      loadPosts();
    } else if (!user) {
      setLocationError('Для использования карты необходимо войти в систему');
      setLoading(false);
    }
  }, [radius, userLocation, loadPosts, user]);

  const handleUseMyLocation = () => {
    if (!navigator.geolocation) {
      setLocationError('Геолокация не поддерживается вашим браузером');
      return;
    }

    setLoading(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setUserLocation({
          lat: position.coords.latitude,
          lng: position.coords.longitude
        });
        setLocationError(null);
      },
      () => {
        setLocationError('Не удалось определить местоположение');
        setLoading(false);
      }
    );
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

  const openPostsCount = posts.filter(p => p.status === 'OPEN').length;

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <div style={styles.headerIcon}>
            <MapPin size={24} color={theme.colors.accent} />
          </div>
          <div>
            <h1 style={styles.title}>Карта помощи</h1>
            {userLocation && (
              <p style={styles.subtitle}>
                Найдено {posts.length} постов • {openPostsCount} открытых
              </p>
            )}
          </div>
        </div>
        
        <div style={styles.headerActions}>
          <div style={styles.radiusSelector}>
            <Filter size={16} color={theme.colors.textMuted} />
            <span style={styles.radiusLabel}>Радиус:</span>
            {RADIUS_OPTIONS.map(r => (
              <button
                key={r}
                onClick={() => setRadius(r)}
                style={{
                  ...styles.radiusBtn,
                  ...(radius === r ? styles.radiusBtnActive : {})
                }}
              >
                {r} км
              </button>
            ))}
          </div>
          
          <Button onClick={handleUseMyLocation} style={styles.locationBtn}>
            <Target size={16} />
            <span>Моё местоположение</span>
          </Button>
        </div>
      </div>

      {/* Error Banner */}
      {locationError && (
        <div style={styles.errorBanner}>
          <div style={styles.errorIcon}>
            <MapPin size={20} />
          </div>
          <div style={styles.errorContent}>
            <div style={styles.errorTitle}>{locationError}</div>
            <Button onClick={handleUseMyLocation} style={styles.errorBtn}>
              <Navigation size={14} />
              Использовать моё местоположение
            </Button>
          </div>
        </div>
      )}

      {/* Map Container */}
      <div style={styles.mapWrapper}>
        {loading || geoLoading ? (
          <div style={styles.loadingOverlay}>
            <Spinner message={geoLoading ? "Определяем местоположение..." : "Загрузка карты..."} />
          </div>
        ) : (
          <>
            <MapContainer 
              center={userLocation ? [userLocation.lat, userLocation.lng] : [52.4415, 30.9877]}
              zoom={radius > 25 ? 8 : radius > 10 ? 10 : 11}
              style={styles.map}
              zoomControl={true}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />

              {userLocation && (
                <Circle
                  center={[userLocation.lat, userLocation.lng]}
                  radius={radius * 1000}
                  pathOptions={{
                    color: '#06b6d4',
                    fillColor: '#06b6d4',
                    fillOpacity: 0.08,
                    weight: 2,
                  }}
                />
              )}

              {posts.filter(p => p.latitude && p.longitude).map((post: PostWithDistance) => (
                <Marker 
                  key={post.id}
                  position={[post.latitude!, post.longitude!]}
                  icon={createCustomIcon(post.category, post.status, selectedPost?.id === post.id)}
                  eventHandlers={{
                    click: () => setSelectedPost(post),
                  }}
                />
              ))}
            </MapContainer>

            {/* Selected Post Card */}
            {selectedPost && (
              <div style={styles.postCard}>
                <div style={styles.postCardHeader}>
                  <div style={{
                    ...styles.statusBadge,
                    backgroundColor: getCategoryColor(selectedPost.category) + '30',
                    color: getCategoryColor(selectedPost.category),
                  }}>
                    {selectedPost.category}
                  </div>
                  <button 
                    style={styles.closeBtn}
                    onClick={() => setSelectedPost(null)}
                  >
                    <X size={18} />
                  </button>
                </div>
                
                <h3 style={styles.postTitle}>{selectedPost.title}</h3>
                
                <div style={styles.postMeta}>
                  <div style={styles.metaItem}>
                    <User size={14} color={theme.colors.accentLight} />
                    <span style={{color: selectedPost.authorNicknameColor || theme.colors.accentLight}}>{selectedPost.authorName}</span>
                  </div>
                  <div style={styles.metaItem}>
                    <MapPin size={14} color={theme.colors.accentLight} />
                    <span>{selectedPost.userCity || 'Город не указан'}</span>
                  </div>
                  <div style={styles.metaItem}>
                    <MapPin size={14} color={theme.colors.accentLight} />
                    <span>{selectedPost.userCity || 'Город не указан'}</span>
                  </div>
                  <div style={styles.metaItem}>
                    {selectedPost.boosted ? (
                      <>
                        <Zap size={14} color="#f59e0b" />
                        <span style={{color: '#f59e0b', fontWeight: 600}}>В топе</span>
                      </>
                    ) : (
                      <>
                        <Clock size={14} color={theme.colors.textMuted} />
                        <span>{getRelativeTime(selectedPost.createdAt)}</span>
                      </>
                    )}
                  </div>
                  {selectedPost.distance && (
                    <div style={styles.metaItem}>
                      <Navigation size={14} color={theme.colors.primaryLight} />
                      <span>{selectedPost.distance.toFixed(1)} км</span>
                    </div>
                  )}
                </div>

                <div style={{
                  ...styles.statusIndicator,
                  backgroundColor: selectedPost.status === 'OPEN' ? '#10b98120' : 
                                   selectedPost.status === 'IN_PROGRESS' ? '#38bdf820' : '#6b728020',
                  color: selectedPost.status === 'OPEN' ? '#10b981' : 
                         selectedPost.status === 'IN_PROGRESS' ? '#38bdf8' : '#6b7280',
                }}>
                  {getStatusLabel(selectedPost.status)}
                </div>

                <p style={styles.postDesc}>
                  {selectedPost.description.substring(0, 120)}
                  {selectedPost.description.length > 120 ? '...' : ''}
                </p>

                <Link to={`/posts/${selectedPost.id}`} style={styles.viewLink}>
                  <Button style={styles.viewBtn}>
                    Смотреть пост
                    <ChevronRight size={16} />
                  </Button>
                </Link>
              </div>
            )}

            {/* Empty State */}
            {posts.length === 0 && userLocation && (
              <div style={styles.emptyState}>
                <div style={styles.emptyIcon}>📍</div>
                <h3 style={styles.emptyTitle}>Постов не найдено</h3>
                <p style={styles.emptyText}>
                  В радиусе {radius} км от {userCity || 'вашего местоположения'} нет открытых постов
                </p>
                <Button onClick={() => setRadius(50)} style={styles.radiusBtn}>
                  Увеличить радиус до 50 км
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: '100%',
    padding: '0 24px 24px',
    display: 'flex',
    flexDirection: 'column',
    height: 'calc(100vh - 100px)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '20px 0',
    gap: '20px',
    flexWrap: 'wrap',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  headerIcon: {
    width: '56px',
    height: '56px',
    borderRadius: '16px',
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.2) 0%, rgba(6, 182, 212, 0.1) 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: theme.colors.text,
    fontSize: '24px',
    fontWeight: 700,
    margin: 0,
    lineHeight: 1.2,
  },
  subtitle: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    margin: '4px 0 0',
  },
  headerActions: {
    display: 'flex',
    gap: '16px',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  radiusSelector: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    background: 'rgba(255,255,255,0.05)',
    padding: '8px 16px',
    borderRadius: '12px',
  },
  radiusLabel: {
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  radiusBtn: {
    background: 'transparent',
    border: 'none',
    borderRadius: '8px',
    padding: '6px 12px',
    color: theme.colors.textSecondary,
    fontSize: '13px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  radiusBtnActive: {
    background: theme.colors.accent,
    color: '#fff',
  },
  locationBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  errorBanner: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    background: 'rgba(239, 68, 68, 0.1)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: '16px',
    padding: '16px 20px',
    marginBottom: '16px',
  },
  errorIcon: {
    width: '44px',
    height: '44px',
    borderRadius: '12px',
    background: 'rgba(239, 68, 68, 0.2)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#ef4444',
  },
  errorContent: {
    flex: 1,
  },
  errorTitle: {
    color: theme.colors.text,
    fontSize: '14px',
    marginBottom: '8px',
  },
  errorBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    fontSize: '13px',
    padding: '8px 16px',
  },
  mapWrapper: {
    flex: 1,
    borderRadius: '20px',
    overflow: 'hidden',
    position: 'relative',
    minHeight: '400px',
    boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
  },
  loadingOverlay: {
    position: 'absolute',
    inset: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'rgba(2, 44, 34, 0.9)',
    zIndex: 1000,
  },
  map: {
    width: '100%',
    height: '100%',
    minHeight: '400px',
  },
  postCard: {
    position: 'absolute',
    top: '20px',
    right: '20px',
    width: '340px',
    background: 'linear-gradient(160deg, #065f46 0%, #0e7490 100%)',
    borderRadius: '20px',
    padding: '20px',
    boxShadow: '0 12px 40px rgba(0,0,0,0.4)',
    zIndex: 1000,
  },
  postCardHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '12px',
  },
  statusBadge: {
    padding: '4px 12px',
    borderRadius: '10px',
    fontSize: '12px',
    fontWeight: 600,
  },
  closeBtn: {
    background: 'rgba(255,255,255,0.1)',
    border: 'none',
    borderRadius: '8px',
    width: '32px',
    height: '32px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: theme.colors.textMuted,
    cursor: 'pointer',
  },
  postTitle: {
    color: theme.colors.text,
    fontSize: '18px',
    fontWeight: 700,
    margin: '0 0 12px',
    lineHeight: 1.3,
  },
  postMeta: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '12px',
    marginBottom: '12px',
  },
  metaItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    color: theme.colors.textSecondary,
    fontSize: '13px',
  },
  statusIndicator: {
    display: 'inline-flex',
    alignItems: 'center',
    padding: '6px 12px',
    borderRadius: '8px',
    fontSize: '13px',
    fontWeight: 600,
    marginBottom: '12px',
  },
  postDesc: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    lineHeight: 1.5,
    margin: '0 0 16px',
  },
  viewLink: {
    textDecoration: 'none',
  },
  viewBtn: {
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
  },
  emptyState: {
    position: 'absolute',
    bottom: '20px',
    left: '50%',
    transform: 'translateX(-50%)',
    background: 'linear-gradient(160deg, #065f46 0%, #0e7490 100%)',
    borderRadius: '20px',
    padding: '24px 32px',
    textAlign: 'center',
    boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
    zIndex: 1000,
  },
  emptyIcon: {
    fontSize: '48px',
    marginBottom: '12px',
  },
  emptyTitle: {
    color: theme.colors.text,
    fontSize: '18px',
    fontWeight: 600,
    margin: '0 0 8px',
  },
  emptyText: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    margin: '0 0 16px',
    maxWidth: '280px',
  },
};
