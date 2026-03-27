import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { Card, Button, Spinner, EmptyState, Avatar, Modal } from '../components';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';
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

  const confirmDeleteChat = (chatId: number, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDeleteChatId(chatId);
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
    if (diffMins < 60) return `${diffMins} мин назад`;
    if (diffHours < 24) return `${diffHours} ч назад`;
    if (diffDays < 7) return `${diffDays} дн назад`;
    return date.toLocaleDateString('ru-RU');
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
                style={styles.chatItem}
                onClick={() => navigate(`/chats/${chat.id}`)}
              >
                <div style={styles.chatLeft}>
                  <Avatar name={chat.participantName} avatarUrl={chat.participantAvatarUrl} size="large" />
                  <div style={styles.chatInfo}>
                    <div style={styles.chatName}>{chat.participantName}</div>
                    <div style={styles.lastMessage}>
                      {chat.lastMessage || 'Нет сообщений'}
                    </div>
                  </div>
                </div>
                <div style={styles.chatRight}>
                  <button
                    onClick={(e) => { e.stopPropagation(); setDeleteChatId(chat.id); }}
                    style={styles.deleteBtn}
                  >
                    🗑️
                  </button>
                  <span style={styles.time}>
                    {formatTime(chat.lastMessageAt)}
                  </span>
                  {chat.unreadCount > 0 && (
                    <span style={styles.unreadBadge}>{chat.unreadCount}</span>
                  )}
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
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '16px 20px',
    borderRadius: theme.borderRadius.md,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  chatLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '14px',
    flex: 1,
    minWidth: 0,
  },
  chatInfo: {
    flex: 1,
    minWidth: 0,
  },
  chatName: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 600,
    marginBottom: '4px',
  },
  lastMessage: {
    color: theme.colors.textSecondary,
    fontSize: '13px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    maxWidth: '300px',
  },
  chatRight: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    flexShrink: 0,
  },
  time: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  deleteBtn: {
    background: 'transparent',
    border: 'none',
    fontSize: '18px',
    cursor: 'pointer',
    padding: '4px 8px',
    opacity: 0.6,
    transition: 'opacity 0.2s',
  },
  unreadBadge: {
    background: theme.colors.accent,
    color: '#fff',
    borderRadius: '50%',
    width: '22px',
    height: '22px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '11px',
    fontWeight: 600,
  },
};