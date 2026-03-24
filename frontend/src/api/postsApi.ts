import api from './axios';
import type { Post, CreatePostRequest, Comment, CreateCommentRequest } from '../types';

interface PostsResponse {
  content: Post[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const postsApi = {
  getAll: async (params?: { page?: number; size?: number; status?: string; title?: string; authorName?: string; category?: string }): Promise<PostsResponse> => {
    const response = await api.get<PostsResponse>('/api/posts', { params });
    return response.data;
  },

  getByUser: async (userId: number): Promise<Post[]> => {
    const response = await api.get<Post[]>(`/api/posts/user/${userId}`);
    return response.data;
  },

  getById: async (id: number): Promise<Post> => {
    const response = await api.get<Post>(`/api/posts/${id}`);
    return response.data;
  },

  create: async (data: CreatePostRequest): Promise<Post> => {
    const response = await api.post<Post>('/api/posts', data);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/api/posts/${id}`);
  },

  update: async (id: number, data: { title?: string; description?: string; status?: string; imageUrls?: string[] }): Promise<Post> => {
    const response = await api.put<Post>(`/api/posts/${id}`, data);
    return response.data;
  },

  getComments: async (postId: number): Promise<Comment[]> => {
    const response = await api.get<Comment[]>(`/api/posts/${postId}/comments`);
    return response.data;
  },

  addComment: async (postId: number, data: CreateCommentRequest): Promise<Comment> => {
    const response = await api.post<Comment>(`/api/posts/${postId}/comments`, data);
    return response.data;
  },

  deleteComment: async (postId: number, commentId: number): Promise<void> => {
    await api.delete(`/api/posts/${postId}/comments/${commentId}`);
  },
};
