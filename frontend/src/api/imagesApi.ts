import api from './axios';

export const imagesApi = {
  upload: async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<{ url: string }>('/api/images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data.url;
  },

  uploadMultiple: async (files: File[]): Promise<string[]> => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    const response = await api.post<{ urls: string[] }>('/api/images/multiple', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data.urls;
  },

  delete: async (url: string): Promise<void> => {
    const encodedUrl = encodeURIComponent(url);
    await api.delete(`/api/images?url=${encodedUrl}`);
  },
};
