import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import type { Chat } from '../types';

export const ChatListPage = () => {
  const [chats, setChats] = useState<Chat[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadChats();
  }, []);

  const loadChats = async () => {
    try {
      const data = await chatApi.getChats();
      setChats(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteChat = async (chatId: number, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (window.confirm('Удалить чат?')) {
      try {
        await chatApi.deleteChat(chatId);
        loadChats();
      } catch (err) {
        console.error(err);
      }
    }
  };

  if (loading) return <div>Загрузка...</div>;

  return (
    <div style={{ padding: 20 }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h1>Чаты</h1>
        <Link to="/">← На главную</Link>
      </header>

      <div style={{ display: 'grid', gap: 10 }}>
        {chats.map((chat) => (
          <div 
            key={chat.id} 
            onClick={() => navigate(`/chats/${chat.id}`)}
            style={{ 
              border: '1px solid #ddd', 
              padding: 15, 
              borderRadius: 8,
              cursor: 'pointer',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}
          >
            <div>
              <strong>{chat.participantName}</strong>
              <p style={{ margin: 0, color: '#666', fontSize: 14 }}>
                {chat.lastMessage || 'Нет сообщений'}
              </p>
              <small style={{ color: '#999' }}>
                {chat.lastMessageAt ? new Date(chat.lastMessageAt).toLocaleString() : ''}
              </small>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              {chat.unreadCount > 0 && (
                <span style={{ 
                  backgroundColor: '#2196F3', 
                  color: 'white', 
                  borderRadius: '50%', 
                  padding: '4px 8px',
                  fontSize: 12
                }}>
                  {chat.unreadCount}
                </span>
              )}
              <button 
                onClick={(e) => handleDeleteChat(chat.id, e)}
                style={{ background: '#F44336', color: 'white', border: 'none', padding: '5px 10px', borderRadius: 4 }}
              >
                Удалить
              </button>
            </div>
          </div>
        ))}
      </div>

      {chats.length === 0 && <p>Чатов пока нет</p>}
    </div>
  );
};
