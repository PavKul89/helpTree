import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { helpApi } from '../api/helpApi';
import { useAuth } from '../context/AuthContext';
import type { Post, Help } from '../types';

export const MyOrdersPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [myPosts, setMyPosts] = useState<Post[]>([]);
  const [myHelps, setMyHelps] = useState<Help[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'posts' | 'helps'>('posts');

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [user]);

  const loadData = async () => {
    if (!user) return;
    try {
      const posts = await postsApi.getByUser(user.id);
      setMyPosts(posts);
      const helps = await helpApi.getHelpsByHelper(user.id);
      setMyHelps(helps);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletePost = async (postId: number) => {
    if (!window.confirm('Вы уверены, что хотите удалить пост?')) return;
    try {
      await postsApi.delete(postId);
      loadData();
    } catch (err) {
      console.error(err);
    }
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
      <Link to="/">← На главную</Link>
      <h1>Мои заказы</h1>

      <div style={{ marginBottom: 20 }}>
        <button
          onClick={() => setActiveTab('posts')}
          style={{
            padding: '10px 20px',
            marginRight: 10,
            backgroundColor: activeTab === 'posts' ? '#2196F3' : '#ddd',
            color: activeTab === 'posts' ? 'white' : 'black',
            border: 'none',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          Мои посты ({myPosts.length})
        </button>
        <button
          onClick={() => setActiveTab('helps')}
          style={{
            padding: '10px 20px',
            backgroundColor: activeTab === 'helps' ? '#2196F3' : '#ddd',
            color: activeTab === 'helps' ? 'white' : 'black',
            border: 'none',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          Мои отклики ({myHelps.length})
        </button>
      </div>

      {activeTab === 'posts' && (
        <div>
          {myPosts.length === 0 ? (
            <p>У вас пока нет постов</p>
          ) : (
            myPosts.map((post) => (
              <div key={post.id} style={{ border: '1px solid #ddd', padding: 15, marginBottom: 10, borderRadius: 8 }}>
                <Link to={`/posts/${post.id}`}><strong>{post.title}</strong></Link>
                <button onClick={() => handleDeletePost(post.id)} style={{ marginLeft: 10, backgroundColor: '#f44336', color: 'white', border: 'none', padding: '2px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
                  Удалить
                </button>
                <p>{post.description}</p>
                <small>
                  Статус: <span style={{ backgroundColor: getStatusColor(post.status), color: 'white', padding: '2px 6px', borderRadius: 4 }}>{post.status}</span>
                  {' | '}Категория: {post.category}
                </small>
              </div>
            ))
          )}
        </div>
      )}

      {activeTab === 'helps' && (
        <div>
          {myHelps.length === 0 ? (
            <p>У вас пока нет откликов</p>
          ) : (
            myHelps.map((help) => (
              <div key={help.id} style={{ border: '1px solid #ddd', padding: 15, marginBottom: 10, borderRadius: 8 }}>
                <Link to={`/posts/${help.postId}`}><strong>{help.postTitle}</strong></Link>
                <p>Автор поста: {help.receiverName}</p>
                <small>
                  Статус: <span style={{ backgroundColor: getStatusColor(help.status), color: 'white', padding: '2px 6px', borderRadius: 4 }}>{help.status}</span>
                </small>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};
