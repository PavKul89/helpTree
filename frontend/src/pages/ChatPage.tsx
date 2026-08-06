import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { MessageCircle, Send, ArrowLeft, MoreVertical, Wifi, WifiOff } from 'lucide-react';
import { chatApi } from '../api/chatApi';
import { authApi } from '../api/authApi';
import { useWebSocketChat } from '../hooks/useWebSocketChat';
import type { Message, User } from '../types';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { formatTime, formatChatDate } from '../utils/dateUtils';
import { Avatar } from '../components/Avatar';
import { Card } from '../components/Card';
import { theme } from '../theme';

export const ChatPage = () => {
  const { id } = useParams<{ id: string }>();
  const chatId = Number(id);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [chatParticipant, setChatParticipant] = useState<{name: string; avatarUrl?: string} | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const { user } = useAuth();

  const handleNewMessage = useCallback((message: Message) => {
    setMessages((prev) => {
      const exists = prev.find((m) => m.id === message.id);
      if (exists) return prev;
      const tempExists = prev.find((m) => m.id < 0 && m.senderId === message.senderId && m.content === message.content);
      if (tempExists) {
        return prev.map((m) => (m === tempExists ? message : m));
      }
      return [...prev, message];
    });
  }, []);

  const handleMessageDeleted = useCallback((_chatId: number, messageId: number) => {
    setMessages((prev) => prev.filter((m) => m.id !== messageId));
  }, []);

  const handleMessagesRead = useCallback((_chatId: number) => {
    setMessages((prev) => prev.map((m) => ({ ...m, isRead: true })));
  }, []);

  const handleChatDeleted = useCallback((_chatId: number) => {
  }, []);

  const { isConnected, subscribeToChat, sendMessage } = useWebSocketChat({
    userId: user?.id ?? null,
    onNewMessage: handleNewMessage,
    onMessageDeleted: handleMessageDeleted,
    onMessagesRead: handleMessagesRead,
    onChatDeleted: handleChatDeleted,
  });

  useEffect(() => {
    authApi.getCurrentUser()
      .then(setCurrentUser)
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (!chatId) return;

    const loadInitial = async () => {
      try {
        const data = await chatApi.getMessages(chatId);
        const reversed = [...data.content].reverse();
        setMessages(reversed);
        await chatApi.markAsRead(chatId);

        if (data.content.length > 0) {
          const otherUser = data.content.find((m) => m.senderId !== user?.id);
          if (otherUser) {
            setChatParticipant({ name: otherUser.senderName, avatarUrl: otherUser.senderAvatarUrl });
          }
        }
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    loadInitial();
  }, [chatId, user?.id]);

  useEffect(() => {
    if (isConnected && chatId) {
      subscribeToChat(chatId);
    }
  }, [isConnected, chatId, subscribeToChat]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    if (!newMessage.trim() || !isConnected) return;

    const tempMessage: Message = {
      id: Date.now() * -1,
      chatId,
      senderId: user!.id,
      senderName: user!.name || 'Вы',
      senderAvatarUrl: user?.avatarUrl,
      content: newMessage,
      createdAt: new Date().toISOString(),
      isRead: false,
    };

    setMessages((prev) => [...prev, tempMessage]);
    setNewMessage('');
    inputRef.current?.focus();

    sendMessage(chatId, tempMessage.content);
  };

  const groupedMessages = messages.reduce((groups: { date: string; messages: Message[] }[], msg) => {
    const date = formatChatDate(msg.createdAt);
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
      <div style={styles.chatHeader}>
        <Link to="/chats" style={styles.backButton}>
          <ArrowLeft size={20} />
        </Link>

        {chatParticipant && (
          <div style={styles.chatInfo}>
            <Avatar
              name={chatParticipant.name}
              avatarUrl={chatParticipant.avatarUrl}
              size="medium"
            />
            <div style={styles.chatInfoText}>
              <div style={styles.chatName}>{chatParticipant.name}</div>
              <div style={styles.connectionStatus}>
                <span style={isConnected ? styles.connectionDotConnected : styles.connectionDotDisconnected} />
                {isConnected ? 'онлайн' : 'подключение...'}
              </div>
            </div>
          </div>
        )}

        <div style={styles.wsIndicator}>
          {isConnected ? <Wifi size={16} color="#22c55e" /> : <WifiOff size={16} color="#ef4444" />}
        </div>

        <button style={styles.menuButton}>
          <MoreVertical size={20} />
        </button>
      </div>

      <Card style={styles.chatCard}>
        <div style={styles.messagesContainer}>
          {messages.length === 0 ? (
            <div style={styles.empty}>
              <div style={styles.emptyIcon}>
                <MessageCircle size={48} color={theme.colors.accent} />
              </div>
              <div style={styles.emptyTitle}>Начните общение!</div>
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
                          background: isOwn
                            ? 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)'
                            : 'rgba(255,255,255,0.1)',
                          border: isOwn ? 'none' : '1px solid rgba(255,255,255,0.15)',
                          borderRadius: isOwn
                            ? '20px 20px 6px 20px'
                            : '20px 20px 20px 6px',
                          boxShadow: isOwn
                            ? '0 4px 12px rgba(6, 182, 212, 0.3)'
                            : '0 2px 8px rgba(0,0,0,0.2)',
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
            placeholder={isConnected ? 'Напишите сообщение...' : 'Подключение к серверу...'}
            disabled={!isConnected}
            style={isConnected ? styles.input : styles.inputDisabled}
          />
          <Button
            onClick={handleSend}
            disabled={!newMessage.trim() || !isConnected}
            style={(!newMessage.trim() || !isConnected) ? styles.sendBtnDisabled : styles.sendBtn}
          >
            <Send size={18} />
          </Button>
        </div>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 1000,
    margin: '0 auto',
    padding: '0 24px 24px',
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  chatHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    padding: '12px 0',
  },
  backButton: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '40px',
    height: '40px',
    borderRadius: '12px',
    background: 'rgba(255,255,255,0.1)',
    color: theme.colors.text,
    transition: 'all 0.2s ease',
  },
  chatInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    flex: 1,
  },
  chatInfoText: {
    display: 'flex',
    flexDirection: 'column',
  },
  chatName: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
  },
  connectionStatus: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    fontSize: '12px',
    color: '#22c55e',
  },
  connectionDotConnected: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#22c55e',
    display: 'inline-block',
  },
  connectionDotDisconnected: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#ef4444',
    display: 'inline-block',
  },
  wsIndicator: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '32px',
    height: '32px',
    borderRadius: '8px',
    background: 'rgba(255,255,255,0.05)',
  },
  menuButton: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '40px',
    height: '40px',
    borderRadius: '12px',
    background: 'rgba(255,255,255,0.1)',
    border: 'none',
    color: theme.colors.text,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  chatCard: {
    padding: 0,
    overflow: 'hidden',
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    minHeight: '500px',
    maxHeight: 'calc(100vh - 200px)',
  },
  messagesContainer: {
    flex: 1,
    overflowY: 'auto',
    padding: '20px',
    background: 'linear-gradient(180deg, rgba(6, 182, 212, 0.05) 0%, rgba(6, 182, 212, 0.02) 100%)',
  },
  messagesWrapper: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  dateSeparator: {
    display: 'flex',
    justifyContent: 'center',
    margin: '20px 0 16px',
  },
  dateText: {
    background: 'rgba(34, 211, 238, 0.2)',
    color: theme.colors.accentLight,
    padding: '6px 16px',
    borderRadius: '20px',
    fontSize: '12px',
    fontWeight: 500,
  },
  empty: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    color: theme.colors.textMuted,
    padding: '60px 40px',
    textAlign: 'center',
  },
  emptyIcon: {
    marginBottom: '16px',
    opacity: 0.6,
  },
  emptyTitle: {
    fontSize: '18px',
    fontWeight: 600,
    marginBottom: '8px',
  },
  emptyHint: {
    fontSize: '14px',
    opacity: 0.7,
  },
  messageRow: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: '10px',
    marginBottom: '6px',
  },
  bubble: {
    padding: '12px 16px',
    maxWidth: '70%',
    wordBreak: 'break-word',
  },
  messageText: {
    fontSize: '15px',
    color: '#fff',
    lineHeight: 1.5,
  },
  messageFooter: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: '6px',
    marginTop: '6px',
  },
  messageTime: {
    fontSize: '11px',
    color: 'rgba(255,255,255,0.6)',
  },
  messageStatus: {
    fontSize: '12px',
    color: 'rgba(255,255,255,0.5)',
  },
  inputContainer: {
    display: 'flex',
    gap: '12px',
    padding: '16px 20px',
    borderTop: `1px solid rgba(255,255,255,0.1)`,
    background: 'rgba(0,0,0,0.3)',
  },
  input: {
    flex: 1,
    padding: '14px 20px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.08)',
    border: `1px solid rgba(255,255,255,0.15)`,
    borderRadius: '24px',
    color: theme.colors.text,
    outline: 'none',
    transition: 'all 0.2s ease',
  },
  inputDisabled: {
    flex: 1,
    padding: '14px 20px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.04)',
    border: `1px solid rgba(255,255,255,0.08)`,
    borderRadius: '24px',
    color: theme.colors.textMuted,
    outline: 'none',
    cursor: 'not-allowed',
  },
  sendBtn: {
    padding: '14px 20px',
    minWidth: '50px',
  },
  sendBtnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
    padding: '14px 20px',
    minWidth: '50px',
  },
};
