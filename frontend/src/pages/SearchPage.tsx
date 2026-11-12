import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ClipboardList,
  HandHeart,
  RefreshCw,
  Search,
  Star,
  UserRound,
} from 'lucide-react';
import { searchApi } from '../api/searchApi';
import { Card } from '../components/Card';
import { Spinner } from '../components/Spinner';
import { EmptyState } from '../components/EmptyState';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';
import type { SearchResult, SearchType } from '../types';

const PAGE_SIZE = 20;

const TYPE_LABELS: Record<Exclude<SearchType, 'ALL'>, string> = {
  POST: 'Заявка',
  USER: 'Пользователь',
  REVIEW: 'Отзыв',
};

const TYPE_ICONS: Record<Exclude<SearchType, 'ALL'>, typeof ClipboardList> = {
  POST: ClipboardList,
  USER: UserRound,
  REVIEW: Star,
};

const TYPE_OPTIONS: Array<{ value: SearchType; label: string }> = [
  { value: 'ALL', label: 'Всё' },
  { value: 'POST', label: 'Заявки' },
  { value: 'USER', label: 'Пользователи' },
  { value: 'REVIEW', label: 'Отзывы' },
];

const SORT_OPTIONS: Array<{ value: 'newest' | 'oldest'; label: string }> = [
  { value: 'newest', label: 'Сначала новые' },
  { value: 'oldest', label: 'Сначала старые' },
];

const resultLink = (result: SearchResult): string => {
  if (result.link) return result.link;
  if (result.type === 'POST') return `/posts/${result.id}`;
  if (result.type === 'REVIEW') return `/profile/${result.ownerId ?? result.id}`;
  return `/profile/${result.id}`;
};

