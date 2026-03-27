import api from './axios';

export interface Activity {
  type: string;
  typeLabel: string;
  emoji: string;
  title: string;
  description?: string;
  timestamp: string;
  relatedUserId?: number;
  relatedUserName?: string;
  relatedPostId?: number;
  relatedPostTitle?: string;
  category?: string;
  status?: string;
}

export const activityApi = {
  getUserActivities: async (userId: number, limit: number = 50): Promise<Activity[]> => {
    const response = await api.get(`/api/users/${userId}/activities?limit=${limit}`);
    return response.data;
  },
  getAllActivities: async (userId?: number, limit: number = 50): Promise<Activity[]> => {
    const url = userId ? `/api/activities?userId=${userId}&limit=${limit}` : `/api/activities?limit=${limit}`;
    const response = await api.get(url);
    return response.data;
  },
};
