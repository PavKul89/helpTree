import axios from 'axios';
import { API_URL } from '../config';

export { API_URL };

const api = axios.create({
  baseURL: API_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  if (token) {
    config.headers = config.headers || {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }

  if (config.data instanceof FormData) {
    if (config.headers) {
      delete (config.headers as any)['Content-Type'];
      delete (config.headers as any)['content-type'];
    }
  } else {
    if (config.headers && !(config.headers as any)['Content-Type'] && !(config.headers as any)['content-type']) {
      (config.headers as any)['Content-Type'] = 'application/json';
    }
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const config = error.config || {};
      const url = String(config.url || '');
      const hadToken = Boolean(
        config.headers?.Authorization ||
          config.headers?.authorization ||
          localStorage.getItem('accessToken') ||
          localStorage.getItem('token')
      );
      const isAuthRequest = url.includes('/api/auth/');
      const isAuthPage =
        window.location.pathname.startsWith('/login') ||
        window.location.pathname.startsWith('/register');

      if (hadToken && !isAuthRequest) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('token');
        if (!isAuthPage) {
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
