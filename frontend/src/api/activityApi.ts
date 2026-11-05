import api from './axios';
import { normalizePage, type PageResponse } from './pagination';

export interface Activity {
  type: string;
  typeLabel: string;
  emoji: string;
  title: string;
  description?: string;
  timestamp: string;
  relatedUserId?: number;
  relatedUserName?: string;
  relatedPostId?: number;
  relatedPostTitle?: string;
  category?: string;
  status?: string;
}

export interface ActivityQuery {
  page?: number;
  size?: number;
  type?: string;
}

/**
 * The server owns visibility, ordering and pagination. Public viewers only
 * receive confirmed helps, public post statuses and achievements; participant
 * names and debt blocks are included only for the owner or an admin.
 */
export const activityApi = {
  getUserActivities: async (
    userId: number,
    query: ActivityQuery = {}
  ): Promise<PageResponse<Activity>> => {
    const response = await api.get<PageResponse<Activity> | Activity[]>(
      `/api/users/${userId}/activities`,
      { params: query }
    );
    return normalizePage(response.data);
  },
  getAllActivities: async (
    userId: number,
    query: ActivityQuery = {}
  ): Promise<PageResponse<Activity>> => {
    const response = await api.get<PageResponse<Activity> | Activity[]>(
      '/api/activities',
      { params: { userId, ...query } }
    );
    return normalizePage(response.data);
  },
};