export const SearchPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q')?.trim() ?? '';
  const rawType = searchParams.get('type');
  const type: SearchType =
    rawType === 'POST' || rawType === 'USER' || rawType === 'REVIEW' ? rawType : 'ALL';
  const sort = searchParams.get('sort') === 'oldest' ? 'oldest' : 'newest';

  const [input, setInput] = useState(query);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const requestRef = useRef(0);

  useEffect(() => {
    setInput(query);
  }, [query]);

  useEffect(() => {
    if (!query) {
      setResults([]);
      setTotal(0);
      setPage(0);
      setHasNext(false);
      setLoading(false);
      setError(false);
      return;
    }

    const requestId = ++requestRef.current;
    const controller = new AbortController();
    setLoading(true);
    setError(false);

    searchApi
      .search({ q: query, type, sort, page: 0, size: PAGE_SIZE }, controller.signal)
      .then((data) => {
        if (requestRef.current !== requestId) return;
        setResults(data.content);
        setTotal(data.totalElements);
        setPage(data.number);
        setHasNext(data.hasNext);
      })
      .catch(() => {
        if (requestRef.current !== requestId || controller.signal.aborted) return;
        setResults([]);
        setTotal(0);
        setHasNext(false);
        setError(true);
      })
      .finally(() => {
        if (requestRef.current === requestId) setLoading(false);
      });

    return () => controller.abort();
  }, [query, type, sort, reloadTick]);

  const updateQuery = (next: Record<string, string | undefined>) => {
    const params = new URLSearchParams(searchParams);
    Object.entries(next).forEach(([key, value]) => {
      if (value) params.set(key, value);
      else params.delete(key);
    });
    setSearchParams(params);
  };

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updateQuery({ q: input.trim() || undefined });
  };

  const loadMore = async () => {
    if (loadingMore || !hasNext || !query) return;
    setLoadingMore(true);
    setError(false);
    try {
      const data = await searchApi.search({
        q: query,
        type,
        sort,
        page: page + 1,
        size: PAGE_SIZE,
      });
      setResults((previous) => [...previous, ...data.content]);
      setTotal(data.totalElements);
      setPage(data.number);
      setHasNext(data.hasNext);
    } catch {
      setError(true);
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <main style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>

      <Card style={styles.mainCard}>
        <header style={styles.header}>
          <div>
            <h1 style={styles.title}>Поиск</h1>
            <p style={styles.subtitle}>Заявки, публичные профили и отзывы в одном месте.</p>
          </div>
          {total > 0 && <span style={styles.count}>{total.toLocaleString('ru-RU')} совпадений</span>}
        </header>

        <form style={styles.searchForm} role="search" onSubmit={submit}>
          <div style={styles.searchField}>
            <Search size={18} aria-hidden="true" style={styles.searchIcon} />
            <input
              type="search"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Что найти?"
              aria-label="Поисковый запрос"
              style={styles.searchInput}
              autoComplete="off"
            />
          </div>
          <button type="submit" style={styles.searchButton}>Найти</button>
        </form>

        <div style={styles.filters}>
          <div style={styles.filterGroup} role="group" aria-label="Тип результата">
            {TYPE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => updateQuery({ type: option.value === 'ALL' ? undefined : option.value })}
                style={{
                  ...styles.filterButton,
                  ...(type === option.value ? styles.filterButtonActive : null),
                }}
              >
                {option.label}
              </button>
            ))}
          </div>
          <select
            value={sort}
            onChange={(event) => updateQuery({ sort: event.target.value === 'newest' ? undefined : event.target.value })}
            aria-label="Порядок результатов"
            style={styles.sortSelect}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>

        {!query ? (
          <EmptyState
            variant="search"
            title="Введите запрос"
            description="Поиск работает по названиям и описаниям заявок, именам и городам пользователей, а также по текстам отзывов."
          />
        ) : loading ? (
          <Spinner message="Ищем…" />
        ) : error ? (
          <div style={styles.stateBox} role="alert">
            <p style={styles.stateText}>Не удалось выполнить поиск.</p>
            <button
              type="button"
              style={styles.retryButton}
              onClick={() => setReloadTick((tick) => tick + 1)}
            >
              <RefreshCw size={15} aria-hidden="true" /> Повторить
            </button>
          </div>
        ) : results.length === 0 ? (
          <EmptyState
            variant="search"
            title="Ничего не найдено"
            description={`По запросу «${query}» нет результатов. Попробуйте изменить формулировку или фильтры.`}
          />
        ) : (
          <div style={styles.list}>
            {results.map((result) => {
              const Icon = TYPE_ICONS[result.type] ?? HandHeart;
              return (
                <Link
                  key={`${result.type}-${result.id}`}
                  to={resultLink(result)}
                  style={styles.resultCard}
                >
                  <span style={styles.resultIcon} aria-hidden="true">
                    <Icon size={19} />
                  </span>
                  <span style={styles.resultBody}>
                    <span style={styles.resultMeta}>
                      <span style={styles.resultType}>{TYPE_LABELS[result.type] ?? result.type}</span>
                      {result.category && <span>{result.category}</span>}
                      {result.city && <span>{result.city}</span>}
                      {typeof result.rating === 'number' && (
                        <span style={styles.rating}>
                          <Star size={12} aria-hidden="true" fill="currentColor" />
                          {result.rating.toFixed(1)}
                        </span>
                      )}
                      <span>{getRelativeTime(result.createdAt)}</span>
                    </span>
                    <span style={styles.resultTitle}>{result.title}</span>
                    {result.snippet && <span style={styles.resultSnippet}>{result.snippet}</span>}
                  </span>
                </Link>
              );
            })}

            {hasNext && (
              <div style={styles.loadMoreWrap}>
                <button
                  type="button"
                  style={styles.retryButton}
                  onClick={() => void loadMore()}
                  disabled={loadingMore}
                >
                  {loadingMore ? 'Загружаем…' : 'Показать ещё'}
                </button>
                <span style={styles.count}>
                  Показано {results.length} из {total.toLocaleString('ru-RU')}
                </span>
              </div>
            )}
          </div>
        )}
      </Card>
    </main>
  );
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
    padding: 0,
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '16px',
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
  subtitle: {
    margin: '6px 0 0',
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  count: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    whiteSpace: 'nowrap',
  },
  searchForm: {
    display: 'flex',
    gap: '10px',
    padding: '20px 32px 12px',
  },
  searchField: {
    position: 'relative',
    flex: 1,
  },
  searchIcon: {
    position: 'absolute',
    left: '13px',
    top: '50%',
    transform: 'translateY(-50%)',
    color: theme.colors.textMuted,
    pointerEvents: 'none',
  },
  searchInput: {
    width: '100%',
    minHeight: '46px',
    padding: '0 14px 0 40px',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    background: 'rgba(255,255,255,0.04)',
    color: theme.colors.text,
    fontSize: '15px',
    outline: 'none',
  },
  searchButton: {
    minHeight: '46px',
    padding: '0 22px',
    borderRadius: theme.borderRadius.md,
    border: '1px solid rgba(200, 245, 106, 0.35)',
    background: 'rgba(200, 245, 106, 0.12)',
    color: theme.colors.accentLight,
    fontSize: '14px',
    fontWeight: 700,
    cursor: 'pointer',
  },
  filters: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: '10px',
    padding: '0 32px 18px',
  },
  filterGroup: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '6px',
  },
  filterButton: {
    padding: '7px 13px',
    borderRadius: '999px',
    border: `1px solid ${theme.colors.border}`,
    background: 'transparent',
    color: theme.colors.textMuted,
    fontSize: '12px',
    fontWeight: 650,
    cursor: 'pointer',
  },
  filterButtonActive: {
    borderColor: 'rgba(200, 245, 106, 0.45)',
    background: 'rgba(200, 245, 106, 0.12)',
    color: theme.colors.accentLight,
  },
  sortSelect: {
    minHeight: '34px',
    padding: '0 10px',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    background: 'rgba(255,255,255,0.04)',
    color: theme.colors.textSecondary,
    fontSize: '12px',
  },
  list: {
    display: 'grid',
    gap: '10px',
    padding: '0 32px 28px',
  },
  resultCard: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '14px',
    padding: '16px 18px',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    background: 'rgba(255,255,255,0.03)',
    textDecoration: 'none',
    transition: 'border-color 0.18s ease, background-color 0.18s ease',
  },
  resultIcon: {
    display: 'grid',
    flex: '0 0 auto',
    placeItems: 'center',
    width: '38px',
    height: '38px',
    borderRadius: '11px',
    background: 'rgba(200, 245, 106, 0.08)',
    color: theme.colors.accentLight,
  },
  resultBody: {
    display: 'grid',
    minWidth: 0,
    gap: '4px',
  },
  resultMeta: {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '8px',
    color: theme.colors.textMuted,
    fontSize: '11px',
  },
  resultType: {
    color: theme.colors.accentLight,
    fontWeight: 700,
    textTransform: 'uppercase',
    letterSpacing: '0.04em',
  },
  rating: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '3px',
    color: theme.colors.accent,
  },
  resultTitle: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 650,
    overflowWrap: 'anywhere',
  },
  resultSnippet: {
    color: theme.colors.textSecondary,
    fontSize: '12px',
    lineHeight: 1.55,
    overflowWrap: 'anywhere',
  },
  stateBox: {
    display: 'grid',
    justifyItems: 'center',
    gap: '10px',
    padding: '28px 32px 36px',
  },
  stateText: {
    margin: 0,
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  retryButton: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '7px',
    padding: '9px 18px',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    background: 'rgba(255,255,255,0.05)',
    color: theme.colors.text,
    fontSize: '13px',
    cursor: 'pointer',
  },
  loadMoreWrap: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '8px',
    paddingTop: '8px',
  },
};
