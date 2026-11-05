import api from './axios';
import { collectAllPages, type PageResponse } from './pagination';
import type { Help, HelpRequest, HelpGraph } from '../types';

export interface HelpStats {
  /** Confirmed help acts: the same set used by the graph and achievements. */
  totalHelps: number;
  /** Every non-deleted help record, independent of status. */
  totalHelpRecords: number;
  /** ACCEPTED or COMPLETED help records. */
  activeHelps: number;
  cancelledHelps: number;
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

  getHelpsByHelper: async (
    helperId: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Help>> => {
    const response = await api.get<PageResponse<Help>>(`/api/helps/helper/${helperId}`, {
      params,
    });
    return response.data;
  },

  getAllHelpsByHelper: async (helperId: number): Promise<Help[]> =>
    collectAllPages((page, size) => helpApi.getHelpsByHelper(helperId, { page, size })),

  getHelpsByReceiver: async (
    receiverId: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Help>> => {
    const response = await api.get<PageResponse<Help>>(`/api/helps/receiver/${receiverId}`, {
      params,
    });
    return response.data;
  },

  getAllHelpsByReceiver: async (receiverId: number): Promise<Help[]> =>
    collectAllPages((page, size) => helpApi.getHelpsByReceiver(receiverId, { page, size })),

  getHelpsByPost: async (
    postId: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Help>> => {
    const response = await api.get<PageResponse<Help>>(`/api/helps/post/${postId}`, {
      params,
    });
    return response.data;
  },

  getAllHelpsByPost: async (postId: number): Promise<Help[]> =>
    collectAllPages((page, size) => helpApi.getHelpsByPost(postId, { page, size })),

  getHelpGraph: async (
    userId?: number,
    options?: { depth?: number; maxEdges?: number }
  ): Promise<HelpGraph> => {
    const params: Record<string, number> = {};
    if (userId) params.userId = userId;
    if (options?.depth !== undefined) params.depth = options.depth;
    if (options?.maxEdges !== undefined) params.maxEdges = options.maxEdges;
    const response = await api.get<HelpGraph>('/api/helps/graph', { params });
    return response.data;
  },

  getHelpStats: async (): Promise<HelpStats> => {
    const response = await api.get<HelpStats>('/api/helps/stats');
    return response.data;
  },
};
