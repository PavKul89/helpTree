import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, StompSubscription } from '@stomp/stompjs';
import { API_URL } from '../api/axios';
import type { Chat, Message, WsMessageEnvelope } from '../types';

export type WsMessageType = 'NEW_MESSAGE' | 'MESSAGE_DELETED' | 'MESSAGES_READ' | 'CHAT_DELETED' | 'CHAT_UPDATED';

const WS_URL = process.env.REACT_APP_WS_URL || `${API_URL.replace(/^http/, 'ws')}/ws/chat`;

interface UseWebSocketChatOptions {
  userId: number | null;
  onNewMessage: (message: Message) => void;
  onMessageDeleted: (chatId: number, messageId: number) => void;
  onMessagesRead: (chatId: number) => void;
  onChatDeleted: (chatId: number) => void;
  onChatUpdated?: (chat: Chat) => void;
}

export function useWebSocketChat(options: UseWebSocketChatOptions) {
  const { userId } = options;
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<number, StompSubscription>>(new Map());
  const userSubscriptionRef = useRef<StompSubscription | null>(null);
  const chatIdsRef = useRef<Set<number>>(new Set());
  const handlersRef = useRef(options);

  useEffect(() => {
    handlersRef.current = options;
  });

  const handleEnvelope = useCallback((envelope: WsMessageEnvelope) => {
    const {
      onNewMessage,
      onMessageDeleted,
      onMessagesRead,
      onChatDeleted,
      onChatUpdated,
    } = handlersRef.current;

    switch (envelope.type) {
      case 'NEW_MESSAGE':
        if (envelope.message) {
          onNewMessage(envelope.message);
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

    if (userSubscriptionRef.current) return;

    userSubscriptionRef.current = client.subscribe(`/topic/user/${userId}`, (message) => {
      parseEnvelope(message.body);
    });
  }, [userId, parseEnvelope]);

  const subscribeToChat = useCallback((chatId: number) => {
    chatIdsRef.current.add(chatId);

    const client = clientRef.current;
    if (!client || !client.connected) return;

    if (subscriptionsRef.current.has(chatId)) return;

    const subscription = client.subscribe(`/topic/chat/${chatId}`, (message) => {
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

  const sendMessage = useCallback((chatId: number, content: string) => {
    const client = clientRef.current;
    if (!client || !client.connected) {
      console.warn('WebSocket не подключён, сообщение не отправлено');
      return;
    }

    client.publish({
      destination: `/app/chat/${chatId}/messages`,
      body: JSON.stringify({ content }),
    });
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
