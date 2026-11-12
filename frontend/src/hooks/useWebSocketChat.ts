import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, StompSubscription } from '@stomp/stompjs';
import { WS_URL } from '../config';
import { authApi } from '../api/authApi';
import type { Chat, Message, Notification, WsMessageEnvelope } from '../types';

export type WsMessageType =
  | 'NEW_MESSAGE'
  | 'MESSAGE_UPDATED'
  | 'MESSAGE_DELETED'
  | 'MESSAGES_READ'
  | 'CHAT_DELETED'
  | 'CHAT_UPDATED'
  | 'NOTIFICATION'
  | 'ERROR';

interface UseWebSocketChatOptions {
  userId: number | null;
  onNewMessage: (message: Message) => void;
  onMessageUpdated: (message: Message) => void;
  onMessageDeleted: (chatId: number, messageId: number) => void;
  onMessagesRead: (chatId: number) => void;
  onChatDeleted: (chatId: number) => void;
  onChatUpdated?: (chat: Chat) => void;
  onNotification?: (notification: Notification, unreadCount: number) => void;
  onError?: (message: string) => void;
}

export function useWebSocketChat(options: UseWebSocketChatOptions) {
  const { userId } = options;
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<number, StompSubscription>>(new Map());
  const userSubscriptionRef = useRef<StompSubscription | null>(null);
  const notificationSubscriptionRef = useRef<StompSubscription | null>(null);
  const errorSubscriptionRef = useRef<StompSubscription | null>(null);
  const chatIdsRef = useRef<Set<number>>(new Set());
  const handlersRef = useRef(options);

  useEffect(() => {
    handlersRef.current = options;
  });

  const handleEnvelope = useCallback((envelope: WsMessageEnvelope) => {
    const {
      onNewMessage,
      onMessageUpdated,
      onMessageDeleted,
      onMessagesRead,
      onChatDeleted,
      onChatUpdated,
      onNotification,
      onError,
    } = handlersRef.current;

    switch (envelope.type) {
      case 'NEW_MESSAGE':
        if (envelope.message) {
          onNewMessage(envelope.message);
        }
        break;
      case 'MESSAGE_UPDATED':
        if (envelope.message) {
          onMessageUpdated(envelope.message);
        }
        break;
      case 'MESSAGE_DELETED':
        if (envelope.chatId && envelope.message) {
          onMessageDeleted(envelope.chatId, envelope.message.id);
        }
        break;
      case 'MESSAGES_READ':
        if (envelope.chatId) {
          onMessagesRead(envelope.chatId);
        }
        break;
      case 'CHAT_DELETED':
        if (envelope.chatId) {
          onChatDeleted(envelope.chatId);
        }
        break;
      case 'CHAT_UPDATED':
        if (envelope.chat && onChatUpdated) {
          onChatUpdated(envelope.chat);
        }
        break;
      case 'NOTIFICATION':
        if (envelope.notification && onNotification) {
          onNotification(envelope.notification, envelope.unreadCount ?? 0);
        }
        break;
      case 'ERROR':
        if (envelope.error) {
          if (onError) {
            onError(envelope.error);
          } else {
            console.warn('WebSocket:', envelope.error);
          }
        }
        break;
    }
  }, []);

  const parseEnvelope = useCallback((body: string) => {
    try {
      handleEnvelope(JSON.parse(body) as WsMessageEnvelope);
    } catch (err) {
      console.error('Не удалось разобрать сообщение WebSocket', err);
    }
  }, [handleEnvelope]);

  const subscribeToUser = useCallback(() => {
    const client = clientRef.current;
    if (!client || !client.connected || !userId) return;

    if (!userSubscriptionRef.current) {
      // Server-side user destination: each session gets only its own updates.
      userSubscriptionRef.current = client.subscribe('/user/queue/chat-updates', (message) => {
        parseEnvelope(message.body);
      });
    }

    if (!notificationSubscriptionRef.current) {
      // The notification queue is separate from chat updates: it is the only
      // realtime transport for the centralized notification domain.
      notificationSubscriptionRef.current = client.subscribe(
        '/user/queue/notifications',
        (message) => {
          parseEnvelope(message.body);
        }
      );
    }

    if (!errorSubscriptionRef.current) {
      errorSubscriptionRef.current = client.subscribe('/user/queue/errors', (message) => {
        parseEnvelope(message.body);
      });
    }
  }, [userId, parseEnvelope]);

  const subscribeToChat = useCallback((chatId: number) => {
    chatIdsRef.current.add(chatId);

    const client = clientRef.current;
    if (!client || !client.connected) return;

    if (subscriptionsRef.current.has(chatId)) return;

    // A private user destination instead of the guessable /topic/chat/{id}.
    const subscription = client.subscribe(`/user/queue/chat/${chatId}`, (message) => {
      parseEnvelope(message.body);
    });

    subscriptionsRef.current.set(chatId, subscription);
  }, [parseEnvelope]);

  const unsubscribeFromChat = useCallback((chatId: number) => {
    chatIdsRef.current.delete(chatId);

    const subscription = subscriptionsRef.current.get(chatId);
    if (subscription) {
      try {
        subscription.unsubscribe();
      } catch (err) {
        console.warn('Не удалось отписаться от чата', chatId, err);
      }
      subscriptionsRef.current.delete(chatId);
    }
  }, []);

  const clearSubscriptions = useCallback(() => {
    subscriptionsRef.current.forEach((subscription) => {
      try {
        subscription.unsubscribe();
      } catch {
        // сокет уже закрыт — игнорируем
      }
    });
    subscriptionsRef.current.clear();

    if (userSubscriptionRef.current) {
      try {
        userSubscriptionRef.current.unsubscribe();
      } catch {
        // сокет уже закрыт — игнорируем
      }
      userSubscriptionRef.current = null;
    }

    if (errorSubscriptionRef.current) {
      try {
        errorSubscriptionRef.current.unsubscribe();
      } catch {
        // сокет уже закрыт — игнорируем
      }
      errorSubscriptionRef.current = null;
    }

    if (notificationSubscriptionRef.current) {
      try {
        notificationSubscriptionRef.current.unsubscribe();
      } catch {
        // сокет уже закрыт — игнорируем
      }
      notificationSubscriptionRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (!userId) return;
    if (clientRef.current?.active) return;

    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!token) return;

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      // The HTTP upgrade needs a short-lived ticket. Fetch a fresh one for every
      // connection attempt, including automatic reconnects.
      beforeConnect: async () => {
        try {
          const wsTicket = await authApi.getWsTicket();
          const separator = WS_URL.includes('?') ? '&' : '?';
          client.brokerURL = `${WS_URL}${separator}ticket=${encodeURIComponent(wsTicket.ticket)}`;
        } catch (error) {
          // Rejecting here aborts the STOMP connect loop and surfaces as an
          // unhandled promise rejection (the dev error overlay). The ticket
          // endpoint is briefly unavailable while the backend restarts, so keep
          // the loop alive with a ticket-less handshake: the server rejects it
          // and the normal reconnectDelay schedules the next attempt.
          console.warn('Не удалось получить тикет WebSocket, повтор подключения позже', error);
          client.brokerURL = WS_URL;
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setIsConnected(true);
        subscribeToUser();
        chatIdsRef.current.forEach((chatId) => subscribeToChat(chatId));
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onWebSocketClose: () => {
        setIsConnected(false);
        clearSubscriptions();
      },
      onWebSocketError: (event) => {
        console.warn('Ошибка WebSocket', event);
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'], frame.body);
        setIsConnected(false);
      },
    });

    clientRef.current = client;
    client.activate();
  }, [userId, subscribeToUser, subscribeToChat, clearSubscriptions]);

  const disconnect = useCallback(() => {
    clearSubscriptions();

    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }

    setIsConnected(false);
  }, [clearSubscriptions]);

  const sendMessage = useCallback((chatId: number, content: string): boolean => {
    const client = clientRef.current;
    if (!client || !client.connected) {
      console.warn('WebSocket не подключён, сообщение не отправлено');
      return false;
    }

    try {
      client.publish({
        destination: `/app/chat/${chatId}/messages`,
        body: JSON.stringify({ content }),
      });
      return true;
    } catch (error) {
      console.error('Не удалось передать сообщение через WebSocket', error);
      return false;
    }
  }, []);

  useEffect(() => {
    if (!userId) return;

    connect();

    return () => {
      disconnect();
    };
  }, [userId, connect, disconnect]);

  return {
    isConnected,
    subscribeToChat,
    unsubscribeFromChat,
    sendMessage,
    disconnect,
  };
}
