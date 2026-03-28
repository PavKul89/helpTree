import axios from 'axios';
import type { AuthResponse, LoginRequest, RegisterRequest, User, UserPublic } from '../types';

const API_URL = 'http://localhost:8080';

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

  updateProfile: async (id: number, data: { name?: string; email?: string; phone?: string; city?: string; birthDate?: string | null }): Promise<User> => {
    const response = await api.put<User>(`/api/users/${id}`, data);
    return response.data;
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
    await api.post(`/api/users/${userId}/favorites/${postId}`);
  },

  removeFavorite: async (userId: number, postId: number): Promise<void> => {
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
};
