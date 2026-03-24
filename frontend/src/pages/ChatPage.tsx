import { useEffect, useState, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import type { Message } from '../types';
import { useAuth } from '../context/AuthContext';

export const ChatPage = () => {
  const { id } = useParams<{ id: string }>();
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { user } = useAuth();

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    loadMessages();
    const interval = setInterval(loadMessages, 5000);
    return () => clearInterval(interval);
  }, [id]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadMessages = async () => {
    try {
      const data = await chatApi.getMessages(Number(id));
      setMessages(data.content);
      await chatApi.markAsRead(Number(id));
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSend = async () => {
    if (!newMessage.trim()) return;
    try {
      await chatApi.sendMessage(Number(id), { content: newMessage });
      setNewMessage('');
      loadMessages();
    } catch (err) {
      console.error(err);
    }
  };

  if (loading) return <div>Загрузка...</div>;

  return (
    <div style={{ padding: 20, maxWidth: 600, margin: '0 auto' }}>
      <header style={{ marginBottom: 20 }}>
        <Link to="/chats">← Назад к чатам</Link>
      </header>

      <div style={{ 
        border: '1px solid #ddd', 
        borderRadius: 8, 
        height: 400, 
        overflowY: 'auto', 
        padding: 15,
        marginBottom: 15
      }}>
        {messages.map((msg) => (
          <div 
            key={msg.id} 
            style={{ 
              textAlign: msg.senderId === user?.id ? 'right' : 'left',
              marginBottom: 10
            }}
          >
            <div style={{ 
              display: 'inline-block',
              background: msg.senderId === user?.id ? '#2196F3' : '#eee',
              color: msg.senderId === user?.id ? 'white' : 'black',
              padding: '8px 12px',
              borderRadius: 12,
              maxWidth: '80%'
            }}>
              <div>{msg.content}</div>
              <small style={{ opacity: 0.7, fontSize: 10 }}>
                {new Date(msg.createdAt).toLocaleTimeString()}
              </small>
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div style={{ display: 'flex', gap: 10 }}>
        <input
          type="text"
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSend()}
          placeholder="Сообщение..."
          style={{ flex: 1, padding: 10 }}
        />
        <button onClick={handleSend} style={{ padding: '10px 20px' }}>
          Отправить
        </button>
      </div>
    </div>
  );
};
