import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
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

  if (loading) return <div>Загрузка...</div>;

  return (
    <div style={{ padding: 20 }}>
      <header style={{ marginBottom: 20 }}>
        <Link to="/">← На главную</Link>
      </header>
      <h1>Пользователи</h1>
      <div style={{ display: 'grid', gap: 15 }}>
        {users.map((user) => (
          <div key={user.id} style={{ border: '1px solid #ddd', padding: 15, borderRadius: 8 }}>
            <Link to={`/profile/${user.id}`}>
              <strong>{user.name}</strong>
            </Link>
            <p>Рейтинг: {user.rating} | Помог: {user.helpedCount} | Долгов: {user.debtCount}</p>
          </div>
        ))}
      </div>
      {users.length === 0 && <p>Пользователей пока нет</p>}
    </div>
  );
};
