import axios from 'axios';

export interface ApiFieldError {
  field?: string;
  message?: string;
  code?: string;
}

export interface ApiErrorBody {
  status?: number;
  code?: string;
  message?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
  /** @deprecated legacy map-based field errors kept for older backends. */
  errors?: Record<string, string>;
}

export interface ApiErrorInfo {
  status?: number;
  code?: string;
  message?: string;
  traceId?: string;
  fields: Record<string, string>;
}

/**
 * Converts the canonical `fieldErrors` list into the map the forms use.
 *
 * The backend may report several violations for one field; a form can display
 * only one message per input, so the first message wins. The legacy `errors`
 * map is still read so an older server cannot break the current frontend.
 */
export const extractFieldErrors = (
  data: ApiErrorBody | undefined
): Record<string, string> => {
  const fields: Record<string, string> = {};
  const list = data?.fieldErrors;
  if (Array.isArray(list)) {
    for (const item of list) {
      if (item?.field && item.field in fields === false) {
        fields[item.field] = item.message ?? '';
      }
    }
    return fields;
  }
  if (data?.errors) {
    Object.assign(fields, data.errors);
  }
  return fields;
};

/**
 * Extracts the ErrorResponse payload used by the Spring backend.
 * The canonical shape is `fieldErrors: [{ field, message, code }]`.
 */
export const getApiErrorInfo = (error: unknown): ApiErrorInfo => {
  if (!axios.isAxiosError(error)) {
    return { fields: {} };
  }

  const data = error.response?.data as ApiErrorBody | undefined;

  return {
    status: error.response?.status,
    code: data?.code,
    message: data?.message,
    traceId: data?.traceId,
    fields: extractFieldErrors(data),
  };
};

export const isGenericValidationMessage = (message?: string): boolean =>
  Boolean(message && /validation failed|see 'errors'/i.test(message));
