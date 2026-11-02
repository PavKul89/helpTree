import axios from 'axios';

export interface ApiErrorInfo {
  status?: number;
  message?: string;
  fields: Record<string, string>;
}

/**
 * Extracts the ErrorResponse payload used by the Spring backend.
 * Field errors are returned as `errors: { field: message }`.
 */
export const getApiErrorInfo = (error: unknown): ApiErrorInfo => {
  if (!axios.isAxiosError(error)) {
    return { fields: {} };
  }

  const data = error.response?.data as
    | { message?: string; errors?: Record<string, string> }
    | undefined;

  return {
    status: error.response?.status,
    message: data?.message,
    fields: data?.errors ?? {},
  };
};

export const isGenericValidationMessage = (message?: string): boolean =>
  Boolean(message && /validation failed|see 'errors'/i.test(message));
