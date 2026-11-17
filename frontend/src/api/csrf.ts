/**
 * Double-submit CSRF token for the cookie-authenticated refresh endpoint.
 *
 * The value is set by the backend in a readable (non-HttpOnly) cookie. It is
 * deliberately not secret: the HttpOnly refresh cookie is the secret. The token
 * only proves that the request came from JavaScript on our own origin.
 */
const CSRF_COOKIE_NAME = 'helptree_csrf';

export const readCsrfToken = (): string | null => {
  const cookies = document.cookie ? document.cookie.split(';') : [];
  for (const entry of cookies) {
    const [name, ...rest] = entry.trim().split('=');
    if (name === CSRF_COOKIE_NAME) {
      return decodeURIComponent(rest.join('='));
    }
  }
  return null;
};
