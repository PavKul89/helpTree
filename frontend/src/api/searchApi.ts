import api from './axios';
import type { PageResponse } from './pagination';
import type { SearchResult, SearchType } from '../types';

export interface SearchQuery {
  q: string;
  type?: SearchType;
  city?: string;
  category?: string;
  sort?: 'newest' | 'oldest';
  page?: number;
  size?: number;
}

/**
 * Unified server-side search over posts, public users and reviews.
 * The backend owns visibility rules, filtering and ordering.
 */
export const searchApi = {
  search: async (
    query: SearchQuery,
    signal?: AbortSignal
  ): Promise<PageResponse<SearchResult>> => {
    const { q, type = 'ALL', city, category, sort = 'newest', page = 0, size = 20 } = query;
    const response = await api.get<PageResponse<SearchResult>>('/api/search', {
      params: {
        q,
        type,
        sort,
        page,
        size,
        ...(city ? { city } : {}),
        ...(category ? { category } : {}),
      },
      signal,
    });
    return response.data;
  },
};
