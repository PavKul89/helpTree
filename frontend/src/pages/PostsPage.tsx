import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import type { Post } from '../types';
import { useAuth } from '../context/AuthContext';

const CATEGORIES = ['Все', 'Дрова', 'Уборка', 'Ремонт', 'Доставка', 'Покупки', 'Другое'];
const STATUSES = ['Все', 'OPEN', 'ACCEPTED', 'COMPLETED', 'CONFIRMED', 'CANCELLED'];

export const PostsPage = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('Все');
  const [status, setStatus] = useState('Все');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const { user, logout } = useAuth();

  useEffect(() => {
    loadPosts();
  }, []);

  const loadPosts = async (pageNum = 0) => {
    try {
      const params: any = { page: pageNum, size: 10 };
      if (search) params.title = search;
      if (category !== 'Все') params.category = category;
      if (status !== 'Все') params.status = status;
      const data = await postsApi.getAll(params);
      setPosts(data.content);
      setTotalPages(data.totalPages);
      setPage(data.number);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    loadPosts(0);
  };

  const handleCategoryChange = (value: string) => {
    setCategory(value);
    setLoading(true);
    loadPosts(0);
  };

  const handleStatusChange = (value: string) => {
    setStatus(value);
    setLoading(true);
    loadPosts(0);
  };

  const handlePageChange = (newPage: number) => {
    setLoading(true);
    loadPosts(newPage);
  };

  const getStatusColor = (status: string) => {
    const colors: Record<string, string> = {
      OPEN: '#4CAF50',
      ACCEPTED: '#2196F3',
      COMPLETED: '#FF9800',
      CONFIRMED: '#9C27B0',
      CANCELLED: '#F44336',
    };
    return colors[status] || '#999';
  };

  if (loading) return <div>Загрузка...</div>;

  return (
    <div style={{ padding: 20 }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1>Посты о помощи</h1>
        <div>
          {user ? (
            <>
              <Link to="/profile" style={{ marginRight: 15 }}>Привет, {user.name}!</Link>
              <Link to="/posts/new" style={{ marginRight: 15 }}>Создать пост</Link>
              <Link to="/chats" style={{ marginRight: 15 }}>Чаты</Link>
              <Link to="/my-orders" style={{ marginRight: 15 }}>Мои заказы</Link>
              <Link to="/users" style={{ marginRight: 15 }}>Пользователи</Link>
              <button onClick={logout}>Выйти</button>
            </>
          ) : (
            <>
              <Link to="/login" style={{ marginRight: 10 }}>Вход</Link>
              <Link to="/register">Регистрация</Link>
            </>
          )}
        </div>
      </header>

      <form onSubmit={handleSearch} style={{ marginBottom: 20, padding: 15, backgroundColor: '#f5f5f5', borderRadius: 8 }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Поиск по названию..."
            style={{ padding: 8, width: 200 }}
          />
          <select value={category} onChange={(e) => handleCategoryChange(e.target.value)} style={{ padding: 8 }}>
            {CATEGORIES.map(cat => <option key={cat} value={cat}>{cat === 'Все' ? 'Все категории' : cat}</option>)}
          </select>
          <select value={status} onChange={(e) => handleStatusChange(e.target.value)} style={{ padding: 8 }}>
            {STATUSES.map(s => <option key={s} value={s}>{s === 'Все' ? 'Все статусы' : s}</option>)}
          </select>
          <button type="submit" style={{ padding: '8px 16px' }}>Найти</button>
        </div>
      </form>

      <div style={{ display: 'grid', gap: 15 }}>
        {posts.map((post) => (
          <div key={post.id} style={{ border: '1px solid #ddd', padding: 15, borderRadius: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <h3>
                <Link to={`/posts/${post.id}`}>{post.title}</Link>
              </h3>
              <span style={{ 
                backgroundColor: getStatusColor(post.status), 
                color: 'white', 
                padding: '4px 8px', 
                borderRadius: 4,
                fontSize: 12
              }}>
                {post.status}
              </span>
            </div>
            <p>{post.description}</p>
            <small>
              Автор: <Link to={`/profile/${post.userId}`}>{post.authorName}</Link> (рейтинг: {post.authorRating}) | 
              Дата: {new Date(post.createdAt).toLocaleDateString()}
            </small>
          </div>
        ))}
      </div>

      {posts.length === 0 && <p>Постов не найдено</p>}

      {totalPages > 1 && (
        <div style={{ marginTop: 20, display: 'flex', justifyContent: 'center', gap: 10 }}>
          <button
            onClick={() => handlePageChange(page - 1)}
            disabled={page === 0}
            style={{ padding: '8px 16px', cursor: page === 0 ? 'not-allowed' : 'pointer' }}
          >
            Назад
          </button>
          <span style={{ padding: '8px 16px' }}>
            Страница {page + 1} из {totalPages}
          </span>
          <button
            onClick={() => handlePageChange(page + 1)}
            disabled={page >= totalPages - 1}
            style={{ padding: '8px 16px', cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer' }}
          >
            Вперёд
          </button>
        </div>
      )}
    </div>
  );
};
