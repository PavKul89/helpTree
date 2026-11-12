import api from './axios';
import { createIdempotencyKey } from '../utils/idempotency';
import type { PageResponse } from './pagination';
import type {
  CreateReportRequest,
  Report,
  ReportDetails,
  ReportMeta,
  ReportReason,
  ReportStatus,
  ReportTargetType,
} from '../types';

export interface MyReportsQuery {
  status?: ReportStatus;
  targetType?: ReportTargetType;
  reason?: ReportReason;
  page?: number;
  size?: number;
}

export const reportsApi = {
  create: async (
    data: CreateReportRequest,
    idempotencyKey: string = createIdempotencyKey('report')
  ): Promise<Report> => {
    const response = await api.post<Report>('/api/reports', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return response.data;
  },

  getMine: async (
    query: MyReportsQuery = {},
    signal?: AbortSignal
  ): Promise<PageResponse<Report>> => {
    const { status, targetType, reason, page = 0, size = 20 } = query;
    const response = await api.get<PageResponse<Report>>('/api/reports/me', {
      params: {
        page,
        size,
        ...(status ? { status } : {}),
        ...(targetType ? { targetType } : {}),
        ...(reason ? { reason } : {}),
      },
      signal,
    });
    return response.data;
  },

  getMeta: async (signal?: AbortSignal): Promise<ReportMeta> => {
    const response = await api.get<ReportMeta>('/api/reports/meta', { signal });
    return response.data;
  },

  getById: async (reportId: number, signal?: AbortSignal): Promise<ReportDetails> => {
    const response = await api.get<ReportDetails>(`/api/reports/${reportId}`, { signal });
    return response.data;
  },
};
