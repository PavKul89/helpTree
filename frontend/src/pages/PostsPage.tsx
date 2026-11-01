import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleSlash2,
  LayoutGrid,
  List,
  LoaderCircle,
  MapPin,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  SearchX,
  SlidersHorizontal,
  TreeDeciduous,
  User,
  X,
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { authApi } from '../api/authApi';
import type { Post } from '../types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { CatalogFilters, POST_STATUSES } from '../components/CatalogFilters';
import { PostCatalogCard } from '../components/PostCatalogCard';
import { CATEGORIES } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';
import './PostsPage.css';

const PAGE_SIZE = 12;

type ViewMode = 'grid' | 'list';
type SortMode = 'newest' | 'oldest';

const SORT_API: Record<SortMode, string> = {
  newest: 'createdAt,desc',
  oldest: 'createdAt,asc',
};

const isPostStatus = (value: string): value is Post['status'] =>
  POST_STATUSES.includes(value as Post['status']);

const getPageItems = (current: number, pages: number): Array<number | 'gap'> => {
  if (pages <= 7) return Array.from({ length: pages }, (_, index) => index);

  const items: Array<number | 'gap'> = [0];
  const start = Math.max(1, current - 1);
  const end = Math.min(pages - 2, current + 1);

  if (start > 1) items.push('gap');
  for (let index = start; index <= end; index += 1) items.push(index);
  if (end < pages - 2) items.push('gap');
  items.push(pages - 1);

  return items;
};

interface MobileFilterDraft {
  category: string;
  status: string;
  city: string;
  author: string;
}

const EMPTY_DRAFT: MobileFilterDraft = { category: '', status: '', city: '', author: '' };

