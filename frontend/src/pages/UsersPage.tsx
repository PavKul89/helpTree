import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Star, HandHeart, ClipboardList, RefreshCw } from 'lucide-react';
import { authApi } from '../api/authApi';
import { Card } from '../components/Card';
import { Spinner } from '../components/Spinner';
import { EmptyState } from '../components/EmptyState';
import { Avatar } from '../components/Avatar';
import { theme } from '../theme';
import type { UserPublic } from '../types';

const PAGE_SIZE = 24;

export const UsersPage = () => {
  const [users, setUsers] = useState<UserPublic[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    authApi
      .getAllUsers({ page: 0, size: PAGE_SIZE, sort: 'rating', direction: 'desc' })
      .then((result) => {
        if (cancelled) return;
        setUsers(result.content);
        setTotal(result.totalElements);
        setPage(result.number);
        setHasNext(result.hasNext);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadMore = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    setError(false);
    try {
      const result = await authApi.getAllUsers({
        page: page + 1,
        size: PAGE_SIZE,
        sort: 'rating',
        direction: 'desc',
      });
      setUsers((previous) => [...previous, ...result.content]);
      setTotal(result.totalElements);
      setPage(result.number);
      setHasNext(result.hasNext);
    } catch {
      setError(true);
    } finally {
      setLoadingMore(false);
    }
  };

  if (loading) return <Spinner message="Загрузка пользователей..." />;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        <div style={styles.header}>
          <h1 className="page-title" style={styles.title}>Пользователи</h1>
          <span style={styles.count}>{total.toLocaleString('ru-RU')} чел.</span>
        </div>

        {error && users.length === 0 ? (
          <div style={styles.loadMoreWrap}>
            <p style={styles.count}>Не удалось загрузить пользователей.</p>
            <button type="button" style={styles.loadMore} onClick={() => window.location.reload()}>
              <RefreshCw size={15} aria-hidden="true" /> Повторить
            </button>
          </div>
        ) : users.length === 0 ? (
          <EmptyState 
            variant="users"
            title="Пользователей пока нет" 
            description="Зарегистрируйтесь, чтобы стать первым пользователем!"
          />
        ) : (
          <div style={styles.grid}>
            {users.map((user) => (
              <Link key={user.id} to={`/profile/${user.id}`} style={styles.userCard}>
                <Avatar name={user.name} avatarUrl={user.avatarUrl} size="large" />
                <div style={styles.userInfo}>
                  <div style={styles.userName}>{user.name}</div>
                  <div style={styles.userStats}>
                    <span style={styles.statItem}>
                      <Star size={14} color={theme.colors.accent} fill={theme.colors.accent} />
                      <span style={styles.statValue}>{user.rating.toFixed(1)}</span>
                    </span>
                    <span style={styles.statDivider}>•</span>
                    <span style={styles.statItem}>
                      <HandHeart size={14} color={theme.colors.accent} />
                      <span style={styles.statValue}>{user.helpedCount}</span>
                    </span>
                    <span style={styles.statDivider}>•</span>
                    <span style={styles.statItem}>
                      <ClipboardList size={14} color={theme.colors.accent} />
                      <span style={styles.statValue}>{user.debtCount}</span>
                    </span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}

        {users.length > 0 && hasNext && (
          <div style={styles.loadMoreWrap}>
            <button
              type="button"
              style={styles.loadMore}
              onClick={() => void loadMore()}
              disabled={loadingMore}
            >
              {loadingMore ? 'Загружаем…' : 'Показать ещё'}
            </button>
            <span style={styles.count}>
              Показано {users.length} из {total.toLocaleString('ru-RU')}
            </span>
          </div>
        )}
      </Card>
    </div>
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
    padding: '0',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
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
  count: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  grid: {
    padding: '24px 32px',
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
    gap: '16px',
  },
  userCard: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    padding: '20px',
    background: 'rgba(255,255,255,0.04)',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    textDecoration: 'none',
    transition: 'all 0.2s ease',
  },
  userInfo: {
    flex: 1,
    minWidth: 0,
  },
  userName: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '6px',
  },
  userStats: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    flexWrap: 'wrap',
  },
  statItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
  },
  statIcon: {
    fontSize: '12px',
  },
  statValue: {
    color: theme.colors.textSecondary,
    fontSize: '13px',
  },
  statDivider: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  loadMoreWrap: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '8px',
    padding: '0 32px 28px',
  },
  loadMore: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '8px',
    padding: '10px 22px',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    background: 'rgba(255,255,255,0.05)',
    color: theme.colors.text,
    cursor: 'pointer',
    fontSize: '14px',
  },
};