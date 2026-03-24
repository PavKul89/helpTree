import api from './axios';
import type { AuthResponse, LoginRequest, RegisterRequest, User, UserPublic } from '../types';

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/login', data);
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

  getUserById: async (id: number): Promise<UserPublic> => {
    console.log('Fetching user:', `/api/users/${id}/public`);
    const response = await api.get<UserPublic>(`/api/users/${id}/public`);
    return response.data;
  },

  getAllUsers: async (): Promise<UserPublic[]> => {
    const response = await api.get<UserPublic[]>('/api/users/public');
    return response.data;
  },

  updateProfile: async (id: number, data: { name?: string; email?: string; phone?: string; city?: string }): Promise<User> => {
    const response = await api.put<User>(`/api/users/${id}`, data);
    return response.data;
  },
};
