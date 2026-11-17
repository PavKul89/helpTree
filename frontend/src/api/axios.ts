import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_URL } from '../config';
import type { AuthResponse } from '../types';
import { readCsrfToken } from './csrf';
import { getAccessToken, purgeLegacyTokens, setAccessToken } from './tokenStore';

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
  setAccessToken(null);
  purgeLegacyTokens();
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

interface RefreshOptions {
  /** Initial session restore must not bounce anonymous visitors to /login. */
  silent?: boolean;
}

export const refreshAccessToken = (options: RefreshOptions = {}): Promise<string> => {
  if (!refreshPromise) {
    const csrfToken = readCsrfToken();
    if (!csrfToken) {
      clearAuthTokens();
      if (!options.silent) redirectToLogin();
      return Promise.reject(new Error('CSRF token is not available'));
    }

    refreshPromise = axios
      .post<AuthResponse>(
        `${API_URL}/api/auth/refresh`,
        {},
        {
          withCredentials: true,
          headers: { 'X-CSRF-Token': csrfToken },
        }
      )
      .then(({ data }) => {
        if (!data.accessToken) {
          throw new Error('Refresh response did not contain an access token');
        }
        setAccessToken(data.accessToken);
        window.dispatchEvent(new Event('auth:tokens-refreshed'));
        return data.accessToken;
      })
      .catch((error: unknown) => {
        clearAuthTokens();
        if (!options.silent) redirectToLogin();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

api.interceptors.request.use((config) => {
  const token = getAccessToken();
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

    const currentAccessToken = getAccessToken();
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
