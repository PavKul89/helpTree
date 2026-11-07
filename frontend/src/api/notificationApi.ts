import api from './axios';
import { normalizePage, type PageResponse } from './pagination';
import type {
  MarkAllReadResponse,
  Notification,
  NotificationChannel,
  NotificationPreferencesResponse,
  NotificationType,
  UnreadCountResponse,
} from '../types';

/** A new notification arrived over the authenticated WebSocket. */
export const NOTIFICATION_EVENT = 'helptree:notification';
/** A read action changed the unread counter (sent by the notification page). */
export const NOTIFICATION_COUNT_EVENT = 'helptree:notification-count';

export interface NotificationQuery {
  page?: number;
  size?: number;
  unreadOnly?: boolean;
}

export interface PreferenceUpdate {
  type: NotificationType;
  channel: NotificationChannel;
  enabled: boolean;
}

export const notificationApi = {
  getNotifications: async (query: NotificationQuery = {}): Promise<PageResponse<Notification>> => {
    const response = await api.get<PageResponse<Notification> | Notification[]>(
      '/api/notifications',
      { params: query }
    );
    return normalizePage(response.data);
  },

  getUnreadCount: async (): Promise<UnreadCountResponse> => {
    const response = await api.get<UnreadCountResponse>('/api/notifications/unread-count');
    return response.data;
  },

  markRead: async (notificationId: number): Promise<UnreadCountResponse> => {
    const response = await api.post<UnreadCountResponse>(
      `/api/notifications/${notificationId}/read`
    );
    return response.data;
  },

  markAllRead: async (): Promise<MarkAllReadResponse> => {
    const response = await api.post<MarkAllReadResponse>('/api/notifications/read-all');
    return response.data;
  },

  getPreferences: async (): Promise<NotificationPreferencesResponse> => {
    const response = await api.get<NotificationPreferencesResponse>(
      '/api/notifications/preferences'
    );
    return response.data;
  },

  /**
   * Partial patch: only the changed (type, channel) pairs are sent. The server
   * treats each row as an upsert, so two open settings tabs cannot lose data.
   */
  updatePreferences: async (
    updates: PreferenceUpdate[]
  ): Promise<NotificationPreferencesResponse> => {
    const response = await api.put<NotificationPreferencesResponse>(
      '/api/notifications/preferences',
      { preferences: updates }
    );
    return response.data;
  },
};
