import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MessageSquare, Search, Plus, ChevronRight } from 'lucide-react';
import { chatApi } from '../api/chatApi';
import { Avatar } from '../components/Avatar';
import { Modal } from '../components/Modal';
import { Spinner } from '../components/Spinner';
import { theme } from '../theme';
import type { Chat } from '../types';

export const ChatListPage = () => {
  const [chats, setChats] = useState<Chat[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleteChatId, setDeleteChatId] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
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

  const filteredChats = chats.filter(chat => 
    chat.participantName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const totalUnread = chats.reduce((sum, chat) => sum + (chat.unreadCount || 0), 0);

  if (loading) return <Spinner message="Загрузка чатов..." />;

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <div style={styles.headerIcon}>
            <MessageSquare size={24} color={theme.colors.accent} />
          </div>
          <div>
            <h1 style={styles.title}>Сообщения</h1>
            {totalUnread > 0 && (
              <span style={styles.unreadTotal}>{totalUnread} непрочитанных</span>
            )}
          </div>
        </div>
        <Link to="/" style={styles.backButton}>
          <Plus size={20} />
          <span>Новый чат</span>
        </Link>
      </div>

      {/* Search */}
      <div style={styles.searchContainer}>
        <Search size={18} style={styles.searchIcon} />
        <input
          type="text"
          placeholder="Поиск по сообщениям..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={styles.searchInput}
        />
      </div>

      {/* Chat List */}
      {filteredChats.length === 0 ? (
        <div style={styles.empty}>
          <div style={styles.emptyIcon}>
            <MessageSquare size={64} color={theme.colors.accent} />
          </div>
          <h3 style={styles.emptyTitle}>
            {searchQuery ? 'Ничего не найдено' : 'Чатов пока нет'}
          </h3>
          <p style={styles.emptyText}>
            {searchQuery 
              ? 'Попробуйте изменить поисковый запрос'
              : 'Начните общение с другими пользователями!'
            }
          </p>
          {!searchQuery && (
            <Link to="/" style={styles.emptyLink}>
              Найти пользователей
            </Link>
          )}
        </div>
      ) : (
        <div style={styles.chatList}>
          {filteredChats.map((chat) => (
            <div 
              key={chat.id} 
              style={{
                ...styles.chatItem,
                ...(chat.unreadCount > 0 ? styles.chatItemUnread : {}),
              }}
              onClick={() => navigate(`/chats/${chat.id}`)}
            >
              <div style={styles.avatarContainer}>
                <Avatar 
                  name={chat.participantName} 
                  avatarUrl={chat.participantAvatarUrl} 
                  size="large" 
                />
                <div style={styles.onlineDot} />
              </div>

              <div style={styles.chatInfo}>
                <div style={styles.chatHeader}>
                  <span style={{
                    ...styles.chatName,
                    fontWeight: chat.unreadCount > 0 ? 700 : 600,
                  }}>
                    {chat.participantName}
                  </span>
                  <span style={{
                    ...styles.chatTime,
                    color: chat.unreadCount > 0 ? theme.colors.accentLight : theme.colors.textMuted,
                  }}>
                    {formatTime(chat.lastMessageAt)}
                  </span>
                </div>
                
                <div style={styles.chatPreview}>
                  <span style={{
                    ...styles.lastMessage,
                    color: chat.unreadCount > 0 ? theme.colors.text : theme.colors.textSecondary,
                    fontWeight: chat.unreadCount > 0 ? 500 : 400,
                  }}>
                    {chat.lastMessage || 'Нет сообщений'}
                  </span>
                  
                  <div style={styles.chatActions}>
                    {chat.unreadCount > 0 && (
                      <span style={styles.unreadBadge}>
                        {chat.unreadCount > 99 ? '99+' : chat.unreadCount}
                      </span>
                    )}
                    <ChevronRight size={18} color={theme.colors.textMuted} />
                  </div>
                </div>
              </div>

              <button
                onClick={(e) => { e.stopPropagation(); setDeleteChatId(chat.id); }}
                style={styles.deleteBtn}
                title="Удалить чат"
              >
                ×
              </button>
            </div>
          ))}
        </div>
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
    maxWidth: 900,
    margin: '0 auto',
    padding: '0 24px 24px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '24px 0',
    gap: '16px',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  headerIcon: {
    width: '56px',
    height: '56px',
    borderRadius: '16px',
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.2) 0%, rgba(6, 182, 212, 0.1) 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
    margin: 0,
    lineHeight: 1.2,
  },
  unreadTotal: {
    color: theme.colors.accentLight,
    fontSize: '13px',
    fontWeight: 500,
  },
  backButton: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '12px 20px',
    background: theme.gradients.button,
    borderRadius: '12px',
    color: theme.colors.text,
    textDecoration: 'none',
    fontSize: '14px',
    fontWeight: 600,
    boxShadow: theme.shadows.button,
    transition: 'all 0.2s ease',
  },
  searchContainer: {
    position: 'relative',
    marginBottom: '20px',
  },
  searchIcon: {
    position: 'absolute',
    left: '16px',
    top: '50%',
    transform: 'translateY(-50%)',
    color: theme.colors.textMuted,
  },
  searchInput: {
    width: '100%',
    padding: '14px 16px 14px 48px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.08)',
    border: '1px solid rgba(255,255,255,0.12)',
    borderRadius: '14px',
    color: theme.colors.text,
    outline: 'none',
    boxSizing: 'border-box',
  },
  chatList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  chatItem: {
    display: 'flex',
    alignItems: 'center',
    padding: '16px 20px',
    background: 'rgba(255,255,255,0.05)',
    borderRadius: '16px',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    gap: '16px',
    position: 'relative',
  },
  chatItemUnread: {
    background: 'linear-gradient(90deg, rgba(6, 182, 212, 0.12) 0%, rgba(6, 182, 212, 0.05) 100%)',
    borderLeft: '3px solid' + theme.colors.accent,
  },
  avatarContainer: {
    position: 'relative',
    flexShrink: 0,
  },
  onlineDot: {
    position: 'absolute',
    bottom: '2px',
    right: '2px',
    width: '16px',
    height: '16px',
    background: '#22c55e',
    borderRadius: '50%',
    border: '3px solid rgba(26, 26, 46, 1)',
  },
  chatInfo: {
    flex: 1,
    minWidth: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  chatHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  chatName: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  chatTime: {
    fontSize: '12px',
    fontWeight: 500,
    flexShrink: 0,
  },
  chatPreview: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '8px',
  },
  lastMessage: {
    fontSize: '14px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    flex: 1,
  },
  chatActions: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    flexShrink: 0,
  },
  unreadBadge: {
    background: theme.colors.accent,
    color: '#fff',
    borderRadius: '12px',
    minWidth: '24px',
    height: '24px',
    padding: '0 8px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '12px',
    fontWeight: 700,
  },
  deleteBtn: {
    position: 'absolute',
    top: '12px',
    right: '12px',
    background: 'rgba(255,255,255,0.1)',
    border: 'none',
    color: theme.colors.textMuted,
    fontSize: '18px',
    cursor: 'pointer',
    padding: '4px 10px',
    borderRadius: '8px',
    opacity: 0,
    transition: 'opacity 0.2s, background 0.2s',
  },
  empty: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '80px 40px',
    textAlign: 'center',
  },
  emptyIcon: {
    marginBottom: '24px',
    opacity: 0.4,
  },
  emptyTitle: {
    color: theme.colors.text,
    fontSize: '20px',
    fontWeight: 600,
    margin: '0 0 8px',
  },
  emptyText: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    margin: 0,
    maxWidth: '300px',
  },
  emptyLink: {
    marginTop: '20px',
    padding: '12px 24px',
    background: theme.gradients.button,
    borderRadius: '12px',
    color: theme.colors.text,
    textDecoration: 'none',
    fontSize: '14px',
    fontWeight: 600,
  },
};
