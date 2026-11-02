import {
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Link } from 'react-router-dom';
import L from 'leaflet';
import {
  Circle,
  MapContainer,
  Marker,
  ScaleControl,
  TileLayer,
  Tooltip,
  ZoomControl,
} from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ChevronDown,
  CircleSlash2,
  LoaderCircle,
  LocateFixed,
  Map as MapIcon,
  MapPin,
  MapPinOff,
  Maximize2,
  RefreshCw,
  RotateCcw,
  Search,
  SearchX,
  ShieldCheck,
  TreeDeciduous,
  X,
} from 'lucide-react';
import 'leaflet/dist/leaflet.css';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import { postsApi } from '../api/postsApi';
import type { Post } from '../types';
import { MapPostCard } from '../components/MapPostCard';
import { getCategoryIcon } from '../utils/categoryIcons';
import { hasCoordinates } from '../utils/coordinates';
import './MapPage.css';

const LIST_BATCH = 12;
const DEFAULT_ZOOM = 6;

type TileMode = 'dark' | 'light';

const TILE_PROVIDERS: Record<
  TileMode,
  { url: string; attribution: string; subdomains: string; maxZoom: number }
> = {
  // CARTO basemaps are public and keyless. Attribution is required.
  dark: {
    url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
    subdomains: 'abcd',
    maxZoom: 20,
  },
  light: {
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    subdomains: 'abc',
    maxZoom: 19,
  },
};

type GeoState =
  | { status: 'idle' }
  | { status: 'locating' }
  | { status: 'granted'; position: [number, number]; accuracy: number }
  | { status: 'denied' }
  | { status: 'unavailable' }
  | { status: 'timeout' }
  | { status: 'unsupported' };

const POST_ICONS = new Map<string, L.DivIcon>();

/** Russian plural for "заявка" used in the "no coordinates" note. */
const postWord = (count: number): string => {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return 'заявка';
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'заявки';
  return 'заявок';
};

/**
 * Markers use the real category icon and the same lime accent for every post.
 * The selected marker is the only one that flips to a filled lime pin.
 */
const createPostIcon = (post: Post, selected: boolean): L.DivIcon => {
  const cacheKey = `${post.category}|${selected ? 'selected' : 'default'}`;
  const cached = POST_ICONS.get(cacheKey);
  if (cached) return cached;

  const CategoryIcon = getCategoryIcon(post.category);
  let glyph = '';
  try {
    glyph = renderToStaticMarkup(
      <CategoryIcon size={15} strokeWidth={2.2} aria-hidden="true" />
    );
  } catch {
    glyph = '';
  }

  const icon = L.divIcon({
    className: 'map-pin-wrapper',
    html: `<span class="map-pin${selected ? ' map-pin--selected' : ''}"><span class="map-pin-glyph">${glyph}</span></span>`,
    iconSize: [40, 40],
    iconAnchor: [20, 38],
  });

  POST_ICONS.set(cacheKey, icon);
  return icon;
};

const createClusterIcon = (cluster: L.MarkerCluster): L.DivIcon => {
  const count = cluster.getChildCount();
  const size = count < 10 ? 46 : count < 50 ? 54 : 62;

  return L.divIcon({
    className: 'map-cluster-wrapper',
    html: `<span class="map-cluster" style="width:${size}px;height:${size}px" role="img" aria-label="${count} заявок"><span class="map-cluster-count">${count}</span><span class="map-cluster-caption">заявок</span></span>`,
    iconSize: L.point(size, size),
    iconAnchor: L.point(size / 2, size / 2),
  });
};

const USER_ICON = L.divIcon({
  className: 'map-user-wrapper',
  html: '<span class="map-user-dot"></span>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
});

