import axios from 'axios';
import type { PageResponse } from './pagination';
import type { AuthResponse, LoginRequest, RegisterRequest, User, UserPublic, WsTicket } from '../types';
import { API_URL } from '../config';

export type ProfileVisibility = 'PUBLIC' | 'REGISTERED' | 'PRIVATE';
export type DigestFrequency = 'INSTANT' | 'DAILY' | 'WEEKLY' | 'OFF';

export interface NotificationPreferences {
  emailEnabled: boolean;
  pushEnabled: boolean;
  inAppEnabled: boolean;
  digestFrequency: DigestFrequency;
  quietHoursStart?: string | null;
  quietHoursEnd?: string | null;
  updatedAt?: string;
}

export type UpdateNotificationPreferences = Partial<
  Pick<
    NotificationPreferences,
    | 'emailEnabled'
    | 'pushEnabled'
    | 'inAppEnabled'
    | 'digestFrequency'
    | 'quietHoursStart'
    | 'quietHoursEnd'
  >
>;

export interface PrivacySettings {
  profileVisibility: ProfileVisibility;
  showCity: boolean;
  showEmail: boolean;
  showPhone: boolean;
  showOnlineStatus: boolean;
  allowDirectMessages: boolean;
  searchable: boolean;
  updatedAt?: string;
}

export type UpdatePrivacySettings = Partial<
  Pick<
    PrivacySettings,
    | 'profileVisibility'
    | 'showCity'
    | 'showEmail'
    | 'showPhone'
    | 'showOnlineStatus'
    | 'allowDirectMessages'
    | 'searchable'
  >
>;

// Block 16: user-initiated blocks. `ALL` covers every scenario; the narrower
// scopes only close chats/messages, public profile & reviews, or help replies.
export type UserBlockScope = 'ALL' | 'MESSAGES' | 'PROFILE' | 'HELP_REQUESTS';

export interface UserBlockRequest {
  scope?: UserBlockScope;
  expiresAt?: string | null;
}

export interface UserBlock {
  id: number;
  blockerId: number;
  blockedId: number;
  scope: UserBlockScope;
  createdAt: string;
  expiresAt?: string | null;
  active: boolean;
}

export interface UserBlockStatus {
  userId: number;
  blockedByMe: boolean;
  blockedMe: boolean;
  active: boolean;
  scope?: UserBlockScope | null;
  expiresAt?: string | null;
}

export interface BlockedUser {
  id: number;
  userId: number;
  name: string;
  avatarUrl?: string | null;
  scope: UserBlockScope;
  createdAt: string;
  expiresAt?: string | null;
}

export interface AccountSession {
  id: string;
  createdAt: string;
  lastUsedAt?: string;
  expiresAt: string;
  ipAddress?: string;
  userAgent?: string;
  deviceName?: string;
  current: boolean;
}

