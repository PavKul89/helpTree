import { useEffect, useState, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import type { Message } from '../types';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/Button';
import { theme } from '../theme';

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

  if (loading) return <div style={styles.loading}>Загрузка...</div>;

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <Link to="/chats" style={styles.backLink}>← Назад к чатам</Link>
      </header>

      <div style={styles.messagesContainer}>
        {messages.map((msg) => (
          <div 
            key={msg.id} 
            style={{ 
              textAlign: msg.senderId === user?.id ? 'right' : 'left',
              marginBottom: 12
            }}
          >
            <div style={{ 
              display: 'inline-block',
              background: msg.senderId === user?.id ? theme.colors.accent : 'rgba(255,255,255,0.1)',
              color: msg.senderId === user?.id ? 'white' : theme.colors.text,
              padding: '10px 14px',
              borderRadius: 16,
              maxWidth: '80%'
            }}>
              <div style={styles.messageText}>{msg.content}</div>
              <small style={styles.messageTime}>
                {new Date(msg.createdAt).toLocaleTimeString()}
              </small>
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div style={styles.inputContainer}>
        <input
          type="text"
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSend()}
          placeholder="Сообщение..."
          style={styles.input}
        />
        <Button onClick={handleSend}>
          Отправить
        </Button>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 800,
    margin: '0 auto',
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
  header: {
    marginBottom: '20px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  messagesContainer: {
    background: 'rgba(255,255,255,0.05)',
    borderRadius: theme.borderRadius.lg,
    border: `1px solid ${theme.colors.border}`,
    height: 400,
    overflowY: 'auto',
    padding: '16px',
    marginBottom: '16px',
  },
  messageText: {
    fontSize: '14px',
  },
  messageTime: {
    opacity: 0.7,
    fontSize: '11px',
    display: 'block',
    marginTop: '4px',
  },
  inputContainer: {
    display: 'flex',
    gap: '12px',
  },
  input: {
    flex: 1,
    padding: '12px 16px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
  },
};
