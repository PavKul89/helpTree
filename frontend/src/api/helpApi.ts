import api from './axios';
import type { Help, HelpRequest, HelpGraph } from '../types';

export interface HelpStats {
  totalHelps: number;
  byMonth: Record<string, number>;
  byCategory: Record<string, number>;
  topHelpers: Array<{
    userId: number;
    name: string;
    helpCount: number;
  }>;
}

export const helpApi = {
  acceptHelp: async (data: HelpRequest): Promise<Help> => {
    const response = await api.post<Help>('/api/helps/accept', data);
    return response.data;
  },

  completeHelp: async (helpId: number): Promise<Help> => {
    const response = await api.post<Help>(`/api/helps/${helpId}/complete`);
    return response.data;
  },

  confirmHelp: async (helpId: number): Promise<Help> => {
    const response = await api.post<Help>(`/api/helps/${helpId}/confirm`);
    return response.data;
  },

  cancelHelp: async (helpId: number): Promise<Help> => {
    const response = await api.post<Help>(`/api/helps/${helpId}/cancel`);
    return response.data;
  },

  getHelpsByHelper: async (helperId: number): Promise<Help[]> => {
    const response = await api.get<Help[]>(`/api/helps/helper/${helperId}`);
    return response.data;
  },

  getHelpsByReceiver: async (receiverId: number): Promise<Help[]> => {
    const response = await api.get<Help[]>(`/api/helps/receiver/${receiverId}`);
    return response.data;
  },

  getHelpsByPost: async (postId: number): Promise<Help[]> => {
    const response = await api.get<Help[]>(`/api/helps/post/${postId}`);
    return response.data;
  },

  getHelpGraph: async (userId?: number): Promise<HelpGraph> => {
    const params = userId ? `?userId=${userId}` : '';
    const response = await api.get<HelpGraph>(`/api/helps/graph${params}`);
    return response.data;
  },

  getHelpStats: async (): Promise<HelpStats> => {
    const response = await api.get<HelpStats>('/api/helps/stats');
    return response.data;
  },
};
