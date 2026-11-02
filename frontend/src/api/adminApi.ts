import api from './axios';
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
  totalHelps: number;
  activePosts: number;
}

export interface AdminHelpStats {
  totalHelps: number;
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
  totalHelps: number;
  totalReceivedHelps: number;
  successRate: number;
}

export interface AdminAddCoinsResponse {
  success: boolean;
  newBalance: number;
  addedAmount: number;
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
  getUsers: async (): Promise<AdminUser[]> => {
    const response = await api.get<AdminUser[]>('/api/users');
    return response.data;
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

  addCoins: async (id: number, amount: number, description?: string): Promise<AdminAddCoinsResponse> => {
    const response = await api.post<AdminAddCoinsResponse>(`/api/users/${id}/wallet/admin-add`, null, {
      params: { amount, description },
    });
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

  getUserPosts: async (id: number): Promise<Post[]> => {
    const response = await api.get<Post[]>(`/api/posts/user/${id}`);
    return response.data;
  },

  getUserAchievements: async (id: number): Promise<Achievement[]> => {
    const response = await api.get<Achievement[]>(`/api/users/${id}/achievements`);
    return response.data;
  },

  getUserActivities: async (id: number, limit: number = 50): Promise<Activity[]> => {
    const response = await api.get<Activity[]>(`/api/users/${id}/activities`, { params: { limit } });
    return response.data;
  },

  getUserReviews: async (id: number): Promise<Review[]> => {
    const response = await api.get<Review[]>(`/api/reviews/user/${id}`);
    return response.data;
  },

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
  deleteReview: async (id: number): Promise<void> => {
    await api.delete(`/api/reviews/${id}`);
  },
};
