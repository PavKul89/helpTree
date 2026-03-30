import axios from 'axios';

const API_URL = 'http://localhost:8080';

const createNotificationApi = () => {
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

const api = createNotificationApi();

export const notificationApi = {
  getNewResponsesCount: async (): Promise<number> => {
    const response = await api.get<number>('/api/helps/new-responses/count');
    return response.data;
  },
};
