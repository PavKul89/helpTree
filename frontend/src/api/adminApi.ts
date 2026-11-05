import api from './axios';
import { createIdempotencyKey } from '../utils/idempotency';
import { collectAllPages } from './pagination';
import type { Post, Review } from '../types';
import type { Achievement } from './achievementApi';
import type { Activity } from './activityApi';
import type { CoinTransactionDto, PageResponse, WalletDto } from './walletApi';

/**
 * Administrative API client.
 *
 * Every request below maps 1:1 to an existing backend endpoint that
 * authorises the ADMIN role. Nothing here invents a capability the
 * service does not expose.
 */

export type AdminUserRole = 'USER' | 'ADMIN';
export type AdminUserStatus = 'NEWBIE' | 'HELPER' | 'ACTIVE' | 'DEBTOR';
export type AdminPostStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface AdminUser {
  id: number;
  name: string;
  email: string;
  phone?: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  helpedCount?: number;
  debtCount?: number;
  rating: number;
  role: AdminUserRole;
  status: AdminUserStatus;
  createdAt: string;
  avatarUrl?: string;
  birthDate?: string;
  blockedAt?: string;
  blockedUntil?: string;
  debtStartedAt?: string;
  helpCoins?: number;
  vipUntil?: string;
  nicknameColor?: string;
}

