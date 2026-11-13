import axios, { AxiosError, AxiosHeaders } from 'axios';
import type { AxiosAdapter, AxiosResponse } from 'axios';
import api from './axios';
import { authApi } from './authApi';

const originalAdapter = api.defaults.adapter;

const response = (config: Parameters<AxiosAdapter>[0], status: number, data: unknown): AxiosResponse => ({
  config,
  data,
  headers: new AxiosHeaders(),
  status,
  statusText: status === 200 ? 'OK' : 'Unauthorized',
});

const unauthorized = (config: Parameters<AxiosAdapter>[0]) =>
  Promise.reject(
    new AxiosError(
      'Unauthorized',
      'ERR_BAD_REQUEST',
      config,
      undefined,
      response(config, 401, {})
    )
  );

describe('API access-token refresh', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState({}, '', '/login');
    localStorage.setItem('accessToken', 'old-access');
    localStorage.setItem('refreshToken', 'old-refresh');
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    jest.restoreAllMocks();
  });

  it('refreshes after 401 and retries the original request once', async () => {
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    } as AxiosResponse);
    api.defaults.adapter = async (config) => {
      if (config.headers.Authorization === 'Bearer new-access') {
        return response(config, 200, { ok: true });
      }
      return unauthorized(config);
    };

    const result = await api.get('/protected');

    expect(result.data).toEqual({ ok: true });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/auth\/refresh$/),
      { refreshToken: 'old-refresh' }
    );
    expect(localStorage.getItem('accessToken')).toBe('new-access');
    expect(localStorage.getItem('refreshToken')).toBe('new-refresh');
  });

  it('shares one refresh promise across simultaneous 401 responses', async () => {
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    } as AxiosResponse);
    api.defaults.adapter = async (config) => {
      if (config.headers.Authorization === 'Bearer new-access') {
        return response(config, 200, { ok: true });
      }
      return unauthorized(config);
    };

    const results = await Promise.all([
      api.get('/protected/one'),
      api.get('/protected/two'),
      api.get('/protected/three'),
    ]);

    expect(results).toHaveLength(3);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('refreshes before retrying an unauthorized WebSocket ticket request', async () => {
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue({
      data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
    } as AxiosResponse);
    let ticketRequests = 0;
    api.defaults.adapter = async (config) => {
      if (config.url === '/api/auth/ws-ticket' && ++ticketRequests === 1) {
        return unauthorized(config);
      }
      return response(config, 200, { ticket: 'ticket', expiresInSeconds: 60 });
    };

    await expect(authApi.getWsTicket()).resolves.toEqual({
      ticket: 'ticket',
      expiresInSeconds: 60,
    });

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(ticketRequests).toBe(2);
    expect(localStorage.getItem('accessToken')).toBe('new-access');
  });

  it('clears both tokens and emits logout when refresh fails', async () => {
    jest.spyOn(axios, 'post').mockRejectedValue(new Error('refresh failed'));
    const logoutEvent = jest.fn();
    window.addEventListener('auth:logout', logoutEvent);
    api.defaults.adapter = unauthorized;

    await expect(api.get('/protected')).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(logoutEvent).toHaveBeenCalledTimes(1);
    window.removeEventListener('auth:logout', logoutEvent);
  });
});
