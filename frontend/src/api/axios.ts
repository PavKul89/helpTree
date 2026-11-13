import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_URL } from '../config';
import type { AuthResponse } from '../types';

export { API_URL };

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _sentAccessToken?: string;
}

const api = axios.create({
  baseURL: API_URL,
});

let refreshPromise: Promise<string> | null = null;

export const clearAuthTokens = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('token');
  window.dispatchEvent(new Event('auth:logout'));
};

const redirectToLogin = () => {
  const isAuthPage =
    window.location.pathname.startsWith('/login') ||
    window.location.pathname.startsWith('/register');
  if (!isAuthPage) {
    window.location.href = '/login';
  }
};

export const refreshAccessToken = (): Promise<string> => {
  if (!refreshPromise) {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      clearAuthTokens();
      redirectToLogin();
      return Promise.reject(new Error('Refresh token is not available'));
    }

    refreshPromise = axios
      .post<AuthResponse>(`${API_URL}/api/auth/refresh`, { refreshToken })
      .then(({ data }) => {
        if (!data.accessToken || !data.refreshToken) {
          throw new Error('Refresh response did not contain both tokens');
        }
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        window.dispatchEvent(new Event('auth:tokens-refreshed'));
        return data.accessToken;
      })
      .catch((error: unknown) => {
        clearAuthTokens();
        redirectToLogin();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
    (config as RetriableRequestConfig)._sentAccessToken = token;
  }

  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type');
  } else if (!config.headers['Content-Type']) {
    config.headers['Content-Type'] = 'application/json';
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableRequestConfig | undefined;
    const url = String(config?.url || '');
    if (error.response?.status !== 401 || !config || config._retry || /\/api\/auth\//.test(url)) {
      return Promise.reject(error);
    }

    const currentAccessToken = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!currentAccessToken) {
      return Promise.reject(error);
    }

    config._retry = true;
    if (config._sentAccessToken && config._sentAccessToken !== currentAccessToken) {
      config.headers.Authorization = `Bearer ${currentAccessToken}`;
      return api(config);
    }

    try {
      const newAccessToken = await refreshAccessToken();
      config.headers.Authorization = `Bearer ${newAccessToken}`;
      return api(config);
    } catch {
      return Promise.reject(error);
    }
  }
);

export default api;
