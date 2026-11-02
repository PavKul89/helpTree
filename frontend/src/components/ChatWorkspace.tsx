import {
  FormEvent,
  KeyboardEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCheck,
  ChevronRight,
  LoaderCircle,
  MessageCircle,
  MessageSquareText,
  RefreshCw,
  Search,
  Send,
  Trash2,
  X,
} from 'lucide-react';
import { chatApi } from '../api/chatApi';
import { useWebSocketChat } from '../hooks/useWebSocketChat';
import { Avatar } from './Avatar';
import { useAuth } from '../context/AuthContext';
import { useToast } from './Toast';
import { formatChatDate, formatTime, getRelativeTime } from '../utils/dateUtils';
import type { Chat, Message } from '../types';
import './ChatWorkspace.css';

const PAGE_SIZE = 30;
const MAX_MESSAGE_LENGTH = 5000;

const uniqueMessages = (messages: Message[]): Message[] => {
  const seen = new Set<number>();
  return messages.filter((message) => {
    if (seen.has(message.id)) return false;
    seen.add(message.id);
    return true;
  });
};

const sortChats = (chats: Chat[]): Chat[] =>
  [...chats].sort((a, b) => {
    const timeA = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0;
    const timeB = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0;
    return timeB - timeA;
  });

export const ChatWorkspace = () => {
  const { id: routeChatId } = useParams<{ id?: string }>();
  const chatId = routeChatId ? Number(routeChatId) : null;
  const invalidChatId =
    routeChatId !== undefined &&
    (!Number.isSafeInteger(chatId) || (chatId ?? 0) <= 0);
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showToast } = useToast();

  const [chats, setChats] = useState<Chat[]>([]);
  const [chatsLoading, setChatsLoading] = useState(true);
  const [chatsError, setChatsError] = useState(false);
  const [search, setSearch] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messagesError, setMessagesError] = useState(false);
  const [hasOlderMessages, setHasOlderMessages] = useState(false);
  const [messagePage, setMessagePage] = useState(0);
  const [olderLoading, setOlderLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [draft, setDraft] = useState('');
  const [deletingChatId, setDeletingChatId] = useState<number | null>(null);
  const [newMessagesBelow, setNewMessagesBelow] = useState(0);

  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const pendingScrollHeightRef = useRef<number | null>(null);
  const shouldScrollToBottomRef = useRef(false);
  const nearBottomRef = useRef(true);
  const pendingMessageRef = useRef<string | null>(null);
  const sendTimeoutRef = useRef<number | null>(null);
  const activeChatRef = useRef<number | null>(chatId);
  const ownMessageIdsRef = useRef(new Set<number>());

  useEffect(() => {
    activeChatRef.current = chatId;
  }, [chatId]);

  const handleChatUpdated = useCallback((updatedChat: Chat) => {
    setChats((current) => {
      const exists = current.some((chat) => chat.id === updatedChat.id);
      return sortChats(
        exists
          ? current.map((chat) => (chat.id === updatedChat.id ? updatedChat : chat))
          : [updatedChat, ...current]
      );
    });
  }, []);

  const handleChatDeleted = useCallback((deletedId: number) => {
    setChats((current) => current.filter((chat) => chat.id !== deletedId));
    if (activeChatRef.current === deletedId) {
      navigate('/chats', { replace: true });
      showToast('Этот диалог был удалён.', 'info');
    }
  }, [navigate, showToast]);

  const handleNewMessage = useCallback((message: Message) => {
    const activeId = activeChatRef.current;
    if (!activeId || message.chatId !== activeId) return;

    setMessages((current) => {
      if (current.some((item) => item.id === message.id)) return current;
      const next = uniqueMessages([...current, message]).sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      );
      return next;
    });

    if (message.senderId === user?.id) {
      ownMessageIdsRef.current.add(message.id);
      if (pendingMessageRef.current === message.content || draft === message.content) {
        pendingMessageRef.current = null;
        if (sendTimeoutRef.current !== null) {
          window.clearTimeout(sendTimeoutRef.current);
          sendTimeoutRef.current = null;
        }
        setSending(false);
        setDraft((current) => (current === message.content ? '' : current));
      }
    } else {
      void chatApi.markAsRead(activeId).catch(() => {
        showToast('Не удалось отметить сообщение прочитанным.', 'error');
      });
      setChats((current) =>
        current.map((chat) => (chat.id === activeId ? { ...chat, unreadCount: 0 } : chat))
      );
    }

    if (nearBottomRef.current) {
      shouldScrollToBottomRef.current = true;
      setNewMessagesBelow(0);
    } else if (message.senderId !== user?.id) {
      setNewMessagesBelow((count) => count + 1);
    }
  }, [draft, showToast, user?.id]);

  const handleMessageDeleted = useCallback((eventChatId: number, messageId: number) => {
    if (activeChatRef.current !== eventChatId) return;
    setMessages((current) => current.filter((message) => message.id !== messageId));
  }, []);

  const handleMessagesRead = useCallback((eventChatId: number) => {
    if (activeChatRef.current !== eventChatId) return;
    void chatApi.getMessages(eventChatId, 0, PAGE_SIZE).then((page) => {
      if (activeChatRef.current !== eventChatId) return;
      const currentIds = new Set(page.content.map((message) => message.id));
      setMessages((current) =>
        current.map((message) =>
          currentIds.has(message.id)
            ? page.content.find((updated) => updated.id === message.id) || message
            : message
        )
      );
    }).catch(() => {
      showToast('Не удалось обновить статусы прочтения.', 'error');
    });
  }, [showToast]);

  const { isConnected, subscribeToChat, unsubscribeFromChat, sendMessage } = useWebSocketChat({
    userId: user?.id ?? null,
    onNewMessage: handleNewMessage,
    onMessageDeleted: handleMessageDeleted,
    onMessagesRead: handleMessagesRead,
    onChatDeleted: handleChatDeleted,
    onChatUpdated: handleChatUpdated,
  });

  useEffect(() => {
    let cancelled = false;
    setChatsLoading(true);
    setChatsError(false);
    chatApi
      .getChats()
      .then((data) => {
        if (!cancelled) setChats(sortChats(data));
      })
      .catch(() => {
        if (!cancelled) setChatsError(true);
      })
      .finally(() => {
        if (!cancelled) setChatsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (invalidChatId) {
      setMessagesError(true);
      return;
    }
    if (chatId === null) {
      setMessages([]);
      setMessagesError(false);
      setMessagesLoading(false);
      setHasOlderMessages(false);
      return;
    }

    let cancelled = false;
    setMessagesLoading(true);
    setMessagesError(false);
    setMessages([]);
    setNewMessagesBelow(0);
    nearBottomRef.current = true;
    shouldScrollToBottomRef.current = true;
    pendingMessageRef.current = null;
    if (sendTimeoutRef.current !== null) {
      window.clearTimeout(sendTimeoutRef.current);
      sendTimeoutRef.current = null;
    }
    setSending(false);

    const loadMessages = async () => {
      try {
        const page = await chatApi.getMessages(chatId, 0, PAGE_SIZE);
        if (cancelled) return;
        const chronological = [...page.content].reverse();
        setMessagePage(0);
        chronological
          .filter((message) => message.senderId === user?.id)
          .forEach((message) => ownMessageIdsRef.current.add(message.id));
        setMessages((current) =>
          uniqueMessages([...chronological, ...current]).sort(
            (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          )
        );
        setHasOlderMessages(page.number + 1 < page.totalPages);
        await chatApi.markAsRead(chatId);
        setChats((current) =>
          current.map((chat) => (chat.id === chatId ? { ...chat, unreadCount: 0 } : chat))
        );
      } catch {
        if (!cancelled) setMessagesError(true);
      } finally {
        if (!cancelled) setMessagesLoading(false);
      }
    };

    void loadMessages();
    return () => {
      cancelled = true;
    };
  }, [chatId, invalidChatId, user?.id]);

  useEffect(() => {
    if (!isConnected || chatId === null || invalidChatId) return undefined;
    subscribeToChat(chatId);
    return () => unsubscribeFromChat(chatId);
  }, [chatId, invalidChatId, isConnected, subscribeToChat, unsubscribeFromChat]);

  useEffect(() => {
    return () => {
      if (sendTimeoutRef.current !== null) window.clearTimeout(sendTimeoutRef.current);
    };
  }, []);

  useLayoutEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;
    if (pendingScrollHeightRef.current !== null) {
      const previousHeight = pendingScrollHeightRef.current;
      container.scrollTop += container.scrollHeight - previousHeight;
      pendingScrollHeightRef.current = null;
    } else if (shouldScrollToBottomRef.current) {
      container.scrollTop = container.scrollHeight;
      shouldScrollToBottomRef.current = false;
    }
  }, [messages, messagesLoading]);

  const selectedChat = useMemo(
    () => chats.find((chat) => chat.id === chatId) || null,
    [chats, chatId]
  );
  const filteredChats = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('ru');
    if (!query) return chats;
    return chats.filter((chat) =>
      [chat.participantName, chat.lastMessage]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase('ru').includes(query))
    );
  }, [chats, search]);

  const loadOlderMessages = async () => {
    if (chatId === null || olderLoading || !hasOlderMessages) return;
    if (messages.length === 0) return;

    setOlderLoading(true);
    pendingScrollHeightRef.current =
      messagesContainerRef.current?.scrollHeight ?? null;
    try {
      const nextPage = await chatApi.getMessages(chatId, messagePage + 1, PAGE_SIZE);
      const older = [...nextPage.content].reverse();
      setMessages((current) => uniqueMessages([...older, ...current]));
      setMessagePage(nextPage.number);
      setHasOlderMessages(nextPage.number + 1 < nextPage.totalPages);
    } catch {
      showToast('Не удалось загрузить более ранние сообщения.', 'error');
      pendingScrollHeightRef.current = null;
    } finally {
      setOlderLoading(false);
    }
  };

  const handleScroll = () => {
    const container = messagesContainerRef.current;
    if (!container) return;
    nearBottomRef.current =
      container.scrollHeight - container.scrollTop - container.clientHeight < 90;
    if (nearBottomRef.current) setNewMessagesBelow(0);
    if (container.scrollTop < 80 && hasOlderMessages && !olderLoading) {
      void loadOlderMessages();
    }
  };

  const submitMessage = (event?: FormEvent) => {
    event?.preventDefault();
    const content = draft.trim();
    if (!content || sending || chatId === null || !isConnected || !user) return;
    if (content.length > MAX_MESSAGE_LENGTH) {
      showToast(`Сообщение не должно превышать ${MAX_MESSAGE_LENGTH} символов.`, 'error');
      return;
    }

    const published = sendMessage(chatId, content);
    if (!published) {
      showToast('Соединение с чатом потеряно. Сообщение не отправлено.', 'error');
      return;
    }

    pendingMessageRef.current = content;
    setSending(true);
    sendTimeoutRef.current = window.setTimeout(() => {
      if (pendingMessageRef.current === content) {
        pendingMessageRef.current = null;
        setSending(false);
        showToast('Сервер не подтвердил отправку. Текст сохранён — попробуйте ещё раз.', 'error');
      }
    }, 12000);
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submitMessage();
    }
  };

  const handleDeleteChat = async (target: Chat) => {
    const confirmed = window.confirm(
      `Удалить диалог с ${target.participantName}? Сообщения будут удалены.`
    );
    if (!confirmed) return;
    setDeletingChatId(target.id);
    try {
      await chatApi.deleteChat(target.id);
      setChats((current) => current.filter((chat) => chat.id !== target.id));
      if (chatId === target.id) navigate('/chats', { replace: true });
    } catch {
      showToast('Не удалось удалить диалог.', 'error');
    } finally {
      setDeletingChatId(null);
    }
  };

  const totalUnread = chats.reduce((sum, chat) => sum + (chat.unreadCount || 0), 0);
  const groupedMessages = useMemo(() => {
    const groups: { date: string; messages: Message[] }[] = [];
    messages.forEach((message) => {
      const date = formatChatDate(message.createdAt);
      const last = groups[groups.length - 1];
      if (last?.date === date) last.messages.push(message);
      else groups.push({ date, messages: [message] });
    });
    return groups;
  }, [messages]);

  return (
    <main className={`chat-workspace${chatId !== null ? ' has-active-chat' : ''}`}>
      <header className="chat-page-heading">
        <div>
          <span className="chat-eyebrow">Личное пространство</span>
          <h1>Сообщения</h1>
          <p>Ваши диалоги с участниками HelpTree</p>
        </div>
        {totalUnread > 0 && (
          <span className="chat-total-unread">
            {totalUnread} непрочитанных
          </span>
        )}
      </header>

      <div className="chat-shell">
        <aside className="chat-sidebar" aria-label="Список диалогов">
          <div className="chat-sidebar-heading">
            <div>
              <span className="chat-eyebrow">Переписки</span>
              <h2>Диалоги <span>{chats.length}</span></h2>
            </div>
          </div>
          <label className="chat-search">
            <Search size={16} aria-hidden="true" />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Поиск по диалогам"
              aria-label="Поиск по диалогам"
            />
            {search && (
              <button type="button" onClick={() => setSearch('')} aria-label="Очистить поиск">
                <X size={15} />
              </button>
            )}
          </label>

          <div className="chat-list-scroll">
            {chatsLoading ? (
              <div className="chat-state chat-state--small">
                <span className="chat-spinner" />
                <span>Загружаем диалоги…</span>
              </div>
            ) : chatsError ? (
              <div className="chat-state chat-state--small" role="alert">
                <span>Не удалось загрузить диалоги.</span>
                <button type="button" onClick={() => window.location.reload()}>
                  <RefreshCw size={14} /> Повторить
                </button>
              </div>
            ) : chats.length === 0 ? (
              <div className="chat-state chat-state--small">
                <MessageSquareText size={23} />
                <strong>Диалогов пока нет</strong>
                <span>Откройте профиль участника, чтобы начать общение.</span>
                <Link to="/users">Найти участников <ChevronRight size={14} /></Link>
              </div>
            ) : filteredChats.length === 0 ? (
              <div className="chat-state chat-state--small">
                <Search size={22} />
                <strong>Ничего не найдено</strong>
                <span>Измените поисковый запрос.</span>
              </div>
            ) : (
              filteredChats.map((chat) => (
                <div
                  className={`chat-list-item${chat.id === chatId ? ' is-selected' : ''}${chat.unreadCount > 0 ? ' has-unread' : ''}`}
                  key={chat.id}
                >
                  <Link
                    className="chat-list-link"
                    to={`/chats/${chat.id}`}
                    aria-current={chat.id === chatId ? 'page' : undefined}
                  >
                    <Avatar
                      name={chat.participantName}
                      avatarUrl={chat.participantAvatarUrl}
                      size="large"
                    />
                    <span className="chat-list-copy">
                      <span className="chat-list-name-row">
                        <strong>{chat.participantName}</strong>
                        {chat.lastMessageAt && (
                          <time dateTime={chat.lastMessageAt}>
                            {getRelativeTime(chat.lastMessageAt)}
                          </time>
                        )}
                      </span>
                      <span className="chat-list-preview">
                        <span>{chat.lastMessage || 'Начните переписку'}</span>
                        {chat.unreadCount > 0 && (
                          <b>{chat.unreadCount > 99 ? '99+' : chat.unreadCount}</b>
                        )}
                      </span>
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="chat-delete-button"
                    onClick={() => void handleDeleteChat(chat)}
                    disabled={deletingChatId === chat.id}
                    aria-label={`Удалить диалог с ${chat.participantName}`}
                    title="Удалить диалог"
                  >
                    {deletingChatId === chat.id ? (
                      <LoaderCircle className="chat-spin" size={15} />
                    ) : (
                      <Trash2 size={15} />
                    )}
                  </button>
                </div>
              ))
            )}
          </div>
        </aside>

        <section className="chat-conversation" aria-label="Переписка">
          {chatId === null ? (
            <div className="chat-welcome">
              <span className="chat-welcome-icon"><MessageCircle size={28} /></span>
              <span className="chat-eyebrow">HelpTree</span>
              <h2>Добрые дела начинаются с разговора</h2>
              <p>Выберите диалог слева или найдите участника, чтобы начать общение.</p>
              <Link to="/users">Найти участника <ChevronRight size={15} /></Link>
            </div>
          ) : invalidChatId ? (
            <div className="chat-state">
              <span className="chat-state-icon"><MessageCircle size={25} /></span>
              <h2>Некорректная ссылка на диалог</h2>
              <p>Выберите переписку из списка доступных вам диалогов.</p>
              <Link to="/chats">К списку диалогов</Link>
            </div>
          ) : messagesError ? (
            <div className="chat-state">
              <span className="chat-state-icon"><MessageCircle size={25} /></span>
              <h2>Не удалось открыть переписку</h2>
              <p>Диалог может быть недоступен или соединение с сервером временно потеряно.</p>
              <button type="button" onClick={() => window.location.reload()}>
                <RefreshCw size={15} /> Повторить
              </button>
            </div>
          ) : (
            <>
              <header className="conversation-header">
                <Link className="conversation-back" to="/chats" aria-label="Вернуться к диалогам">
                  <ArrowLeft size={18} />
                </Link>
                {selectedChat ? (
                  <>
                    <Link
                      className="conversation-person"
                      to={`/profile/${selectedChat.participantId}`}
                    >
                      <Avatar
                        name={selectedChat.participantName}
                        avatarUrl={selectedChat.participantAvatarUrl}
                        size="medium"
                      />
                      <span>
                        <strong>{selectedChat.participantName}</strong>
                        <small>Профиль участника</small>
                      </span>
                    </Link>
                    <span className={`chat-connection${isConnected ? ' is-connected' : ''}`}>
                      <i />
                      {isConnected ? 'Сервис чата подключён' : 'Нет соединения'}
                    </span>
                  </>
                ) : (
                  <div className="conversation-person conversation-person--unknown">
                    <span className="chat-spinner" />
                    <span><strong>Загружаем диалог…</strong></span>
                  </div>
                )}
              </header>

              <div
                className="conversation-messages"
                ref={messagesContainerRef}
                onScroll={handleScroll}
                aria-live="polite"
                aria-relevant="additions text"
              >
                {messagesLoading ? (
                  <div className="chat-state">
                    <span className="chat-spinner" />
                    <span>Загружаем историю сообщений…</span>
                  </div>
                ) : messages.length === 0 ? (
                  <div className="chat-state">
                    <span className="chat-state-icon"><MessageCircle size={24} /></span>
                    <h2>Начните переписку</h2>
                    <p>Напишите первое сообщение участнику.</p>
                  </div>
                ) : (
                  <div className="message-groups">
                    {hasOlderMessages && (
                      <button
                        type="button"
                        className="load-older-button"
                        onClick={() => void loadOlderMessages()}
                        disabled={olderLoading}
                      >
                        {olderLoading ? <LoaderCircle className="chat-spin" size={14} /> : <ArrowLeft size={14} />}
                        {olderLoading ? 'Загружаем…' : 'Более ранние сообщения'}
                      </button>
                    )}
                    {groupedMessages.map((group) => (
                      <div className="message-day" key={group.date}>
                        <div className="message-day-label"><span>{group.date}</span></div>
                        {group.messages.map((message) => {
                          const own = message.senderId === user?.id;
                          return (
                            <article
                              className={`message-row${own ? ' is-own' : ''}`}
                              key={message.id}
                            >
                              {!own && (
                                <Avatar
                                  name={message.senderName}
                                  avatarUrl={message.senderAvatarUrl}
                                  size="small"
                                />
                              )}
                              <div className="message-bubble">
                                <p>{message.content}</p>
                                <span className="message-time">
                                  <time dateTime={message.createdAt}>
                                    {formatTime(message.createdAt)}
                                  </time>
                                  {own && message.isRead && ownMessageIdsRef.current.has(message.id) && (
                                    <CheckCheck size={13} aria-label="Прочитано" />
                                  )}
                                </span>
                              </div>
                            </article>
                          );
                        })}
                      </div>
                    ))}
                    <div ref={endRef} />
                  </div>
                )}
              </div>

              {newMessagesBelow > 0 && (
                <button
                  type="button"
                  className="new-messages-indicator"
                  onClick={() => {
                    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
                    setNewMessagesBelow(0);
                    nearBottomRef.current = true;
                  }}
                >
                  {newMessagesBelow} новых сообщений
                </button>
              )}

              {!isConnected && (
                <div className="chat-offline-notice" role="status">
                  <span>Связь прервана. Отправка станет доступна после восстановления соединения.</span>
                </div>
              )}

              <form className="message-composer" onSubmit={submitMessage}>
                <textarea
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={handleComposerKeyDown}
                  placeholder={isConnected ? 'Напишите сообщение…' : 'Ожидаем подключения…'}
                  aria-label="Текст сообщения"
                  maxLength={MAX_MESSAGE_LENGTH}
                  rows={1}
                  disabled={!isConnected || messagesLoading || messagesError || sending}
                />
                <div className="composer-footer">
                  <span>
                    Shift + Enter — новая строка
                    {draft.length > MAX_MESSAGE_LENGTH - 100
                      ? ` · ${draft.length}/${MAX_MESSAGE_LENGTH}`
                      : ''}
                    {sending && ' · Отправляем…'}
                  </span>
                  <button
                    type="submit"
                    disabled={!draft.trim() || sending || !isConnected || messagesLoading}
                    aria-label="Отправить сообщение"
                    title="Отправить"
                  >
                    {sending ? <LoaderCircle className="chat-spin" size={17} /> : <Send size={17} />}
                  </button>
                </div>
              </form>
            </>
          )}
        </section>
      </div>
    </main>
  );
};
