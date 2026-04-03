import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, Circle } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Map as MapIcon, User, Clock } from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import type { Post } from '../types';
import { Card, Button, Spinner } from '../components';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';
import { geocodeCity } from '../utils/geocoding';

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const CATEGORY_ICONS: Record<string, string> = {
  'Дрова': 'DRW', 'Уборка': 'UBN', 'Ремонт': 'RMT', 'Доставка': 'DST',
  'Покупки': 'PKP', 'Готовка': 'GTK', 'Садоводство': 'SDV', 'Перевозка': 'PRV',
  'Уход за животными': 'JIV', 'Помощь с детьми': 'DTI', 'Компьютерная помощь': 'KMP',
  'Стрижка': 'STR', 'Медицинская помощь': 'MDC', 'Юридическая консультация': 'YUR',
  'Обучение': 'OBU', 'Репетитор': 'RPT', 'Транспорт': 'TRN', 'Строительство': 'STR',
  'Электрика': 'ELK', 'Сантехника': 'SNK', 'Погрузка/Разгрузка': 'PGR',
};

const getCategoryIcon = (category: string): string => CATEGORY_ICONS[category] || category.substring(0, 3).toUpperCase();

const getStatusColor = (status: string): string => {
  const colors: Record<string, string> = {
    OPEN: '#10B981',
    IN_PROGRESS: '#38bdf8',
    COMPLETED: '#F59E0B',
    CANCELLED: '#EF4444',
  };
  return colors[status] || '#6B7280';
};

