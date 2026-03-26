import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { chatApi } from '../api/chatApi';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { EmptyState } from '../components/EmptyState';
import { Modal } from '../components/Modal';
import { Avatar } from '../components/Avatar';
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

  const confirmDeleteChat = (chatId: number, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDeleteChatId(chatId);
  };

  if (loading) return <Spinner message="Загрузка чатов..." />;

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h1 style={styles.title}>Чаты</h1>
        <Link to="/" style={styles.backLink}>← На главную</Link>
      </header>

      <div style={styles.grid}>
        {chats.map((chat) => (
          <Card 
            key={chat.id} 
            hoverable
            style={styles.chatCard}
          >
            <div 
              onClick={() => navigate(`/chats/${chat.id}`)}
              style={styles.chatContent}
            >
              <div style={styles.chatInfo}>
                <Avatar name={chat.participantName} size="medium" showName />
                <p style={styles.lastMessage}>
                  {chat.lastMessage || 'Нет сообщений'}
                </p>
                <small style={styles.timestamp}>
                  {chat.lastMessageAt ? new Date(chat.lastMessageAt).toLocaleString() : ''}
                </small>
              </div>
              <div style={styles.chatActions}>
                {chat.unreadCount > 0 && (
                  <span style={styles.unreadBadge}>
                    {chat.unreadCount}
                  </span>
                )}
                <Button 
                  variant="danger"
                  onClick={(e) => confirmDeleteChat(chat.id, e)}
                  style={styles.deleteBtn}
                >
                  Удалить
                </Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {chats.length === 0 && (
        <EmptyState 
          icon="💬" 
          title="Чатов пока нет" 
          description="Начните общение с другими пользователями!"
        />
      )}

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
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '24px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    margin: 0,
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  grid: {
    display: 'grid',
    gap: '12px',
  },
  chatCard: {
    padding: '16px',
  },
  chatContent: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    cursor: 'pointer',
  },
  chatInfo: {
    flex: 1,
  },
  lastMessage: {
    margin: '4px 0',
    color: theme.colors.textSecondary,
    fontSize: '14px',
  },
  timestamp: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  chatActions: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  unreadBadge: {
    backgroundColor: theme.colors.accent,
    color: 'white',
    borderRadius: '50%',
    padding: '4px 10px',
    fontSize: '12px',
    fontWeight: 600,
  },
  deleteBtn: {
    padding: '6px 12px',
    fontSize: '12px',
  },
  empty: {
    color: theme.colors.textMuted,
    textAlign: 'center',
    padding: '40px',
  },
};
