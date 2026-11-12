import api from './axios';
import type { PageResponse } from './pagination';
import type { Chat, Message, CreateChatRequest } from '../types';

export interface MessagesCursorResponse {
  items: Message[];
  nextCursor: number | null;
  hasMore: boolean;
}

export const chatApi = {
  getChats: async (): Promise<Chat[]> => {
    const response = await api.get<Chat[]>('/api/chats');
    return response.data;
  },

  createChat: async (data: CreateChatRequest): Promise<Chat> => {
    const response = await api.post<Chat>('/api/chats', data);
    return response.data;
  },

  deleteChat: async (chatId: number): Promise<void> => {
    await api.delete(`/api/chats/${chatId}`);
  },

  getMessages: async (chatId: number, page = 0, size = 20): Promise<PageResponse<Message>> => {
    const response = await api.get<PageResponse<Message>>(`/api/chats/${chatId}/messages`, {
      params: { page, size },
    });
    return response.data;
  },

  getMessagesCursor: async (
    chatId: number,
    beforeId?: number,
    limit = 20
  ): Promise<MessagesCursorResponse> => {
    const response = await api.get<MessagesCursorResponse>(`/api/chats/${chatId}/messages/cursor`, {
      params: { beforeId, limit },
    });
    return response.data;
  },

  markAsRead: async (chatId: number): Promise<void> => {
    await api.post(`/api/chats/${chatId}/read`);
  },

  deleteMessage: async (chatId: number, messageId: number): Promise<void> => {
    await api.delete(`/api/chats/${chatId}/messages/${messageId}`);
  },

  /** Edits the sender's own message inside the server-owned edit window. */
  editMessage: async (chatId: number, messageId: number, content: string): Promise<Message> => {
    const response = await api.patch<Message>(`/api/chats/${chatId}/messages/${messageId}`, {
      content,
    });
    return response.data;
  },
};
