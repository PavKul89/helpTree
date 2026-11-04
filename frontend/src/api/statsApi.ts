import api from './axios';

export interface Stats {
  totalUsers: number;
  /** Confirmed help acts (same source of truth as graph and achievements). */
  totalHelps: number;
  helpRecords: number;
  activeHelps: number;
  confirmedHelps: number;
  cancelledHelps: number;
  successfulReceipts: number;
  activePosts: number;
}

export const statsApi = {
  getStats: async (): Promise<Stats> => {
    const response = await api.get<Stats>('/api/stats');
    return response.data;
  },
};
