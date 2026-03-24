import api from './axios';
import type { Chat, Message, CreateChatRequest, CreateMessageRequest } from '../types';

interface MessagesResponse {
  content: Message[];
  totalElements: number;
  totalPages: number;
  number: number;
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

  getMessages: async (chatId: number, page = 0, size = 20): Promise<MessagesResponse> => {
    const response = await api.get<MessagesResponse>(`/api/chats/${chatId}/messages`, {
      params: { page, size },
    });
    return response.data;
  },

  sendMessage: async (chatId: number, data: CreateMessageRequest): Promise<Message> => {
    const response = await api.post<Message>(`/api/chats/${chatId}/messages`, data);
    return response.data;
  },

  markAsRead: async (chatId: number): Promise<void> => {
    await api.post(`/api/chats/${chatId}/read`);
  },

  deleteMessage: async (chatId: number, messageId: number): Promise<void> => {
    await api.delete(`/api/chats/${chatId}/messages/${messageId}`);
  },
};