const createCustomIcon = (category: string, status: string, isSelected: boolean) => {
  const icon = getCategoryIcon(category);
  const statusColor = getStatusColor(status);
  const size = isSelected ? 44 : 38;
  const borderColor = isSelected ? '#22d3ee' : statusColor;
  
  return L.divIcon({
    className: 'custom-marker',
    html: `
      <div style="
        width: ${size}px;
        height: ${size}px;
        background: linear-gradient(135deg, ${statusColor}40 0%, ${statusColor}80 100%);
        border: 3px solid ${borderColor};
        border-radius: 50% 50% 50% 0;
        transform: rotate(-45deg);
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 3px 8px rgba(0,0,0,0.3);
      ">
        <span style="
          transform: rotate(45deg);
          font-size: ${isSelected ? '18px' : '14px'};
        ">${icon}</span>
      </div>
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size],
    popupAnchor: [0, -size],
  });
};

const RADIUS_OPTIONS = [5, 10, 25, 50];

const BELARUS_CITIES: Record<string, { lat: number; lng: number }> = {
  'гомель': { lat: 52.4415, lng: 30.9877 },
  'гомельск': { lat: 52.4415, lng: 30.9877 },
  'речица': { lat: 52.3567, lng: 30.4366 },
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
  'добруш': { lat: 52.4167, lng: 31.3167 },
  'ветка': { lat: 52.3500, lng: 31.0833 },
  'хойники': { lat: 51.8833, lng: 29.1667 },
};

export const MapPage = () => {
  const { user } = useAuth();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [radius, setRadius] = useState(10);
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [selectedPost, setSelectedPost] = useState<Post | null>(null);
  const [userCity, setUserCity] = useState<string>('');
  const [geoLoading, setGeoLoading] = useState(false);

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
      coords = await geocodeCity(simplified + ', Belarus');
      if (coords) return coords;
    }
    
    coords = await geocodeCity(cityName + ', Belarus');
    if (coords) return coords;
    
    return null;
  };

  useEffect(() => {
    const loadUserData = async () => {
      if (user) {
        setGeoLoading(true);
        try {
          const userData = await authApi.getCurrentUser();
          if ('city' in userData && userData.city) {
            setUserCity(userData.city);
            if (!userData.latitude || !userData.longitude) {
              const coords = await tryGeocode(userData.city);
              if (coords) {
                setUserLocation({ lat: coords.lat, lng: coords.lng });
              }
            } else {
              setUserLocation({ lat: userData.latitude, lng: userData.longitude });
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
  }, [radius, userLocation]);

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

  const loadPosts = async () => {
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
              const simple = getSimplifiedCity(post.userCity);
              const simpleCoords = await tryGeocode(simple);
              if (simpleCoords) {
                return { ...post, latitude: simpleCoords.lat, longitude: simpleCoords.lng };
              }
            }
          }
          return post;
        })
      );
      
      const filteredPosts = postsWithCoords
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
  };

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
      (error) => {
        setLocationError('Не удалось определить местоположение');
        setLoading(false);
      }
    );
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

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}><MapIcon size={28} style={{marginRight: 10, verticalAlign: 'middle'}} /> Карта помощи</h1>
          {userLocation && userCity && <p style={styles.subtitle}>Показываем посты рядом с {userCity}</p>}
        </div>
        <div style={styles.headerActions}>
          <div style={styles.radiusSelector}>
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
          {!userLocation && (
            <Button onClick={handleUseMyLocation} style={styles.locationBtn}>
              <MapPin size={16} style={{marginRight: 6}} /> Моё местоположение
            </Button>
          )}
        </div>
      </div>

      {locationError && (
        <Card style={styles.errorCard}>
          <p>{locationError}</p>
          <Button onClick={handleUseMyLocation} style={styles.locationBtn}>
            <MapPin size={16} style={{marginRight: 6}} /> Моё местоположение
          </Button>
        </Card>
      )}

      <div style={styles.mapContainer}>
        {loading || geoLoading ? (
          <Spinner message={geoLoading ? "Определяем местоположение..." : "Загрузка карты..."} />
        ) : (
          <>
            <MapContainer 
              center={userLocation ? [userLocation.lat, userLocation.lng] : [52.4415, 30.9877]}
              zoom={radius > 25 ? 8 : radius > 10 ? 10 : 11}
              style={styles.map}
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
                    fillOpacity: 0.1
                  }}
                />
              )}

              {(() => {
                const userGroups = new Map<number, typeof posts>();
                posts.filter(p => p.latitude && p.longitude).forEach(post => {
                  const existing = userGroups.get(post.userId) || [];
                  userGroups.set(post.userId, [...existing, post]);
                });
                
                let groupIndex = 0;
                return Array.from(userGroups.entries()).map(([userId, userPosts]) => {
                  const mainPost = userPosts[0];
                  const offset = (groupIndex % 6) * 0.003;
                  const offsetY = Math.floor(groupIndex / 6) * 0.003;
                  groupIndex++;
                  
                  const openCount = userPosts.filter(p => p.status === 'OPEN').length;
                  const inProgressCount = userPosts.filter(p => p.status === 'IN_PROGRESS').length;
                  
                  return (
                    <Marker 
                      key={userId}
                      position={[mainPost.latitude! + offsetY, mainPost.longitude! + offset]}
                      icon={createCustomIcon(mainPost.category, mainPost.status, selectedPost?.userId === userId)}
                      eventHandlers={{
                        click: () => setSelectedPost(mainPost),
                      }}
                    >
                      <Popup>
                        <div style={{
                          minWidth: '240px',
                          background: '#065F46',
                          borderRadius: '12px',
                          padding: '12px',
                        }}>
                          <div style={{display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px'}}>
                            <User size={28} color="#22d3ee" />
                            <div>
                              <strong style={{color: '#fff', fontSize: '16px', display: 'block'}}>{mainPost.authorName}</strong>
                              <span style={{color: '#22d3ee', fontSize: '12px'}}><MapPin size={12} style={{verticalAlign: 'middle', marginRight: 4}} />{mainPost.userCity}</span>
                            </div>
                          </div>
                          
                          {(openCount > 0 || inProgressCount > 0) && (
                            <div style={{display: 'flex', gap: '6px', marginBottom: '10px'}}>
                              {openCount > 0 && (
                                <span style={{
                                  background: 'rgba(16, 185, 129, 0.2)',
                                  border: '1px solid rgba(16, 185, 129, 0.5)',
                                  color: '#34d399',
                                  fontSize: '10px',
                                  padding: '3px 8px',
                                  borderRadius: '10px',
                                  fontWeight: 600,
                                }}>● {openCount} Открыто</span>
                              )}
                              {inProgressCount > 0 && (
                                <span style={{
                                  background: 'rgba(56, 189, 248, 0.2)',
                                  border: '1px solid rgba(56, 189, 248, 0.5)',
                                  color: '#38bdf8',
                                  fontSize: '10px',
                                  padding: '3px 8px',
                                  borderRadius: '10px',
                                  fontWeight: 600,
                                }}>● {inProgressCount} В работе</span>
                              )}
                            </div>
                          )}
                          
                          <div style={{borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: '10px', maxHeight: '150px', overflowY: 'auto'}}>
                            {userPosts.map(p => (
                              <div key={p.id} style={{marginBottom: '10px', padding: '8px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px'}}>
                                <Link to={`/posts/${p.id}`} style={{color: '#fff', textDecoration: 'none', fontSize: '13px', display: 'block', marginBottom: '4px'}}>
                                  {getCategoryIcon(p.category)} {p.title.length > 28 ? p.title.substring(0, 28) + '...' : p.title}
                                </Link>
                                <div style={{display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px'}}>
                                  <span style={{color: '#22d3ee'}}>{p.category}</span>
                                  <span style={{
                                    color: getStatusColor(p.status),
                                    background: `${getStatusColor(p.status)}20`,
                                    padding: '2px 6px',
                                    borderRadius: '6px',
                                    fontSize: '10px',
                                    fontWeight: 500,
                                  }}>{getStatusLabel(p.status)}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </Popup>
                    </Marker>
                  );
                });
              })()}
            </MapContainer>

            {selectedPost && (
              <Card style={styles.postCard}>
                <div style={styles.postHeader}>
                  <span style={{
                    ...styles.statusDot,
                    backgroundColor: getStatusColor(selectedPost.status)
                  }} />
                  <span>{getStatusLabel(selectedPost.status)}</span>
                  <span style={styles.category}>{selectedPost.category}</span>
                </div>
                <h3 style={styles.postTitle}>{selectedPost.title}</h3>
                <p style={styles.postDesc}>{selectedPost.description.substring(0, 100)}...</p>
                <div style={styles.postMeta}>
                  <span><User size={14} style={{verticalAlign: 'middle', marginRight: 4}} />{selectedPost.authorName}</span>
                  <span><MapPin size={14} style={{verticalAlign: 'middle', marginRight: 4}} />{selectedPost.userCity || 'Город не указан'}</span>
                  <span><Clock size={14} style={{verticalAlign: 'middle', marginRight: 4}} />{getRelativeTime(selectedPost.createdAt)}</span>
                </div>
                <Link to={`/posts/${selectedPost.id}`}>
                  <Button style={styles.viewBtn}>Смотреть пост</Button>
                </Link>
              </Card>
            )}
          </>
        )}
      </div>

      {posts.length === 0 && !loading && userLocation && (
        <Card style={styles.emptyCard}>
          <p>В выбранном радиусе нет открытых постов о помощи</p>
          <p>Попробуйте увеличить радиус поиска</p>
        </Card>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: '100%',
    padding: '24px',
    height: 'calc(100vh - 120px)',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '16px',
    flexWrap: 'wrap',
    gap: '16px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '24px',
    fontWeight: 700,
    margin: 0,
  },
  subtitle: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    marginTop: '4px',
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
    gap: '8px',
  },
  radiusLabel: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  radiusBtn: {
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: '8px',
    padding: '8px 14px',
    color: theme.colors.textSecondary,
    fontSize: '13px',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  radiusBtnActive: {
    background: 'rgba(6, 182, 212, 0.2)',
    border: `1px solid ${theme.colors.accent}`,
    color: theme.colors.accentLight,
  },
  locationBtn: {
    padding: '10px 16px',
  },
  errorCard: {
    textAlign: 'center',
    padding: '24px',
    marginBottom: '16px',
    color: theme.colors.textMuted,
  },
  mapContainer: {
    flex: 1,
    display: 'flex',
    gap: '16px',
    borderRadius: '16px',
    overflow: 'hidden',
    position: 'relative',
    minHeight: '500px',
  },
  map: {
    flex: 1,
    height: '500px',
    borderRadius: '16px',
    zIndex: 1,
  },
  popupLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '13px',
    display: 'inline-block',
    marginTop: '4px',
  },
  postCard: {
    position: 'absolute',
    bottom: '20px',
    right: '20px',
    width: '300px',
    zIndex: 1000,
    padding: '16px',
  },
  postHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    marginBottom: '8px',
    fontSize: '12px',
    color: theme.colors.textMuted,
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  category: {
    color: theme.colors.accentLight,
    fontSize: '11px',
    marginLeft: 'auto',
  },
  postTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    margin: '0 0 8px',
  },
  postDesc: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    margin: '0 0 12px',
  },
  postMeta: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '12px',
    fontSize: '12px',
    color: theme.colors.textSecondary,
    marginBottom: '12px',
  },
  viewBtn: {
    width: '100%',
  },
  emptyCard: {
    textAlign: 'center',
    padding: '40px',
    marginTop: '16px',
    color: theme.colors.textMuted,
  },
};