export const MapPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<'network' | 'access' | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [category, setCategory] = useState('');
  const [cityFilter, setCityFilter] = useState('');

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [scrollTick, setScrollTick] = useState(0);
  const [visibleCount, setVisibleCount] = useState(LIST_BATCH);
  const [mobileView, setMobileView] = useState<'list' | 'map'>('map');

  const [mapReady, setMapReady] = useState(false);
  const [pendingRevealId, setPendingRevealId] = useState<number | null>(null);
  const [tilesLoading, setTilesLoading] = useState(true);
  const [tileError, setTileError] = useState(false);
  const [tileMode, setTileMode] = useState<TileMode>('dark');
  const [tileReload, setTileReload] = useState(0);

  const [geo, setGeo] = useState<GeoState>({ status: 'idle' });

  const mapRef = useRef<L.Map | null>(null);
  const clusterRef = useRef<L.MarkerClusterGroup | null>(null);
  const markerRefs = useRef(new Map<number, L.Marker>());
  const itemRefs = useRef(new Map<number, HTMLLIElement>());
  const requestRef = useRef(0);
  const geoRequestRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    const sequence = ++requestRef.current;

    setLoading(true);
    setError(null);

    postsApi
      .getMapPosts(controller.signal)
      .then((data) => {
        if (sequence !== requestRef.current) return;
        setPosts(Array.isArray(data) ? data.filter(Boolean) : []);
        setLoading(false);
      })
      .catch((requestError: unknown) => {
        if (controller.signal.aborted || sequence !== requestRef.current) return;

        const status = (requestError as { response?: { status?: number } })?.response
          ?.status;
        setError(status === 401 || status === 403 ? 'access' : 'network');
        setLoading(false);
      });

    return () => controller.abort();
  }, [reloadTick]);

  const categoryOptions = useMemo(() => {
    const values = new Set(posts.map((post) => post.category).filter(Boolean));
    if (category) values.add(category);
    return Array.from(values).sort((left, right) => left.localeCompare(right, 'ru'));
  }, [posts, category]);

  const cityOptions = useMemo(() => {
    const values = new Set(
      posts.map((post) => post.userCity?.trim()).filter(Boolean) as string[]
    );
    if (cityFilter) values.add(cityFilter);
    return Array.from(values).sort((left, right) => left.localeCompare(right, 'ru'));
  }, [posts, cityFilter]);

  const locatedPosts = useMemo(() => posts.filter(hasCoordinates), [posts]);

  const filteredPosts = useMemo(() => {
    const query = deferredSearch.trim().toLocaleLowerCase('ru');

    return posts.filter((post) => {
      if (category && post.category !== category) return false;
      if (cityFilter && (post.userCity?.trim() || '') !== cityFilter) return false;
      if (!query) return true;

      return [post.title, post.description, post.category, post.authorName, post.userCity]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase('ru').includes(query));
    });
  }, [posts, category, cityFilter, deferredSearch]);

  const filteredLocated = useMemo(() => filteredPosts.filter(hasCoordinates), [filteredPosts]);

  const selectedPost = useMemo(
    () => filteredPosts.find((post) => post.id === selectedId) ?? null,
    [filteredPosts, selectedId]
  );

  const visiblePosts = useMemo(
    () => filteredPosts.slice(0, visibleCount),
    [filteredPosts, visibleCount]
  );

  const unlocatedAllCount = posts.length - locatedPosts.length;
  const unlocatedFilteredCount = filteredPosts.length - filteredLocated.length;
  const hasActiveFilters = Boolean(search.trim() || category || cityFilter);
  const initialLoading = loading && posts.length === 0;
  const fatalError = Boolean(error) && posts.length === 0;
  const isLocating = geo.status === 'locating';
  const tileProvider = TILE_PROVIDERS[tileMode];
  const filterKey = `${deferredSearch.trim()}|${category}|${cityFilter}`;

  useEffect(() => {
    setVisibleCount(LIST_BATCH);
  }, [filterKey]);

  useEffect(() => {
    if (!selectedPost) return;
    const index = filteredPosts.findIndex((post) => post.id === selectedPost.id);
    if (index >= visibleCount) setVisibleCount(index + 1);
  }, [selectedPost, filteredPosts, visibleCount]);

  useEffect(() => {
    if (!selectedId) return;
    const node = itemRefs.current.get(selectedId);
    if (!node) return;

    const reduceMotion =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    node.scrollIntoView({
      block: 'nearest',
      behavior: reduceMotion ? 'auto' : 'smooth',
    });
  }, [selectedId, scrollTick, visibleCount]);

  useEffect(() => {
    if (locatedPosts.length > 0) return;
    setMapReady(false);
    setTilesLoading(true);
    setTileError(false);
  }, [locatedPosts.length]);

  const fitPosts = useCallback((list: Post[], animate = true) => {
    const map = mapRef.current;
    if (!map) return;

    const points = list
      .filter(hasCoordinates)
      .map((post) => [post.latitude, post.longitude] as [number, number]);

    if (points.length === 0) return;

    if (points.length === 1) {
      map.flyTo(points[0], 12, { animate, duration: animate ? 0.6 : 0 });
      return;
    }

    map.fitBounds(L.latLngBounds(points), {
      padding: [56, 56],
      maxZoom: 12,
      animate,
      duration: animate ? 0.6 : 0,
    });
  }, []);

  useEffect(() => {
    if (!mapReady || filteredLocated.length === 0) return;
    fitPosts(filteredLocated, false);
  }, [mapReady, filteredLocated, fitPosts]);

  useEffect(() => {
    if (mobileView !== 'map' || !mapReady) return undefined;
    const timer = window.setTimeout(() => mapRef.current?.invalidateSize(), 80);
    return () => window.clearTimeout(timer);
  }, [mobileView, mapReady]);

  useEffect(() => {
    if (!mapReady) return undefined;
    const map = mapRef.current;
    const node = map?.getContainer();
    if (!map || !node || typeof ResizeObserver === 'undefined') return undefined;

    const observer = new ResizeObserver(() => {
      const rect = node.getBoundingClientRect();
      // Hidden mobile tab has zero size; resizing Leaflet to 0x0 makes it drop
      // markers and tiles, so only sync when the panel is actually visible.
      if (rect.width > 0 && rect.height > 0) map.invalidateSize();
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [mapReady]);

  useEffect(
    () => () => {
      geoRequestRef.current += 1;
    },
    []
  );

  const handleMapRef = useCallback((map: L.Map | null) => {
    mapRef.current = map;
  }, []);

  const revealPost = useCallback((post: Post) => {
    if (!hasCoordinates(post)) return;
    const map = mapRef.current;
    if (!map) return;

    const lat = post.latitude;
    const lng = post.longitude;
    const marker = markerRefs.current.get(post.id);
    const cluster = clusterRef.current;

    if (cluster && marker && cluster.getVisibleParent(marker) !== marker) {
      cluster.zoomToShowLayer(marker, () => {
        mapRef.current?.panInside(marker.getLatLng(), {
          padding: [72, 72],
          animate: true,
        });
      });
      return;
    }

    map.flyTo([lat, lng], Math.max(map.getZoom(), 12), { duration: 0.65 });
  }, []);

  useEffect(() => {
    if (pendingRevealId === null || !mapReady) return undefined;

    const post = filteredPosts.find((item) => item.id === pendingRevealId);
    if (!post || !hasCoordinates(post)) {
      setPendingRevealId(null);
      return undefined;
    }

    const timer = window.setTimeout(() => {
      revealPost(post);
      setPendingRevealId(null);
    }, 160);
    return () => window.clearTimeout(timer);
  }, [pendingRevealId, mapReady, filteredPosts, revealPost]);

  const handleSelectFromList = useCallback((post: Post) => {
    setSelectedId(post.id);
    setMobileView('map');
    setPendingRevealId(post.id);
  }, []);

  const handleSelectFromMarker = useCallback(
    (post: Post) => {
      setSelectedId(post.id);
      setScrollTick((tick) => tick + 1);

      const index = filteredPosts.findIndex((item) => item.id === post.id);
      if (index >= 0) setVisibleCount((count) => Math.max(count, index + 1));
    },
    [filteredPosts]
  );

  const resetFilters = useCallback(() => {
    setSearch('');
    setCategory('');
    setCityFilter('');
    setSelectedId(null);
  }, []);

  const handleLocate = useCallback(() => {
    if (geo.status === 'granted') {
      mapRef.current?.flyTo(geo.position, 12, { duration: 0.7 });
      return;
    }

    if (!('geolocation' in navigator)) {
      setGeo({ status: 'unsupported' });
      return;
    }

    setGeo({ status: 'locating' });
    const requestId = ++geoRequestRef.current;

    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (requestId !== geoRequestRef.current) return;

        const coords: [number, number] = [
          position.coords.latitude,
          position.coords.longitude,
        ];
        setGeo({
          status: 'granted',
          position: coords,
          accuracy: position.coords.accuracy,
        });
        mapRef.current?.flyTo(coords, 12, { duration: 0.8 });
      },
      (geoError) => {
        if (requestId !== geoRequestRef.current) return;

        if (geoError.code === 1) setGeo({ status: 'denied' });
        else if (geoError.code === 3) setGeo({ status: 'timeout' });
        else setGeo({ status: 'unavailable' });
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 }
    );
  }, [geo]);

  const geoNotice = useMemo(() => {
    switch (geo.status) {
      case 'locating':
        return {
          tone: 'info',
          title: 'Определяем местоположение',
          text: 'Подтвердите доступ в браузере. Координаты нужны только для центрирования карты и не отправляются на сервер.',
          retry: false,
        };
      case 'denied':
        return {
          tone: 'warning',
          title: 'Доступ к геолокации закрыт',
          text: 'Разрешите определение местоположения в настройках браузера для этого сайта. Карта продолжает работать и без ваших координат.',
          retry: true,
        };
      case 'unavailable':
        return {
          tone: 'warning',
          title: 'Не удалось определить местоположение',
          text: 'Проверьте, включены ли службы геолокации на устройстве, и попробуйте снова. Заявки на карте доступны и без ваших координат.',
          retry: true,
        };
      case 'timeout':
        return {
          tone: 'warning',
          title: 'Браузер не успел определить местоположение',
          text: 'Попробуйте ещё раз или продолжите работу с картой без центрирования на вас.',
          retry: true,
        };
      case 'unsupported':
        return {
          tone: 'warning',
          title: 'Геолокация недоступна',
          text: 'Ваш браузер не поддерживает Geolocation API. Карта и заявки работают без определения вашего местоположения.',
          retry: false,
        };
      default:
        return null;
    }
  }, [geo.status]);

  const retryTiles = () => {
    setTileMode((mode) => (mode === 'dark' ? 'light' : mode));
    setTileReload((tick) => tick + 1);
    setTileError(false);
    setTilesLoading(true);
  };

  const handleTileError = useCallback(() => {
    setTilesLoading(false);
    setTileError(true);
  }, []);

  return (
    <div className="map-page">
      <header className="map-hero">
        <div className="map-hero-text">
          <h1>Помощь рядом</h1>
          <p>Находите людей, которым нужна поддержка, и добрые дела рядом с вами.</p>
        </div>

        {!initialLoading && !fatalError && posts.length > 0 && (
          <div className="map-hero-stats" aria-label="Сводка по заявкам">
            <span className="map-stat">
              <strong>{posts.length}</strong> открытых
            </span>
            <span className="map-stat">
              <strong>{locatedPosts.length}</strong> на карте
            </span>
            {unlocatedAllCount > 0 && (
              <span className="map-stat map-stat--muted">
                <strong>{unlocatedAllCount}</strong> без координат
              </span>
            )}
          </div>
        )}
      </header>

      {!fatalError && (loading || posts.length > 0) && (
        <form
          className="map-toolbar"
          role="search"
          onSubmit={(event) => event.preventDefault()}
        >
          <div className="map-search">
            <Search className="map-search-icon" size={18} aria-hidden="true" />
            <input
              type="search"
              value={search}
              placeholder="Поиск по названию, описанию, автору или городу…"
              aria-label="Поиск заявок на карте"
              autoComplete="off"
              onChange={(event) => setSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape' && search) setSearch('');
              }}
            />
            {search && (
              <button
                type="button"
                className="map-search-clear"
                onClick={() => setSearch('')}
                aria-label="Очистить поиск"
              >
                <X size={15} aria-hidden="true" />
              </button>
            )}
          </div>

          <label className="map-field">
            <span>Категория</span>
            <span className="map-select-wrap">
              <select
                value={category}
                onChange={(event) => setCategory(event.target.value)}
              >
                <option value="">Все категории</option>
                {categoryOptions.map((option) => (
                  <option value={option} key={option}>
                    {option}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} aria-hidden="true" />
            </span>
          </label>

          <label className="map-field">
            <span>Город</span>
            <span className="map-select-wrap">
              <select
                value={cityFilter}
                onChange={(event) => setCityFilter(event.target.value)}
              >
                <option value="">Все города</option>
                {cityOptions.map((option) => (
                  <option value={option} key={option}>
                    {option}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} aria-hidden="true" />
            </span>
          </label>

          <button
            type="button"
            className="map-reset"
            onClick={resetFilters}
            disabled={!hasActiveFilters}
          >
            <RotateCcw size={15} aria-hidden="true" />
            Сбросить
          </button>
        </form>
      )}

      {!fatalError && hasActiveFilters && (
        <div className="map-active" aria-label="Активные фильтры">
          {search.trim() && (
            <span className="map-chip">
              <Search size={12} aria-hidden="true" />
              <span className="map-chip-label">Поиск: {search.trim()}</span>
              <button
                type="button"
                onClick={() => setSearch('')}
                aria-label="Убрать поиск"
              >
                <X size={12} aria-hidden="true" />
              </button>
            </span>
          )}

          {category && (
            <span className="map-chip">
              {(() => {
                const CategoryIcon = getCategoryIcon(category);
                return <CategoryIcon size={12} aria-hidden="true" />;
              })()}
              <span className="map-chip-label">{category}</span>
              <button
                type="button"
                onClick={() => setCategory('')}
                aria-label={`Убрать категорию: ${category}`}
              >
                <X size={12} aria-hidden="true" />
              </button>
            </span>
          )}

          {cityFilter && (
            <span className="map-chip">
              <MapPin size={12} aria-hidden="true" />
              <span className="map-chip-label">{cityFilter}</span>
              <button
                type="button"
                onClick={() => setCityFilter('')}
                aria-label={`Убрать город: ${cityFilter}`}
              >
                <X size={12} aria-hidden="true" />
              </button>
            </span>
          )}

          <button type="button" className="map-active-reset" onClick={resetFilters}>
            Сбросить всё
          </button>
        </div>
      )}

      {!fatalError && (
        <p className="map-privacy">
          <ShieldCheck size={15} aria-hidden="true" />
          <span>
            Геолокация включается только по кнопке «Рядом со мной» и работает в
            браузере. Ваши координаты не отправляются на сервер.
          </span>
        </p>
      )}

      {!fatalError && geoNotice && (
        <div className={`map-geo-notice map-geo-notice--${geoNotice.tone}`} role="status">
          <span className="map-geo-icon">
            {isLocating ? (
              <LoaderCircle className="map-spin" size={18} aria-hidden="true" />
            ) : (
              <LocateFixed size={18} aria-hidden="true" />
            )}
          </span>
          <div className="map-geo-copy">
            <strong>{geoNotice.title}</strong>
            <p>{geoNotice.text}</p>
          </div>
          {geoNotice.retry && (
            <button
              type="button"
              className="map-geo-retry"
              onClick={handleLocate}
              disabled={isLocating}
            >
              Попробовать снова
            </button>
          )}
          <button
            type="button"
            className="map-geo-dismiss"
            onClick={() => setGeo({ status: 'idle' })}
            aria-label="Скрыть сообщение о геолокации"
          >
            <X size={15} aria-hidden="true" />
          </button>
        </div>
      )}

      {error && posts.length > 0 && (
        <div className="map-error-banner" role="alert">
          <RefreshCw size={16} aria-hidden="true" />
          <span>Не удалось обновить данные карты.</span>
          <button type="button" onClick={() => setReloadTick((tick) => tick + 1)}>
            Повторить
          </button>
        </div>
      )}

      {fatalError ? (
        <div className="map-state">
          <span className="map-state-icon">
            {error === 'access' ? (
              <CircleSlash2 size={26} aria-hidden="true" />
            ) : (
              <RefreshCw size={26} aria-hidden="true" />
            )}
          </span>
          <h2>
            {error === 'access'
              ? 'Нет доступа к карте заявок'
              : 'Не удалось загрузить заявки'}
          </h2>
          <p>
            {error === 'access'
              ? 'Сессия истекла или у аккаунта нет прав на просмотр карты. Войдите снова, чтобы продолжить.'
              : 'Проверьте соединение с сервером и попробуйте ещё раз. Данные не подменяются заглушками.'}
          </p>
          <div className="map-state-actions">
            {error === 'access' ? (
              <Link className="map-btn map-btn--primary" to="/login">
                Войти снова
              </Link>
            ) : (
              <button
                type="button"
                className="map-btn map-btn--primary"
                onClick={() => setReloadTick((tick) => tick + 1)}
              >
                <RefreshCw size={15} aria-hidden="true" />
                Повторить
              </button>
            )}
            <Link className="map-btn map-btn--ghost" to="/posts">
              Перейти в каталог
            </Link>
          </div>
        </div>
      ) : !loading && posts.length === 0 ? (
        <div className="map-state">
          <span className="map-state-icon">
            <TreeDeciduous size={26} aria-hidden="true" />
          </span>
          <h2>Сейчас нет открытых заявок</h2>
          <p>
            Как только кому-то понадобится помощь, заявка появится на карте и в
            списке. Карта не заполняется вымышленными точками.
          </p>
          <div className="map-state-actions">
            <Link className="map-btn map-btn--primary" to="/posts">
              Перейти в каталог
            </Link>
          </div>
        </div>
      ) : (
        <>
          <div className="map-view-switch" role="group" aria-label="Список или карта">
            <button
              type="button"
              className={mobileView === 'list' ? 'is-active' : ''}
              aria-pressed={mobileView === 'list'}
              onClick={() => setMobileView('list')}
            >
              <span className="map-view-icon" aria-hidden="true">
                <svg viewBox="0 0 16 16" width="15" height="15" fill="none">
                  <path
                    d="M3 4h10M3 8h10M3 12h10"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                  />
                </svg>
              </span>
              Список
              <span className="map-view-count">{filteredPosts.length}</span>
            </button>
            <button
              type="button"
              className={mobileView === 'map' ? 'is-active' : ''}
              aria-pressed={mobileView === 'map'}
              onClick={() => setMobileView('map')}
            >
              <MapIcon size={15} aria-hidden="true" />
              Карта
              <span className="map-view-count">{filteredLocated.length}</span>
            </button>
          </div>

          <p className="map-results" aria-live="polite">
            {initialLoading ? (
              'Загружаем заявки…'
            ) : (
              <>
                Найдено: <strong>{filteredPosts.length}</strong>
                <span className="map-results-sep">·</span>
                На карте: <strong>{filteredLocated.length}</strong>
                {unlocatedFilteredCount > 0 && (
                  <>
                    <span className="map-results-sep">·</span>
                    Без координат: <strong>{unlocatedFilteredCount}</strong>
                  </>
                )}
                {loading && (
                  <LoaderCircle
                    className="map-spin map-results-spinner"
                    size={13}
                    aria-label="Обновление"
                  />
                )}
              </>
            )}
          </p>

          <div className={`map-layout is-${mobileView}-view`}>
            <section className="map-list-panel" aria-label="Список заявок">
              <div className="map-list-head">
                <div>
                  <h2>Заявки</h2>
                  <p>Открытые заявки</p>
                </div>
                <span className="map-list-count">
                  {initialLoading ? '…' : filteredPosts.length}
                </span>
              </div>

              {initialLoading ? (
                <div className="map-list-skeletons" aria-label="Загрузка заявок">
                  {Array.from({ length: 4 }, (_, index) => (
                    <span className="map-skeleton-card" key={index} aria-hidden="true" />
                  ))}
                </div>
              ) : filteredPosts.length === 0 ? (
                <div className="map-list-empty">
                  <span className="map-list-empty-icon">
                    <SearchX size={24} aria-hidden="true" />
                  </span>
                  <h3>Ничего не найдено</h3>
                  <p>Измените запрос или сбросьте фильтры.</p>
                  <button
                    type="button"
                    className="map-btn map-btn--ghost"
                    onClick={resetFilters}
                  >
                    <RotateCcw size={14} aria-hidden="true" />
                    Сбросить фильтры
                  </button>
                </div>
              ) : (
                <>
                  {unlocatedAllCount > 0 && (
                    <p className="map-list-note">
                      <MapPinOff size={13} aria-hidden="true" />
                      {unlocatedAllCount === 1
                        ? '1 заявка без координат — доступна только в списке.'
                        : `${unlocatedAllCount} ${postWord(unlocatedAllCount)} без координат — доступны только в списке.`}
                    </p>
                  )}

                  <ul className="map-list-scroll">
                    {visiblePosts.map((post) => (
                      <li
                        key={post.id}
                        ref={(node) => {
                          if (node) itemRefs.current.set(post.id, node);
                          else itemRefs.current.delete(post.id);
                        }}
                      >
                        <MapPostCard
                          post={post}
                          variant="list"
                          isSelected={selectedPost?.id === post.id}
                          onSelect={handleSelectFromList}
                        />
                      </li>
                    ))}
                  </ul>

                  {visiblePosts.length < filteredPosts.length && (
                    <button
                      type="button"
                      className="map-load-more"
                      onClick={() => setVisibleCount((count) => count + LIST_BATCH)}
                    >
                      Показать ещё{' '}
                      {Math.min(LIST_BATCH, filteredPosts.length - visiblePosts.length)}
                    </button>
                  )}
                </>
              )}
            </section>

            <section className="map-panel" aria-label="Карта заявок">
              <div className="map-panel-head">
                <div className="map-panel-title">
                  <span className="map-panel-icon" aria-hidden="true">
                    <MapPin size={16} />
                  </span>
                  <div>
                    <h2>Карта заявок</h2>
                    <p>
                      {initialLoading
                        ? 'Получаем координаты…'
                        : `${filteredLocated.length} на карте`}
                    </p>
                  </div>
                </div>

                <div className="map-tools">
                  <button
                    type="button"
                    className="map-tool"
                    onClick={() => fitPosts(filteredLocated)}
                    disabled={initialLoading || filteredLocated.length === 0}
                    title="Показать все заявки с координатами"
                  >
                    <Maximize2 size={14} aria-hidden="true" />
                    <span>Весь охват</span>
                  </button>

                  <button
                    type="button"
                    className={`map-tool${geo.status === 'granted' ? ' is-active' : ''}`}
                    onClick={handleLocate}
                    disabled={initialLoading || locatedPosts.length === 0 || isLocating}
                    title="Определить ваше местоположение в браузере"
                  >
                    {isLocating ? (
                      <LoaderCircle className="map-spin" size={14} aria-hidden="true" />
                    ) : (
                      <LocateFixed size={14} aria-hidden="true" />
                    )}
                    <span>
                      {isLocating
                        ? 'Определяем…'
                        : geo.status === 'granted'
                        ? 'Центрировать на мне'
                        : 'Рядом со мной'}
                    </span>
                  </button>
                </div>
              </div>

              {initialLoading ? (
                <div className="map-loading-state">
                  <span className="map-spinner" aria-hidden="true" />
                  <p>Загружаем карту…</p>
                  <span>Получаем открытые заявки с координатами.</span>
                </div>
              ) : locatedPosts.length === 0 ? (
                <div className="map-state map-state--inline">
                  <span className="map-state-icon">
                    <MapPinOff size={24} aria-hidden="true" />
                  </span>
                  <h2>Заявки без координат</h2>
                  <p>
                    У открытых заявок пока не заполнены широта и долгота, поэтому
                    нанести их на карту нельзя. Они доступны в списке рядом.
                  </p>
                </div>
              ) : (
                <div
                  className="map-canvas"
                  role="region"
                  aria-label="Карта открытых заявок"
                >
                  <MapContainer
                    ref={handleMapRef}
                    center={[locatedPosts[0].latitude, locatedPosts[0].longitude]}
                    zoom={DEFAULT_ZOOM}
                    minZoom={4}
                    zoomControl={false}
                    className="map-leaflet"
                    whenReady={() => setMapReady(true)}
                  >
                    <TileLayer
                      key={`${tileMode}-${tileReload}`}
                      url={tileProvider.url}
                      attribution={tileProvider.attribution}
                      subdomains={tileProvider.subdomains}
                      maxZoom={tileProvider.maxZoom}
                      eventHandlers={{
                        // Leaflet fires "load" even when every tile failed,
                        // so the error flag is cleared only by an explicit retry.
                        load: () => setTilesLoading(false),
                        tileerror: handleTileError,
                      }}
                    />

                    <ZoomControl position="topright" />
                    <ScaleControl position="bottomright" imperial={false} />

                    <MarkerClusterGroup
                      ref={clusterRef}
                      chunkedLoading
                      maxClusterRadius={54}
                      showCoverageOnHover={false}
                      spiderfyOnMaxZoom
                      iconCreateFunction={createClusterIcon}
                    >
                      {filteredLocated.map((post) => (
                        <Marker
                          key={post.id}
                          position={[post.latitude, post.longitude]}
                          icon={createPostIcon(post, selectedPost?.id === post.id)}
                          title={post.title}
                          alt={`Заявка: ${post.title}`}
                          zIndexOffset={selectedPost?.id === post.id ? 500 : 0}
                          ref={(marker) => {
                            if (marker) markerRefs.current.set(post.id, marker);
                            else markerRefs.current.delete(post.id);
                          }}
                          eventHandlers={{
                            click: () => handleSelectFromMarker(post),
                          }}
                        />
                      ))}
                    </MarkerClusterGroup>

                    {geo.status === 'granted' && (
                      <>
                        {geo.accuracy > 0 && (
                          <Circle
                            center={geo.position}
                            radius={geo.accuracy}
                            interactive={false}
                            pathOptions={{
                              color: '#C8F56A',
                              weight: 1,
                              opacity: 0.45,
                              fillColor: '#C8F56A',
                              fillOpacity: 0.06,
                            }}
                          />
                        )}
                        <Marker
                          position={geo.position}
                          icon={USER_ICON}
                          interactive={false}
                          zIndexOffset={1200}
                          title="Ваше местоположение"
                        >
                          <Tooltip direction="top" offset={[0, -12]} permanent>
                            Вы здесь
                          </Tooltip>
                        </Marker>
                      </>
                    )}
                  </MapContainer>

                  {tilesLoading && (
                    <div className="map-tile-loading" aria-hidden="true">
                      <span className="map-spinner" />
                      <span>Загружаем подложку…</span>
                    </div>
                  )}

                  {tileError && (
                    <div className="map-tile-error" role="status">
                      <div>
                        <strong>Подложка карты не загрузилась</strong>
                        <span>Маркеры заявок остаются доступны.</span>
                      </div>
                      <button type="button" onClick={retryTiles}>
                        {tileMode === 'dark' ? 'Светлая подложка' : 'Повторить'}
                      </button>
                    </div>
                  )}

                  {filteredPosts.length === 0 ? (
                    <div className="map-overlay-state">
                      <div className="map-overlay-card">
                        <SearchX size={24} aria-hidden="true" />
                        <h3>По вашим фильтрам ничего не нашлось</h3>
                        <p>
                          Измените запрос или сбросьте фильтры — карта покажет
                          открытые заявки с координатами.
                        </p>
                        <button
                          type="button"
                          className="map-btn map-btn--primary"
                          onClick={resetFilters}
                        >
                          <RotateCcw size={14} aria-hidden="true" />
                          Сбросить фильтры
                        </button>
                      </div>
                    </div>
                  ) : filteredLocated.length === 0 ? (
                    <div className="map-overlay-state">
                      <div className="map-overlay-card">
                        <MapPinOff size={24} aria-hidden="true" />
                        <h3>У найденных заявок нет координат</h3>
                        <p>
                          Они доступны в списке, но показать их на карте нельзя:
                          backend не вернул широту и долготу.
                        </p>
                        <button
                          type="button"
                          className="map-btn map-btn--ghost"
                          onClick={resetFilters}
                        >
                          <RotateCcw size={14} aria-hidden="true" />
                          Сбросить фильтры
                        </button>
                      </div>
                    </div>
                  ) : null}

                  {selectedPost && hasCoordinates(selectedPost) && (
                    <div className="map-spotlight">
                      <MapPostCard
                        post={selectedPost}
                        variant="spotlight"
                        onClose={() => setSelectedId(null)}
                      />
                    </div>
                  )}
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
};

export default MapPage;
