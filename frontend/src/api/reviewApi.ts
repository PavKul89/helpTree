import api from './axios';
import type { Review } from '../types';

export const reviewApi = {
  create: async (data: { helpId: number; rating: number; comment?: string }): Promise<Review> => {
    const response = await api.post<Review>('/api/reviews', data);
    return response.data;
  },

  getByHelp: async (helpId: number): Promise<Review[]> => {
    const response = await api.get<Review[]>(`/api/reviews/help/${helpId}`);
    return response.data;
  },

  getByUser: async (userId: number): Promise<Review[]> => {
    const response = await api.get<Review[]>(`/api/reviews/user/${userId}`);
    return response.data;
  },

  delete: async (reviewId: number): Promise<void> => {
    await api.delete(`/api/reviews/${reviewId}`);
  },
};
