import { useEffect, useState, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import type { Message, User } from '../types';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { Avatar } from '../components/Avatar';
import { Card } from '../components/Card';
import { theme } from '../theme';

export const ChatPage = () => {
  const { id } = useParams<{ id: string }>();
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { user } = useAuth();

  useEffect(() => {
    authApi.getCurrentUser()
      .then(setCurrentUser)
      .catch(console.error);
  }, []);

  useEffect(() => {
    loadMessages();
    const interval = setInterval(loadMessages, 5000);
    return () => clearInterval(interval);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadMessages = async () => {
    try {
      const data = await chatApi.getMessages(Number(id));
      setMessages(data.content);
      await chatApi.markAsRead(Number(id));
      
      document.dispatchEvent(new CustomEvent('chatsUpdated'));
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

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  };

  if (loading) return <Spinner message="Загрузка сообщений..." />;

  return (
    <div style={styles.container}>
      <Link to="/chats" style={styles.backLink}>← Назад к чатам</Link>
      
      <Card style={styles.chatCard}>
        <div style={styles.messagesContainer}>
          {messages.length === 0 ? (
            <div style={styles.empty}>Сообщений пока нет</div>
          ) : (
              messages.map((msg) => {
              const isOwn = msg.senderId === user?.id;
              return (
                <div 
                  key={msg.id} 
                  style={{ 
                    ...styles.messageRow,
                    justifyContent: isOwn ? 'flex-end' : 'flex-start',
                  }}
                >
                  {isOwn && currentUser?.avatarUrl ? (
                    <Avatar name={currentUser.name} avatarUrl={currentUser.avatarUrl} size="small" />
                  ) : !isOwn ? (
                    <Avatar name={msg.senderName} avatarUrl={msg.senderAvatarUrl} size="small" />
                  ) : (
                    <Avatar name={currentUser?.name || 'Вы'} size="small" />
                  )}
                  <div style={{
                    ...styles.bubble,
                    background: isOwn ? 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)' : 'rgba(255,255,255,0.1)',
                    borderRadius: isOwn ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
                  }}>
                    <div style={styles.messageText}>{msg.content}</div>
                    <small style={styles.messageTime}>
                      {formatTime(msg.createdAt)}
                    </small>
                  </div>
                </div>
              );
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        <div style={styles.inputContainer}>
          <input
            type="text"
            value={newMessage}
            onChange={(e) => setNewMessage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Введите сообщение..."
            style={styles.input}
          />
          <Button onClick={handleSend}>
            Отправить
          </Button>
        </div>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 800,
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
  chatCard: {
    padding: '0',
    overflow: 'hidden',
  },
  messagesContainer: {
    height: 450,
    overflowY: 'auto',
    padding: '20px',
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  empty: {
    textAlign: 'center',
    color: theme.colors.textMuted,
    padding: '40px',
  },
  messageRow: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: '8px',
  },
  bubble: {
    padding: '12px 16px',
    maxWidth: '70%',
    wordBreak: 'break-word',
  },
  messageText: {
    fontSize: '14px',
    color: '#fff',
    lineHeight: 1.4,
  },
  messageTime: {
    opacity: 0.7,
    fontSize: '11px',
    color: 'rgba(255,255,255,0.8)',
    display: 'block',
    marginTop: '4px',
    textAlign: 'right',
  },
  inputContainer: {
    display: 'flex',
    gap: '12px',
    padding: '16px 20px',
    borderTop: `1px solid ${theme.colors.border}`,
    background: 'rgba(0,0,0,0.2)',
  },
  input: {
    flex: 1,
    padding: '12px 16px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
  },
};