export interface AdminStats {
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

export interface AdminHelpStats {
  /** Confirmed help acts: the same set used by the graph and achievements. */
  totalHelps: number;
  totalHelpRecords: number;
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

export interface CreateUserPayload {
  name: string;
  email: string;
  password: string;
  phone: string;
  city: string;
}

export interface UpdateUserPayload {
  name?: string;
  email?: string;
  phone?: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  birthDate?: string;
}

export interface RatingResponse {
  userId: number;
  userName: string;
  overallRating: number;
  ratingComponents: Record<string, number>;
  level: string;
  calculatedAt: string;
  /** Confirmed help acts given by this user. */
  totalHelps: number;
  /** Every non-deleted help record where this user was the helper. */
  totalHelpRecords: number;
  /** ACCEPTED or COMPLETED help records given by this user. */
  activeHelps: number;
  cancelledHelps: number;
  totalReceivedHelps: number;
  reviewCount: number;
  averageReviewRating?: number;
  successRate: number;
}

export interface AdminReviewsQuery {
  page?: number;
  size?: number;
  userId?: number;
  rating?: number;
  /** ISO date-time, inclusive lower bound on review creation. */
  from?: string;
  /** ISO date-time, inclusive upper bound on review creation. */
  to?: string;
}

export interface AdminAddCoinsResponse {
  success: boolean;
  newBalance: number;
  addedAmount: number;
  applied: boolean;
}

export interface AdminPostsQuery {
  page?: number;
  size?: number;
  userId?: number;
  status?: AdminPostStatus;
  title?: string;
  authorName?: string;
  category?: string;
  city?: string;
  sort?: string;
}

export interface AdminUsersQuery {
  page?: number;
  size?: number;
  q?: string;
  role?: AdminUserRole;
  status?: AdminUserStatus;
  blocked?: boolean;
  sort?: 'id' | 'name' | 'email' | 'rating' | 'helpedCount' | 'createdAt';
  direction?: 'asc' | 'desc';
}

/**
 * Server-side aggregates for the dashboard. No endpoint returns the whole
 * user table any more, so counts come from `totalElements` of filtered pages.
 */
export interface AdminUserSummary {
  total: number;
  admins: number;
  blocked: number;
  statusCounts: Record<AdminUserStatus, number>;
}

const getPostStatusCount = async (status: AdminPostStatus): Promise<number> => {
  const response = await api.get<PageResponse<Post>>('/api/posts', {
    params: { status, page: 0, size: 1 },
  });
  return response.data.totalElements;
};

export const adminApi = {
  // ---------------------------------------------------------------- overview
  getStats: async (): Promise<AdminStats> => {
    const response = await api.get<AdminStats>('/api/stats');
    return response.data;
  },

  getHelpStats: async (): Promise<AdminHelpStats> => {
    const response = await api.get<AdminHelpStats>('/api/helps/stats');
    return response.data;
  },

  getPostStatusCounts: async (): Promise<Record<AdminPostStatus, number>> => {
    const statuses: AdminPostStatus[] = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
    const counts = await Promise.all(statuses.map((status) => getPostStatusCount(status)));
    return statuses.reduce(
      (acc, status, index) => ({ ...acc, [status]: counts[index] }),
      {} as Record<AdminPostStatus, number>
    );
  },

  // ------------------------------------------------------------------- users
  getUsers: async (query: AdminUsersQuery = {}): Promise<PageResponse<AdminUser>> => {
    const response = await api.get<PageResponse<AdminUser>>('/api/users', { params: query });
    return response.data;
  },

  getUserSummary: async (): Promise<AdminUserSummary> => {
    const count = async (query: AdminUsersQuery): Promise<number> => {
      const page = await adminApi.getUsers({ ...query, page: 0, size: 1 });
      return page.totalElements;
    };

    const [total, admins, blocked, newbie, helper, active, debtor] = await Promise.all([
      count({}),
      count({ role: 'ADMIN' }),
      count({ blocked: true }),
      count({ status: 'NEWBIE' }),
      count({ status: 'HELPER' }),
      count({ status: 'ACTIVE' }),
      count({ status: 'DEBTOR' }),
    ]);

    return {
      total,
      admins,
      blocked,
      statusCounts: { NEWBIE: newbie, HELPER: helper, ACTIVE: active, DEBTOR: debtor },
    };
  },

  getUser: async (id: number): Promise<AdminUser> => {
    const response = await api.get<AdminUser>(`/api/users/${id}`);
    return response.data;
  },

  createUser: async (payload: CreateUserPayload): Promise<AdminUser> => {
    const response = await api.post<AdminUser>('/api/users', payload);
    return response.data;
  },

  updateUser: async (id: number, payload: UpdateUserPayload): Promise<AdminUser> => {
    const response = await api.put<AdminUser>(`/api/users/${id}`, payload);
    return response.data;
  },

  deleteUser: async (id: number): Promise<void> => {
    await api.delete(`/api/users/${id}`);
  },

  updateUserRating: async (id: number, rating: number): Promise<void> => {
    await api.put(`/api/users/${id}/rating`, null, { params: { rating } });
  },

  recalculateRating: async (id: number): Promise<RatingResponse> => {
    const response = await api.post<RatingResponse>(`/api/ratings/user/${id}/recalculate`);
    return response.data;
  },

  addCoins: async (
    id: number,
    amount: number,
    reason: string,
    idempotencyKey: string = createIdempotencyKey('admin')
  ): Promise<AdminAddCoinsResponse> => {
    const response = await api.post<AdminAddCoinsResponse>(
      `/api/users/${id}/wallet/admin-add`,
      null,
      {
        params: { amount, reason },
        headers: { 'Idempotency-Key': idempotencyKey },
      }
    );
    return response.data;
  },

  getWallet: async (id: number): Promise<WalletDto> => {
    const response = await api.get<WalletDto>(`/api/users/${id}/wallet`);
    return response.data;
  },

  getWalletTransactions: async (
    id: number,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<CoinTransactionDto>> => {
    const response = await api.get<PageResponse<CoinTransactionDto>>(`/api/users/${id}/wallet/transactions`, {
      params: { page, size },
    });
    return response.data;
  },

  getUserPosts: async (
    id: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Post>> => {
    const response = await api.get<PageResponse<Post>>(`/api/posts/user/${id}`, { params });
    return response.data;
  },

  getAllUserPosts: async (id: number): Promise<Post[]> =>
    collectAllPages((page, size) => adminApi.getUserPosts(id, { page, size })),

  getUserAchievements: async (id: number): Promise<Achievement[]> => {
    const response = await api.get<Achievement[]>(`/api/users/${id}/achievements`);
    return response.data;
  },

  getUserActivities: async (id: number, limit: number = 50): Promise<Activity[]> => {
    const response = await api.get<Activity[]>(`/api/users/${id}/activities`, { params: { limit } });
    return response.data;
  },

  getUserReviews: async (
    id: number,
    params?: { page?: number; size?: number }
  ): Promise<PageResponse<Review>> => {
    const response = await api.get<PageResponse<Review>>(`/api/reviews/user/${id}`, { params });
    return response.data;
  },

  getAllUserReviews: async (id: number): Promise<Review[]> =>
    collectAllPages((page, size) => adminApi.getUserReviews(id, { page, size })),

  // ------------------------------------------------------------------- posts
  getPosts: async (params: AdminPostsQuery, signal?: AbortSignal): Promise<PageResponse<Post>> => {
    const response = await api.get<PageResponse<Post>>('/api/posts', { params, signal });
    return response.data;
  },

  getPost: async (id: number): Promise<Post> => {
    const response = await api.get<Post>(`/api/posts/${id}`);
    return response.data;
  },

  deletePost: async (id: number): Promise<void> => {
    await api.delete(`/api/posts/${id}`);
  },

  // ----------------------------------------------------------------- reviews
  getAdminReviews: async (
    params: AdminReviewsQuery,
    signal?: AbortSignal
  ): Promise<PageResponse<Review>> => {
    const response = await api.get<PageResponse<Review>>('/api/admin/reviews', {
      params,
      signal,
    });
    return response.data;
  },

  deleteReview: async (id: number): Promise<void> => {
    await api.delete(`/api/reviews/${id}`);
  },
};
