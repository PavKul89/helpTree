import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, StompSubscription } from '@stomp/stompjs';
import type { Chat, Message, WsMessageEnvelope } from '../types';

export type WsMessageType = 'NEW_MESSAGE' | 'MESSAGE_DELETED' | 'MESSAGES_READ' | 'CHAT_DELETED' | 'CHAT_UPDATED';

interface UseWebSocketChatOptions {
  userId: number | null;
  onNewMessage: (message: Message) => void;
  onMessageDeleted: (chatId: number, messageId: number) => void;
  onMessagesRead: (chatId: number) => void;
  onChatDeleted: (chatId: number) => void;
  onChatUpdated?: (chat: Chat) => void;
}

export function useWebSocketChat({
  userId,
  onNewMessage,
  onMessageDeleted,
  onMessagesRead,
  onChatDeleted,
  onChatUpdated,
}: UseWebSocketChatOptions) {
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<number, StompSubscription>>(new Map());
  const userSubscriptionRef = useRef<StompSubscription | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const retryCountRef = useRef(0);
  const maxRetries = 10;

  const connect = useCallback(() => {
    if (!userId) return;

    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!token) return;

    const client = new Client({
      brokerURL: 'ws://localhost:8081/ws/chat',
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setIsConnected(true);
        retryCountRef.current = 0;
      },
      onDisconnect: () => {
        setIsConnected(false);
        scheduleReconnect();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
        setIsConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [userId]);

  const scheduleReconnect = useCallback(() => {
    if (retryCountRef.current >= maxRetries) return;

    const delay = Math.min(1000 * Math.pow(2, retryCountRef.current), 30000);
    retryCountRef.current++;

    reconnectTimeoutRef.current = window.setTimeout(() => {
      connect();
      resubscribeAll();
    }, delay);
  }, [connect]);

  const subscribeToChat = useCallback((chatId: number) => {
    const client = clientRef.current;
    if (!client || !client.connected) return;

    if (subscriptionsRef.current.has(chatId)) return;

    const subscription = client.subscribe(`/topic/chat/${chatId}`, (message) => {
      const envelope: WsMessageEnvelope = JSON.parse(message.body);
      handleEnvelope(envelope);
    });

    subscriptionsRef.current.set(chatId, subscription);
  }, []);

  const unsubscribeFromChat = useCallback((chatId: number) => {
    const subscription = subscriptionsRef.current.get(chatId);
    if (subscription) {
      subscription.unsubscribe();
      subscriptionsRef.current.delete(chatId);
    }
  }, []);

  const subscribeToUser = useCallback(() => {
    const client = clientRef.current;
    if (!client || !client.connected || !userId) return;

    if (userSubscriptionRef.current) return;

    const subscription = client.subscribe(`/topic/user/${userId}`, (message) => {
      const envelope: WsMessageEnvelope = JSON.parse(message.body);
      handleEnvelope(envelope);
    });

    userSubscriptionRef.current = subscription;
  }, [userId]);

  const handleEnvelope = useCallback((envelope: WsMessageEnvelope) => {
    switch (envelope.type) {
      case 'NEW_MESSAGE':
        if (envelope.message) {
          onNewMessage(envelope.message as Message);
        }
        break;
      case 'MESSAGE_DELETED':
        if (envelope.chatId && envelope.message?.id) {
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
          onChatUpdated(envelope.chat as Chat);
        }
        break;
    }
  }, [onNewMessage, onMessageDeleted, onMessagesRead, onChatDeleted, onChatUpdated]);

  const resubscribeAll = useCallback(() => {
    const chatIds = Array.from(subscriptionsRef.current.keys());
    chatIds.forEach((chatId) => {
      subscribeToChat(chatId);
    });
    subscribeToUser();
  }, [subscribeToChat, subscribeToUser]);

  const sendMessage = useCallback((chatId: number, content: string) => {
    const client = clientRef.current;
    if (!client || !client.connected) return;

    client.publish({
      destination: `/app/chat/${chatId}/messages`,
      body: JSON.stringify({ content }),
    });
  }, []);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    subscriptionsRef.current.forEach((sub) => sub.unsubscribe());
    subscriptionsRef.current.clear();

    if (userSubscriptionRef.current) {
      userSubscriptionRef.current.unsubscribe();
      userSubscriptionRef.current = null;
    }

    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }

    setIsConnected(false);
  }, []);

  useEffect(() => {
    if (userId) {
      connect();
      subscribeToUser();
    }

    return () => {
      disconnect();
    };
  }, [userId]);

  return {
    isConnected,
    subscribeToChat,
    unsubscribeFromChat,
    sendMessage,
    disconnect,
  };
}
