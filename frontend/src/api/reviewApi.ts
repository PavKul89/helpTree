import api from './axios';
import { collectAllPages, type PageResponse } from './pagination';
import { createIdempotencyKey } from '../utils/idempotency';
import type { CreateReviewReportRequest, Report, Review } from '../types';

export const reviewApi = {
  create: async (data: { helpId: number; rating: number; comment?: string }): Promise<Review> => {
    const response = await api.post<Review>('/api/reviews', data);
    return response.data;
  },

  getByHelp: async (
    helpId: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Review>> => {
    const response = await api.get<PageResponse<Review>>(`/api/reviews/help/${helpId}`, {
      params,
    });
    return response.data;
  },

  getAllByHelp: async (helpId: number): Promise<Review[]> =>
    collectAllPages((page, size) => reviewApi.getByHelp(helpId, { page, size })),

  getByUser: async (
    userId: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Review>> => {
    const response = await api.get<PageResponse<Review>>(`/api/reviews/user/${userId}`, {
      params,
    });
    return response.data;
  },

  getAllByUser: async (userId: number): Promise<Review[]> =>
    collectAllPages((page, size) => reviewApi.getByUser(userId, { page, size })),

  delete: async (reviewId: number): Promise<void> => {
    await api.delete(`/api/reviews/${reviewId}`);
  },

  report: async (
    reviewId: number,
    data: CreateReviewReportRequest,
    idempotencyKey: string = createIdempotencyKey('review-report')
  ): Promise<Report> => {
    const response = await api.post<Report>(`/api/reviews/${reviewId}/report`, data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return response.data;
  },
};
