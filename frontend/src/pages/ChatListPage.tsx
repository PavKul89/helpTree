import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { Card, Spinner, EmptyState, Avatar, Modal } from '../components';
import { theme } from '../theme';
import type { Chat } from '../types';

export const ChatListPage = () => {
  const [chats, setChats] = useState<Chat[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleteChatId, setDeleteChatId] = useState<number | null>(null);
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

  const handleDeleteChat = async () => {
    if (!deleteChatId) return;
    try {
      await chatApi.deleteChat(deleteChatId);
      setDeleteChatId(null);
      loadChats();
    } catch (err) {
      console.error(err);
    }
  };

  const formatTime = (dateString?: string) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    
    if (diffMins < 1) return 'только что';
    if (diffMins < 60) return `${diffMins} мин`;
    if (diffHours < 24) return `${diffHours} ч`;
    if (diffDays < 7) return `${diffDays} дн`;
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  };

  if (loading) return <Spinner message="Загрузка чатов..." />;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        <div style={styles.header}>
          <h1 className="page-title" style={styles.title}>Сообщения</h1>
          <span style={styles.count}>{chats.length} чатов</span>
        </div>

        {chats.length === 0 ? (
          <EmptyState 
            variant="chats"
            title="Чатов пока нет" 
            description="Начните общение с другими пользователями!"
          />
        ) : (
          <div style={styles.list}>
            {chats.map((chat) => (
              <div 
                key={chat.id} 
                className="chat-item"
                style={{
                  ...styles.chatItem,
                  ...(chat.unreadCount > 0 ? styles.chatItemUnread : {}),
                }}
                onClick={() => navigate(`/chats/${chat.id}`)}
              >
                <div style={styles.avatarWrapper}>
                  <Avatar name={chat.participantName} avatarUrl={chat.participantAvatarUrl} size="large" />
                  <div style={styles.onlineIndicator} />
                </div>
                <div style={styles.chatContent}>
                  <div style={styles.chatTop}>
                    <div style={styles.chatName}>{chat.participantName}</div>
                    <div style={styles.chatMeta}>
                      <span style={{
                        ...styles.time,
                        ...(chat.unreadCount > 0 ? styles.timeUnread : {}),
                      }}>
                        {formatTime(chat.lastMessageAt)}
                      </span>
                      {chat.unreadCount > 0 && (
                        <span style={styles.unreadBadge}>
                          {chat.unreadCount > 9 ? '9+' : chat.unreadCount}
                        </span>
                      )}
                    </div>
                  </div>
                  <div style={styles.chatBottom}>
                    <div style={{
                      ...styles.lastMessage,
                      ...(chat.unreadCount > 0 ? styles.lastMessageUnread : {}),
                    }}>
                      {chat.lastMessage || 'Нет сообщений'}
                    </div>
                    <button
                      onClick={(e) => { e.stopPropagation(); setDeleteChatId(chat.id); }}
                      style={styles.deleteBtn}
                    >
                      ✕
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Modal
        isOpen={!!deleteChatId}
        onClose={() => setDeleteChatId(null)}
        onConfirm={handleDeleteChat}
        title="Удаление чата"
        message="Вы уверены, что хотите удалить этот чат? Все сообщения будут потеряны."
        confirmText="Удалить"
        cancelText="Отмена"
      />
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 700,
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
  mainCard: {
    padding: '0',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '24px 32px',
    background: 'linear-gradient(180deg, rgba(34, 211, 238, 0.08) 0%, transparent 100%)',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  title: {
    color: theme.colors.text,
    fontSize: '24px',
    fontWeight: 700,
    margin: 0,
  },
  count: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  list: {
    padding: '8px',
  },
  chatItem: {
    display: 'flex',
    alignItems: 'center',
    padding: '16px 20px',
    borderRadius: theme.borderRadius.md,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    gap: '14px',
  },
  chatItemUnread: {
    background: 'linear-gradient(90deg, rgba(6, 182, 212, 0.08) 0%, transparent 50%)',
  },
  avatarWrapper: {
    position: 'relative',
    flexShrink: 0,
  },
  onlineIndicator: {
    position: 'absolute',
    bottom: '2px',
    right: '2px',
    width: '14px',
    height: '14px',
    background: '#22c55e',
    borderRadius: '50%',
    border: '3px solid var(--card-bg)',
  },
  chatContent: {
    flex: 1,
    minWidth: 0,
  },
  chatTop: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '6px',
  },
  chatName: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 600,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    flex: 1,
    marginRight: '8px',
  },
  chatMeta: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    flexShrink: 0,
  },
  chatBottom: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  time: {
    fontSize: '12px',
    color: theme.colors.textMuted,
  },
  timeUnread: {
    color: theme.colors.accentLight,
    fontWeight: 500,
  },
  lastMessage: {
    color: theme.colors.textSecondary,
    fontSize: '13px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    flex: 1,
    marginRight: '8px',
  },
  lastMessageUnread: {
    color: theme.colors.text,
    fontWeight: 500,
  },
  deleteBtn: {
    background: 'transparent',
    border: 'none',
    color: theme.colors.textMuted,
    fontSize: '14px',
    cursor: 'pointer',
    padding: '4px 8px',
    opacity: 0,
    transition: 'opacity 0.2s',
    borderRadius: '4px',
  },
  unreadBadge: {
    background: theme.colors.accent,
    color: '#fff',
    borderRadius: '12px',
    minWidth: '20px',
    height: '20px',
    padding: '0 6px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '11px',
    fontWeight: 600,
  },
};