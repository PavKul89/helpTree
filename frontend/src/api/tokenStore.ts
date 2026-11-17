/**
 * The access token lives in memory only. A page reload restores the session by
 * calling the refresh endpoint, which uses the HttpOnly refresh cookie. Keeping
 * the token out of localStorage means an XSS payload cannot read a long-lived
 * credential from persistent storage.
 */
let accessToken: string | null = null;

export const getAccessToken = (): string | null => accessToken;

export const setAccessToken = (token: string | null): void => {
  accessToken = token;
};

const LEGACY_KEYS = ['accessToken', 'refreshToken', 'token'] as const;

/** Removes values written by the previous localStorage-based implementation. */
export const purgeLegacyTokens = (): void => {
  LEGACY_KEYS.forEach((key) => localStorage.removeItem(key));
};