export const PostsPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const q = searchParams.get('q') ?? '';
  const rawCategory = searchParams.get('category') ?? '';
  const category = CATEGORIES.includes(rawCategory) ? rawCategory : '';
  const rawStatus = searchParams.get('status') ?? '';
  const status: Post['status'] | '' = isPostStatus(rawStatus) ? rawStatus : '';
  const city = searchParams.get('city') ?? '';
  const author = searchParams.get('author') ?? '';
  const sort: SortMode = searchParams.get('sort') === 'oldest' ? 'oldest' : 'newest';
  const view: ViewMode = searchParams.get('view') === 'list' ? 'list' : 'grid';
  const rawPage = Number.parseInt(searchParams.get('page') ?? '0', 10);
  const page = Number.isNaN(rawPage) || rawPage < 0 ? 0 : rawPage;

  const [posts, setPosts] = useState<Post[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  // Page number the currently stored response belongs to. Used to avoid
  // clamping the URL before the very first response arrives (totalPages is 0
  // while loading, which would otherwise drop a deep link like ?page=2).
  const [loadedPage, setLoadedPage] = useState<number | null>(null);

  const [favorites, setFavorites] = useState<number[]>([]);

  const [searchInput, setSearchInput] = useState(q);
  const [cityDraft, setCityDraft] = useState(city);
  const [authorDraft, setAuthorDraft] = useState(author);

  const [sheetOpen, setSheetOpen] = useState(false);
  const [draft, setDraft] = useState<MobileFilterDraft>(EMPTY_DRAFT);

  const searchInputRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);
  const requestRef = useRef(0);
  const hasLoadedRef = useRef(false);

  const applyParams = useCallback(
    (
      updates: Record<string, string | null>,
      options?: { replace?: boolean; resetPage?: boolean }
    ) => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          Object.entries(updates).forEach(([key, value]) => {
            if (value === null || value === '') next.delete(key);
            else next.set(key, value);
          });
          if (options?.resetPage !== false) next.delete('page');
          return next;
        },
        { replace: options?.replace ?? false }
      );
    },
    [setSearchParams]
  );

  // Keep the visible inputs in sync with the URL (browser back/forward,
  // shared links, chips removal).
  useEffect(() => {
    setSearchInput(q);
  }, [q]);

  useEffect(() => {
    setCityDraft(city);
  }, [city]);

  useEffect(() => {
    setAuthorDraft(author);
  }, [author]);

  // Debounced server-side search. The URL is the single source of truth,
  // so the same request path is used for typing, Enter and clearing.
  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === q) return undefined;

    const timer = window.setTimeout(() => {
      applyParams({ q: trimmed || null }, { replace: true });
    }, 420);

    return () => window.clearTimeout(timer);
  }, [searchInput, q, applyParams]);

  useEffect(() => {
    const controller = new AbortController();
    const sequence = ++requestRef.current;

    if (hasLoadedRef.current) setRefreshing(true);
    else setLoading(true);
    setError(false);

    const params: {
      page: number;
      size: number;
      sort: string;
      title?: string;
      category?: string;
      status?: string;
      city?: string;
      authorName?: string;
    } = { page, size: PAGE_SIZE, sort: SORT_API[sort] };

    if (q) params.title = q;
    if (category) params.category = category;
    if (status) params.status = status;
    if (city) params.city = city;
    if (author) params.authorName = author;

    postsApi
      .getAll(params, controller.signal)
      .then((data) => {
        if (sequence !== requestRef.current) return;
        setPosts(data.content);
        setTotal(data.totalElements);
        setTotalPages(data.totalPages);
        setLoadedPage(page);
        hasLoadedRef.current = true;
        setLoading(false);
        setRefreshing(false);
      })
      .catch((requestError) => {
        if (controller.signal.aborted || sequence !== requestRef.current) return;
        console.error('Failed to load posts', requestError);
        setError(true);
        setLoading(false);
        setRefreshing(false);
      });

    return () => controller.abort();
  }, [q, category, status, city, author, sort, page, reloadTick]);

  // If the URL points past the last page (e.g. after a filter narrowed the
  // results), move to the closest valid page instead of showing an empty grid.
  // Only act on data that actually belongs to the page in the URL.
  useEffect(() => {
    if (loadedPage !== page) return;

    if (totalPages > 0 && page >= totalPages) {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          const lastPage = totalPages - 1;
          if (lastPage <= 0) next.delete('page');
          else next.set('page', String(lastPage));
          return next;
        },
        { replace: true }
      );
    } else if (totalPages === 0 && page > 0) {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          next.delete('page');
          return next;
        },
        { replace: true }
      );
    }
  }, [page, totalPages, loadedPage, setSearchParams]);

  useEffect(() => {
    if (!user) {
      setFavorites([]);
      return undefined;
    }

    let cancelled = false;
    authApi
      .getFavorites(user.id)
      .then((ids) => {
        if (!cancelled) setFavorites(ids);
      })
      .catch((favoriteError) => {
        // Favorites are an enhancement — the catalogue must still work.
        console.error('Failed to load favorites', favoriteError);
      });

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!sheetOpen) return undefined;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSheetOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [sheetOpen]);

  const hasActiveFilters = Boolean(q || category || status || city || author);
  const activeCount = [q, category, status, city, author].filter(Boolean).length;
  const isBlocked = Boolean(user?.blockedAt);

  const submitSearch = () => {
    const trimmed = searchInput.trim();
    applyParams({ q: trimmed || null });
  };

  const clearSearch = () => {
    setSearchInput('');
    applyParams({ q: null }, { replace: true });
    searchInputRef.current?.focus();
  };

  const handleCategoryChange = (value: string) => {
    if (value === category) return;
    applyParams({ category: value || null });
  };

  const handleStatusChange = (value: string) => {
    if (value === status) return;
    applyParams({ status: value || null });
  };

  const applyTextFilters = () => {
    const nextCity = cityDraft.trim();
    const nextAuthor = authorDraft.trim();
    if (nextCity === city && nextAuthor === author) return;
    applyParams({ city: nextCity || null, author: nextAuthor || null });
  };

  const handleUseUserCity = () => {
    if (!user?.city) return;
    setCityDraft(user.city);
    applyParams({ city: user.city });
  };

  const resetAllFilters = useCallback(() => {
    setSearchInput('');
    setCityDraft('');
    setAuthorDraft('');
    setDraft(EMPTY_DRAFT);
    applyParams({ q: null, category: null, status: null, city: null, author: null });
  }, [applyParams]);

  const toggleFavorite = useCallback(
    async (post: Post) => {
      if (!user) {
        navigate('/login');
        return;
      }
      if (post.status !== 'OPEN' || post.userId === user.id) return;

      const wasFavorite = favorites.includes(post.id);
      setFavorites((current) =>
        wasFavorite ? current.filter((id) => id !== post.id) : [...current, post.id]
      );

      try {
        if (wasFavorite) await authApi.removeFavorite(user.id, post.id);
        else await authApi.addFavorite(user.id, post.id);
      } catch (favoriteError) {
        console.error('Failed to update favorite', favoriteError);
        setFavorites((current) =>
          wasFavorite ? [...current, post.id] : current.filter((id) => id !== post.id)
        );
        showToast('Не удалось обновить избранное', 'error');
      }
    },
    [user, favorites, navigate, showToast]
  );

  const goToPage = (nextPage: number) => {
    if (nextPage < 0 || nextPage >= totalPages || nextPage === page) return;
    applyParams(
      { page: nextPage === 0 ? null : String(nextPage) },
      { resetPage: false }
    );
    const reduceMotion =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    resultsRef.current?.scrollIntoView({
      behavior: reduceMotion ? 'auto' : 'smooth',
      block: 'start',
    });
  };

  const handleSortChange = (value: string) => {
    if (value === sort) return;
    applyParams({ sort: value === 'oldest' ? 'oldest' : null });
  };

  const handleViewChange = (nextView: ViewMode) => {
    if (nextView === view) return;
    applyParams(
      { view: nextView === 'grid' ? null : nextView },
      { resetPage: false, replace: true }
    );
  };

  const openSheet = () => {
    setDraft({ category, status, city: cityDraft, author: authorDraft });
    setSheetOpen(true);
  };

  const applySheet = () => {
    applyParams({
      category: draft.category || null,
      status: draft.status || null,
      city: draft.city.trim() || null,
      author: draft.author.trim() || null,
    });
    setSheetOpen(false);
  };

  const activeChips = useMemo(() => {
    const chips: Array<{
      key: string;
      label: string;
      icon?: React.ReactNode;
      onRemove: () => void;
    }> = [];

    if (q) {
      chips.push({
        key: 'q',
        label: `Поиск: ${q}`,
        icon: <Search size={12} aria-hidden="true" />,
        onRemove: () => {
          setSearchInput('');
          applyParams({ q: null }, { replace: true });
        },
      });
    }
    if (category) {
      chips.push({
        key: 'category',
        label: category,
        onRemove: () => applyParams({ category: null }),
      });
    }
    if (status) {
      chips.push({
        key: 'status',
        label: getStatusLabel(status),
        onRemove: () => applyParams({ status: null }),
      });
    }
    if (city) {
      chips.push({
        key: 'city',
        label: city,
        icon: <MapPin size={12} aria-hidden="true" />,
        onRemove: () => {
          setCityDraft('');
          applyParams({ city: null });
        },
      });
    }
    if (author) {
      chips.push({
        key: 'author',
        label: `Автор: ${author}`,
        icon: <User size={12} aria-hidden="true" />,
        onRemove: () => {
          setAuthorDraft('');
          applyParams({ author: null });
        },
      });
    }

    return chips;
  }, [q, category, status, city, author, applyParams]);

  const pageItems = useMemo(() => getPageItems(page, totalPages), [page, totalPages]);

  const showSkeleton = !error && posts.length === 0 && (loading || refreshing);
  const showEmpty = !loading && !refreshing && !error && posts.length === 0;
  const showPagination = !error && !loading && !refreshing && totalPages > 1;

  return (
    <div className="catalog-page">
      <header className="catalog-hero">
        <div className="catalog-hero-text">
          <h1>Найдите возможность помочь</h1>
          <p>Здесь люди делятся своими потребностями и находят поддержку сообщества.</p>
        </div>

        {isBlocked ? (
          <button
            type="button"
            className="catalog-create is-disabled"
            aria-disabled="true"
            title="Создание заявок недоступно: аккаунт заблокирован"
            onClick={() =>
              showToast(
                'Ваш аккаунт заблокирован за долг. Помогите другим пользователям, чтобы разблокировать аккаунт.',
                'error'
              )
            }
          >
            <Plus size={17} aria-hidden="true" />
            Создать заявку
          </button>
        ) : (
          <Link to="/posts/new" className="catalog-create">
            <Plus size={17} aria-hidden="true" />
            Создать заявку
          </Link>
        )}
      </header>

      <form
        className="catalog-search"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          submitSearch();
        }}
      >
        <div className="catalog-search-box">
          <Search className="catalog-search-icon" size={20} aria-hidden="true" />
          <input
            ref={searchInputRef}
            type="search"
            value={searchInput}
            placeholder="Поиск по названию или описанию заявки..."
            aria-label="Поиск по названию или описанию заявки"
            autoComplete="off"
            onChange={(event) => setSearchInput(event.target.value)}
          />
          {searchInput && (
            <button
              type="button"
              className="catalog-search-clear"
              aria-label="Очистить поиск"
              onClick={clearSearch}
            >
              <X size={16} aria-hidden="true" />
            </button>
          )}
        </div>
        <button type="submit" className="catalog-search-submit">
          <Search size={17} aria-hidden="true" />
          <span>Найти</span>
        </button>
      </form>

      <div className="catalog-filters-panel">
        <CatalogFilters
          idPrefix="catalog-desktop"
          category={category}
          status={status}
          city={cityDraft}
          author={authorDraft}
          userCity={user?.city}
          onCategoryChange={handleCategoryChange}
          onStatusChange={handleStatusChange}
          onCityChange={setCityDraft}
          onAuthorChange={setAuthorDraft}
          onUseUserCity={handleUseUserCity}
          onSubmit={applyTextFilters}
        />
        <div className="catalog-filter-actions">
          <button type="button" className="catalog-btn catalog-btn--primary" onClick={applyTextFilters}>
            <Search size={15} aria-hidden="true" />
            Применить
          </button>
          <button
            type="button"
            className="catalog-btn catalog-btn--ghost"
            onClick={resetAllFilters}
            disabled={!hasActiveFilters}
          >
            <RotateCcw size={15} aria-hidden="true" />
            Сбросить
          </button>
        </div>
      </div>

      <button type="button" className="catalog-mobile-filters" onClick={openSheet}>
        <SlidersHorizontal size={17} aria-hidden="true" />
        Фильтры
        {activeCount > 0 && <span className="catalog-filter-count">{activeCount}</span>}
      </button>

      {activeChips.length > 0 && (
        <div className="catalog-active">
          {activeChips.map((chip) => (
            <span className="catalog-chip" key={chip.key}>
              {chip.icon}
              <span className="catalog-chip-label">{chip.label}</span>
              <button
                type="button"
                aria-label={`Убрать фильтр: ${chip.label}`}
                onClick={chip.onRemove}
              >
                <X size={12} aria-hidden="true" />
              </button>
            </span>
          ))}
          <button type="button" className="catalog-chip-clear-all" onClick={resetAllFilters}>
            Сбросить все
          </button>
        </div>
      )}

      <div className="catalog-results-bar" ref={resultsRef}>
        <div className="catalog-results-count" aria-live="polite">
          {loading ? (
            <span>Загружаем заявки…</span>
          ) : error && posts.length === 0 ? (
            <span>Не удалось загрузить заявки</span>
          ) : (
            <span>
              Найдено заявок: <strong>{total}</strong>
              {totalPages > 1 && (
                <span className="catalog-page-info">
                  {' '}
                  · страница {page + 1} из {totalPages}
                </span>
              )}
            </span>
          )}
          {refreshing && <LoaderCircle className="catalog-spin" size={14} aria-label="Обновление" />}
        </div>

        <div className="catalog-results-tools">
          <div className="catalog-sort">
            <select
              aria-label="Сортировка заявок"
              value={sort}
              onChange={(event) => handleSortChange(event.target.value)}
            >
              <option value="newest">Сначала новые</option>
              <option value="oldest">Сначала старые</option>
            </select>
            <ChevronDown size={15} aria-hidden="true" />
          </div>

          <div className="catalog-view-toggle" role="group" aria-label="Вид отображения">
            <button
              type="button"
              className={view === 'grid' ? 'is-active' : ''}
              aria-pressed={view === 'grid'}
              aria-label="Показать сеткой"
              title="Сетка"
              onClick={() => handleViewChange('grid')}
            >
              <LayoutGrid size={16} aria-hidden="true" />
            </button>
            <button
              type="button"
              className={view === 'list' ? 'is-active' : ''}
              aria-pressed={view === 'list'}
              aria-label="Показать списком"
              title="Список"
              onClick={() => handleViewChange('list')}
            >
              <List size={16} aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      {error && posts.length > 0 && (
        <div className="catalog-error-banner" role="alert">
          <RefreshCw size={16} aria-hidden="true" />
          <span>Не удалось обновить результаты.</span>
          <button type="button" onClick={() => setReloadTick((tick) => tick + 1)}>
            Повторить
          </button>
        </div>
      )}

      {showSkeleton && (
        <div className={`catalog-grid${view === 'list' ? ' catalog-grid--list' : ''}`} aria-label="Загрузка заявок">
          {Array.from({ length: 6 }, (_, index) => (
            <div
              className={`catalog-card catalog-card--skeleton${
                view === 'list' ? ' catalog-card--list' : ''
              }`}
              key={index}
              aria-hidden="true"
            >
              <div className="catalog-skeleton-media" />
              <div className="catalog-skeleton-body">
                <span className="catalog-skel catalog-skel--tag" />
                <span className="catalog-skel catalog-skel--title" />
                <span className="catalog-skel catalog-skel--title catalog-skel--short" />
                <span className="catalog-skel catalog-skel--line" />
                <span className="catalog-skel catalog-skel--line catalog-skel--short" />
                <span className="catalog-skel catalog-skel--footer" />
              </div>
            </div>
          ))}
        </div>
      )}

      {posts.length > 0 && (
        <div
          className={`catalog-grid${view === 'list' ? ' catalog-grid--list' : ''}${
            refreshing ? ' catalog-results--refreshing' : ''
          }`}
          aria-busy={refreshing}
        >
          {posts.map((post, index) => (
            <PostCatalogCard
              key={post.id}
              post={post}
              view={view}
              index={index}
              isFavorite={favorites.includes(post.id)}
              canFavorite={post.status === 'OPEN' && (!user || user.id !== post.userId)}
              onToggleFavorite={toggleFavorite}
            />
          ))}
        </div>
      )}

      {showEmpty && (
        <div className="catalog-state">
          <span className="catalog-state-icon">
            {hasActiveFilters ? <SearchX size={27} aria-hidden="true" /> : <TreeDeciduous size={27} aria-hidden="true" />}
          </span>
          <h2>{hasActiveFilters ? 'Ничего не найдено' : 'Пока заявок нет'}</h2>
          <p>
            {hasActiveFilters
              ? 'Попробуйте изменить поисковый запрос или сбросить часть фильтров.'
              : 'Попробуйте изменить фильтры или поискать в другой категории.'}
          </p>
          <div className="catalog-state-actions">
            {hasActiveFilters ? (
              <button type="button" className="catalog-btn catalog-btn--primary" onClick={resetAllFilters}>
                <RotateCcw size={15} aria-hidden="true" />
                Сбросить фильтры
              </button>
            ) : (
              user &&
              !isBlocked && (
                <Link to="/posts/new" className="catalog-btn catalog-btn--primary">
                  <Plus size={15} aria-hidden="true" />
                  Создать заявку
                </Link>
              )
            )}
          </div>
        </div>
      )}

      {error && posts.length === 0 && (
        <div className="catalog-state catalog-state--error">
          <span className="catalog-state-icon">
            <CircleSlash2 size={27} aria-hidden="true" />
          </span>
          <h2>Не удалось загрузить заявки</h2>
          <p>Проверьте соединение и попробуйте ещё раз.</p>
          <div className="catalog-state-actions">
            <button
              type="button"
              className="catalog-btn catalog-btn--primary"
              onClick={() => setReloadTick((tick) => tick + 1)}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Повторить
            </button>
          </div>
        </div>
      )}

      {showPagination && (
        <nav className="catalog-pagination" aria-label="Пагинация заявок">
          <button
            type="button"
            className="catalog-page-btn catalog-page-btn--arrow"
            onClick={() => goToPage(page - 1)}
            disabled={page === 0}
            aria-label="Предыдущая страница"
          >
            <ChevronLeft size={16} aria-hidden="true" />
            <span className="catalog-page-arrow-text">Назад</span>
          </button>

          <div className="catalog-pagination-pages">
            {pageItems.map((item, index) =>
              item === 'gap' ? (
                <span className="catalog-page-ellipsis" key={`gap-${index}`} aria-hidden="true">
                  …
                </span>
              ) : (
                <button
                  type="button"
                  key={item}
                  className={`catalog-page-btn${item === page ? ' is-active' : ''}`}
                  aria-current={item === page ? 'page' : undefined}
                  aria-label={`Страница ${item + 1}`}
                  onClick={() => goToPage(item)}
                >
                  {item + 1}
                </button>
              )
            )}
          </div>

          <span className="catalog-pagination-compact">
            Страница {page + 1} из {totalPages}
          </span>

          <button
            type="button"
            className="catalog-page-btn catalog-page-btn--arrow"
            onClick={() => goToPage(page + 1)}
            disabled={page >= totalPages - 1}
            aria-label="Следующая страница"
          >
            <span className="catalog-page-arrow-text">Вперёд</span>
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </nav>
      )}

      {sheetOpen && (
        <div
          className="catalog-sheet-backdrop"
          role="presentation"
          onClick={() => setSheetOpen(false)}
        >
          <div
            className="catalog-sheet"
            role="dialog"
            aria-modal="true"
            aria-labelledby="catalog-sheet-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="catalog-sheet-grab" aria-hidden="true" />
            <div className="catalog-sheet-head">
              <h2 id="catalog-sheet-title">Фильтры</h2>
              <button
                type="button"
                className="catalog-sheet-close"
                onClick={() => setSheetOpen(false)}
                aria-label="Закрыть фильтры"
                autoFocus
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <div className="catalog-sheet-body">
              <CatalogFilters
                idPrefix="catalog-mobile"
                category={draft.category}
                status={draft.status}
                city={draft.city}
                author={draft.author}
                userCity={user?.city}
                onCategoryChange={(value) => setDraft((current) => ({ ...current, category: value }))}
                onStatusChange={(value) => setDraft((current) => ({ ...current, status: value }))}
                onCityChange={(value) => setDraft((current) => ({ ...current, city: value }))}
                onAuthorChange={(value) => setDraft((current) => ({ ...current, author: value }))}
                onUseUserCity={() =>
                  setDraft((current) => ({ ...current, city: user?.city || '' }))
                }
              />
            </div>

            <div className="catalog-sheet-actions">
              <button
                type="button"
                className="catalog-btn catalog-btn--ghost"
                onClick={() => setDraft(EMPTY_DRAFT)}
                disabled={
                  !draft.category && !draft.status && !draft.city.trim() && !draft.author.trim()
                }
              >
                <RotateCcw size={15} aria-hidden="true" />
                Сбросить
              </button>
              <button type="button" className="catalog-btn catalog-btn--primary" onClick={applySheet}>
                Показать результаты
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
