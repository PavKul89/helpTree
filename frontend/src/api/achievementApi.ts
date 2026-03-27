import api from './axios';

export interface Achievement {
  id?: number;
  type: string;
  emoji: string;
  name: string;
  description: string;
  rarity: string;
  earnedAt?: string;
  earned?: boolean;
  currentProgress?: number;
  targetValue?: number;
  isEarned?: boolean;
}

export const achievementApi = {
  getUserAchievements: async (userId: number): Promise<Achievement[]> => {
    const response = await api.get(`/api/users/${userId}/achievements`);
    return response.data;
  },
  getAllAchievements: async (userId?: number): Promise<Achievement[]> => {
    const url = userId ? `/api/achievements?userId=${userId}` : '/api/achievements';
    const response = await api.get(url);
    return response.data;
  },
};