const createAuthApi = () => {
  const instance = axios.create({
    baseURL: API_URL,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  return instance;
};

const api = createAuthApi();

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/login', data);
    return response.data;
  },

  getWsTicket: async (): Promise<WsTicket> => {
    const response = await api.post<WsTicket>('/api/auth/ws-ticket');
    return response.data;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/register', data);
    return response.data;
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/api/users/me');
    return response.data;
  },

  // ------------------------------------------------------- account security
  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await api.post('/api/auth/password/change', { currentPassword, newPassword });
  },

  requestEmailChange: async (newEmail: string, password: string): Promise<void> => {
    await api.post('/api/auth/email/change', { newEmail, password });
  },

  requestEmailVerification: async (): Promise<void> => {
    await api.post('/api/auth/email/verification/request');
  },

  getSessions: async (): Promise<AccountSession[]> => {
    const response = await api.get<AccountSession[]>('/api/auth/sessions');
    return response.data;
  },

  revokeSession: async (sessionId: string): Promise<void> => {
    await api.delete(`/api/auth/sessions/${sessionId}`);
  },

  logoutAll: async (): Promise<void> => {
    await api.post('/api/auth/logout-all');
  },

  // --------------------------------------------------- account preferences
  getNotificationPreferences: async (): Promise<NotificationPreferences> => {
    const response = await api.get<NotificationPreferences>('/api/account/notifications');
    return response.data;
  },

  updateNotificationPreferences: async (
    payload: UpdateNotificationPreferences
  ): Promise<NotificationPreferences> => {
    const response = await api.put<NotificationPreferences>('/api/account/notifications', payload);
    return response.data;
  },

  getPrivacySettings: async (): Promise<PrivacySettings> => {
    const response = await api.get<PrivacySettings>('/api/account/privacy');
    return response.data;
  },

  updatePrivacySettings: async (payload: UpdatePrivacySettings): Promise<PrivacySettings> => {
    const response = await api.put<PrivacySettings>('/api/account/privacy', payload);
    return response.data;
  },

  exportAccountData: async (): Promise<Record<string, unknown>> => {
    const response = await api.get<Record<string, unknown>>('/api/account/export');
    return response.data;
  },

  anonymizeAccount: async (password: string, reason?: string): Promise<void> => {
    await api.post('/api/account/anonymize', { password, reason });
  },

  getUserById: async (id: number): Promise<UserPublic> => {
    const response = await api.get<UserPublic>(`/api/users/${id}/public`);
    return response.data;
  },

  getAllUsers: async (params?: {
    page?: number;
    size?: number;
    q?: string;
    city?: string;
    sort?: 'id' | 'name' | 'rating' | 'helpedCount' | 'createdAt';
    direction?: 'asc' | 'desc';
  }): Promise<PageResponse<UserPublic>> => {
    const response = await api.get<PageResponse<UserPublic>>('/api/users/public', { params });
    return response.data;
  },

  updateProfile: async (id: number, data: { name?: string; email?: string; phone?: string; city?: string; birthDate?: string | null; latitude?: number; longitude?: number }): Promise<User> => {
    const response = await api.put<User>(`/api/users/${id}`, data);
    return response.data;
  },

  deleteAccount: async (id: number): Promise<void> => {
    await api.delete(`/api/users/${id}`);
  },

  uploadAvatar: async (userId: number, file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axios.post(`${API_URL}/api/users/${userId}/avatar`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        Authorization: `Bearer ${localStorage.getItem('accessToken') || localStorage.getItem('token')}`,
      },
    });
    return response.data.url;
  },

  addFavorite: async (userId: number, postId: number): Promise<void> => {
    console.log('API addFavorite:', userId, postId);
    const response = await api.post(`/api/users/${userId}/favorites/${postId}`);
    console.log('API addFavorite done:', response);
  },

  removeFavorite: async (userId: number, postId: number): Promise<void> => {
    console.log('API removeFavorite:', userId, postId);
    await api.delete(`/api/users/${userId}/favorites/${postId}`);
  },

  getFavorites: async (userId: number): Promise<number[]> => {
    const response = await api.get<number[]>(`/api/users/${userId}/favorites`);
    return response.data;
  },

  isFavorite: async (userId: number, postId: number): Promise<boolean> => {
    const response = await api.get<{ isFavorite: boolean }>(`/api/users/${userId}/favorites/${postId}`);
    return response.data.isFavorite;
  },

  // ------------------------------------------------------- user blocks (16)
  blockUser: async (id: number, payload?: UserBlockRequest): Promise<UserBlock> => {
    const response = await api.post<UserBlock>(`/api/users/${id}/block`, payload ?? {});
    return response.data;
  },

  unblockUser: async (id: number): Promise<void> => {
    await api.delete(`/api/users/${id}/block`);
  },

  getMyBlockedUsers: async (): Promise<BlockedUser[]> => {
    const response = await api.get<BlockedUser[]>('/api/users/me/blocked');
    return response.data;
  },

  getBlockStatus: async (id: number): Promise<UserBlockStatus> => {
    const response = await api.get<UserBlockStatus>(`/api/users/${id}/block-status`);
    return response.data;
  },
};
