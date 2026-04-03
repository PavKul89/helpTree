import api from './axios';

export interface Stats {
  totalUsers: number;
  totalHelps: number;
  activePosts: number;
}

export const statsApi = {
  getStats: async (): Promise<Stats> => {
    const response = await api.get<Stats>('/api/stats');
    return response.data;
  },
};
