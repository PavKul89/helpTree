/**
 * Generates a key for money operations that require an Idempotency-Key header.
 * The backend accepts Latin letters, digits, '.', '_', ':' and '-'.
 */
export const createIdempotencyKey = (prefix = 'op'): string => {
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  return `${prefix}-${random}`;
};
