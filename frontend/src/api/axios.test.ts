import axios, { AxiosError, AxiosHeaders } from 'axios';
import type { AxiosAdapter, AxiosResponse } from 'axios';
import api, { refreshAccessToken } from './axios';
import { authApi } from './authApi';
import { getAccessToken, purgeLegacyTokens, setAccessToken } from './tokenStore';

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

const refreshed = (): AxiosResponse =>
  ({ data: { accessToken: 'new-access' } } as AxiosResponse);

describe('API access-token refresh', () => {
  beforeEach(() => {
    setAccessToken('old-access');
    purgeLegacyTokens();
    document.cookie = 'helptree_csrf=csrf-123; path=/';
    window.history.replaceState({}, '', '/login');
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    document.cookie = 'helptree_csrf=; max-age=0; path=/';
    setAccessToken(null);
    jest.restoreAllMocks();
  });

  it('sends the CSRF header, refreshes after 401 and retries the original request once', async () => {
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue(refreshed());
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
      {},
      expect.objectContaining({
        withCredentials: true,
        headers: expect.objectContaining({ 'X-CSRF-Token': 'csrf-123' }),
      })
    );
    expect(getAccessToken()).toBe('new-access');
  });

  it('shares one refresh promise across simultaneous 401 responses', async () => {
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue(refreshed());
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
    const refresh = jest.spyOn(axios, 'post').mockResolvedValue(refreshed());
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
    expect(getAccessToken()).toBe('new-access');
  });

  it('fails early when the CSRF cookie is missing, without a request', async () => {
    document.cookie = 'helptree_csrf=; max-age=0; path=/';
    const refresh = jest.spyOn(axios, 'post');

    await expect(refreshAccessToken()).rejects.toThrow('CSRF token is not available');
    expect(refresh).not.toHaveBeenCalled();
  });

  it('clears the in-memory token and emits logout when refresh fails', async () => {
    jest.spyOn(axios, 'post').mockRejectedValue(new Error('refresh failed'));
    const logoutEvent = jest.fn();
    window.addEventListener('auth:logout', logoutEvent);
    api.defaults.adapter = unauthorized;

    await expect(api.get('/protected')).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(getAccessToken()).toBeNull();
    expect(logoutEvent).toHaveBeenCalledTimes(1);
    window.removeEventListener('auth:logout', logoutEvent);
  });
});
