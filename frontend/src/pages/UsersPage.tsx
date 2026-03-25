import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { Card } from '../components/Card';
import { theme } from '../theme';
import type { UserPublic } from '../types';

export const UsersPage = () => {
  const [users, setUsers] = useState<UserPublic[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    authApi.getAllUsers()
      .then(setUsers)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={styles.loading}>Загрузка...</div>;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 style={styles.title}>Пользователи</h1>
      <div style={styles.grid}>
        {users.map((user) => (
          <Card key={user.id} hoverable>
            <Link to={`/profile/${user.id}`} style={styles.userLink}>
              <strong style={styles.userName}>{user.name}</strong>
            </Link>
            <p style={styles.userStats}>★ Рейтинг: {user.rating} | Помог: {user.helpedCount} | Долгов: {user.debtCount}</p>
          </Card>
        ))}
      </div>
      {users.length === 0 && <p style={styles.empty}>Пользователей пока нет</p>}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '24px',
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '200px',
    color: theme.colors.text,
    fontSize: '18px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    marginBottom: '24px',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
    gap: '16px',
  },
  userLink: {
    textDecoration: 'none',
    color: theme.colors.text,
  },
  userName: {
    fontSize: '18px',
  },
  userStats: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginTop: '8px',
  },
  empty: {
    color: theme.colors.textMuted,
    textAlign: 'center',
    padding: '40px',
  },
};
