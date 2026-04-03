import { useEffect, useState, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { MessageCircle, Send } from 'lucide-react';
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
  const [sending, setSending] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
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
    if (!loading) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading]);

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
    if (!newMessage.trim() || sending) return;
    setSending(true);
    try {
      await chatApi.sendMessage(Number(id), { content: newMessage });
      setNewMessage('');
      loadMessages();
    } catch (err) {
      console.error(err);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  };

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) {
      return 'Сегодня';
    } else if (date.toDateString() === yesterday.toDateString()) {
      return 'Вчера';
    } else {
      return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' });
    }
  };

  const shouldShowDateSeparator = (index: number) => {
    if (index === messages.length - 1) return true;
    const current = new Date(messages[index].createdAt).toDateString();
    const next = new Date(messages[index + 1].createdAt).toDateString();
    return current !== next;
  };

  const groupedMessages = [...messages].reverse().reduce((groups: { date: string; messages: Message[] }[], msg) => {
    const date = formatDate(msg.createdAt);
    const lastGroup = groups[groups.length - 1];
    if (lastGroup && lastGroup.date === date) {
      lastGroup.messages.push(msg);
    } else {
      groups.push({ date, messages: [msg] });
    }
    return groups;
  }, []);

  if (loading) return <Spinner message="Загрузка сообщений..." />;

  return (
    <div style={styles.container}>
      <Link to="/chats" style={styles.backLink}>← Назад к чатам</Link>
      
      <Card style={styles.chatCard}>
        <div style={styles.messagesContainer}>
          {messages.length === 0 ? (
            <div style={styles.empty}>
              <MessageCircle size={48} color={theme.colors.accent} style={{ marginBottom: 12, opacity: 0.6 }} />
              <div>Начните общение!</div>
              <div style={styles.emptyHint}>Отправьте первое сообщение</div>
            </div>
          ) : (
            <div style={styles.messagesWrapper}>
              {groupedMessages.map((group) => (
                <div key={group.date}>
                  <div style={styles.dateSeparator}>
                    <span style={styles.dateText}>{group.date}</span>
                  </div>
                  {group.messages.map((msg) => {
                    const isOwn = msg.senderId === user?.id;
                    return (
                      <div 
                        key={msg.id} 
                        className="message-animate"
                        style={{ 
                          ...styles.messageRow,
                          justifyContent: isOwn ? 'flex-end' : 'flex-start',
                        }}
                      >
                        {!isOwn && (
                          <Avatar name={msg.senderName} avatarUrl={msg.senderAvatarUrl} size="small" />
                        )}
                        <div style={{
                          ...styles.bubble,
                          background: isOwn ? 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)' : 'rgba(255,255,255,0.08)',
                          border: isOwn ? 'none' : '1px solid rgba(255,255,255,0.1)',
                          borderRadius: isOwn ? '20px 20px 6px 20px' : '20px 20px 20px 6px',
                        }}>
                          <div style={styles.messageText}>{msg.content}</div>
                          <div style={styles.messageFooter}>
                            <small style={styles.messageTime}>
                              {formatTime(msg.createdAt)}
                            </small>
                            {isOwn && (
                              <span style={styles.messageStatus}>
                                {msg.isRead ? '✓✓' : '✓'}
                              </span>
                            )}
                          </div>
                        </div>
                        {isOwn && (
                          <Avatar name={currentUser?.name || 'Вы'} avatarUrl={currentUser?.avatarUrl} size="small" />
                        )}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div style={styles.inputContainer}>
          <input
            ref={inputRef}
            type="text"
            value={newMessage}
            onChange={(e) => setNewMessage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
            placeholder="Введите сообщение..."
            style={styles.input}
          />
          <Button 
            onClick={handleSend} 
            disabled={!newMessage.trim() || sending}
            style={sending ? styles.sendBtnDisabled : undefined}
          >
            {sending ? '...' : <Send size={18} />}
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
    height: 500,
    overflowY: 'auto',
    padding: '16px',
    background: 'linear-gradient(180deg, rgba(6, 182, 212, 0.03) 0%, rgba(6, 182, 212, 0.08) 100%)',
  },
  messagesWrapper: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  dateSeparator: {
    display: 'flex',
    justifyContent: 'center',
    margin: '16px 0 12px',
  },
  dateText: {
    background: 'rgba(34, 211, 238, 0.2)',
    color: theme.colors.accentLight,
    padding: '4px 16px',
    borderRadius: '20px',
    fontSize: '12px',
    fontWeight: 500,
  },
  empty: {
    textAlign: 'center',
    color: theme.colors.textMuted,
    padding: '60px 40px',
  },
  emptyHint: {
    fontSize: '13px',
    marginTop: '8px',
    opacity: 0.7,
  },
  messageRow: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: '8px',
    marginBottom: '4px',
  },
  bubble: {
    padding: '10px 14px',
    maxWidth: '75%',
    wordBreak: 'break-word',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  },
  messageText: {
    fontSize: '14px',
    color: '#fff',
    lineHeight: 1.45,
  },
  messageFooter: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: '4px',
    marginTop: '4px',
  },
  messageTime: {
    fontSize: '10px',
    color: 'rgba(255,255,255,0.6)',
  },
  messageStatus: {
    fontSize: '11px',
    color: 'rgba(255,255,255,0.5)',
  },
  inputContainer: {
    display: 'flex',
    gap: '12px',
    padding: '16px 20px',
    borderTop: `1px solid ${theme.colors.border}`,
    background: 'rgba(0,0,0,0.3)',
  },
  input: {
    flex: 1,
    padding: '12px 18px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.06)',
    border: `1px solid rgba(255,255,255,0.1)`,
    borderRadius: '24px',
    color: theme.colors.text,
    outline: 'none',
  },
  sendBtnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
};