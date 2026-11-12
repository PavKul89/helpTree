export interface User {
  id: number;
  name: string;
  email: string;
  rating: number;
  role: 'USER' | 'ADMIN';
  telegramChatId?: string;
  phone?: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  avatarUrl?: string;
  createdAt: string;
  helpedCount?: number;
  debtCount?: number;
  birthDate?: string;
  blockedAt?: string;
  blockedUntil?: string;
  debtStartedAt?: string;
  helpCoins?: number;
  vipUntil?: string;
  emailVerified?: boolean;
  deleted?: boolean;
  deletedAt?: string;
  anonymizedAt?: string;
}

export interface UserPublic {
  id: number;
  name: string;
  rating: number;
  helpedCount: number;
  debtCount: number;
  avatarUrl?: string;
  city?: string;
  nicknameColor?: string;
}

export interface Post {
  id: number;
  title: string;
  description: string;
  category: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  userId: number;
  authorName: string;
  authorAvatarUrl?: string;
  authorRating: number;
  userCity?: string;
  latitude?: number;
  longitude?: number;
  helperId?: number;
  helperName?: string;
  imageUrls?: string[];
  createdAt: string;
  updatedAt: string;
  /** Present only in the owner/admin projection of the post detail. */
  boostedUntil?: string;
  boosted: boolean;
  commentsCount: number;
}

export interface Review {
  id: number;
  helpId: number;
  postId?: number;
  fromUserId: number;
  fromUserName: string;
  toUserId: number;
  toUserName: string;
  rating: number;
  comment?: string;
  createdAt: string;
  replyText?: string;
  replyAt?: string;
  replyUpdatedAt?: string;
  editedAt?: string;
  editCount?: number;
  /** Last moment the author may still edit this review. */
  editableUntil?: string;
  /** Last moment the recipient may still edit the reply, null when no reply exists. */
  replyEditableUntil?: string;
}

export interface Comment {
  id: number;
  postId: number;
  userId: number;
  userName: string;
  userAvatarUrl?: string;
  /** Null for a tombstone (a deleted comment whose replies must survive). */
  content?: string | null;
  parentCommentId?: number;
  depth: number;
  createdAt: string;
  updatedAt?: string;
  deleted: boolean;
  deletedByAuthor: boolean;
  deletedAt?: string;
}

export type CommentSort = 'OLDEST' | 'NEWEST';

export interface CommentPage {
  /** Root comments of the page followed by their descendants. */
  content: Comment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
  /** Number of non-deleted comments on the post. */
  totalComments: number;
}

export interface CommentAction {
  comment: Comment;
  totalComments: number;
}

export type SearchType = 'ALL' | 'POST' | 'USER' | 'REVIEW';

/** One hit of GET /api/search. */
export interface SearchResult {
  type: Exclude<SearchType, 'ALL'>;
  id: number;
  title: string;
  snippet?: string;
  category?: string;
  city?: string;
  rating?: number;
  createdAt: string;
  ownerId?: number;
  /** Relative route supplied by the server for this hit. */
  link: string;
}

export type ReportReason =
  | 'SPAM'
  | 'ABUSE'
  | 'HARASSMENT'
  | 'INAPPROPRIATE'
  | 'FRAUD'
  | 'IMPERSONATION'
  | 'OTHER';

export type ReportTargetType =
  | 'POST'
  | 'USER'
  | 'COMMENT'
  | 'REVIEW'
  | 'MESSAGE'
  /** Historical only: new HELP/CHAT reports are rejected by the server. */
  | 'HELP'
  | 'CHAT'
  /** Target of a report-level moderation action; never a report target itself. */
  | 'REPORT';

export type ReportStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE';
export type ReportPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export interface CreateCommentReportRequest {
  reason: ReportReason;
  details?: string;
}

/** Body of POST /api/reports. */
export interface CreateReportRequest {
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  description?: string;
}

/** Body of POST /api/reviews/{id}/report. */
export interface CreateReviewReportRequest {
  reason: ReportReason;
  details?: string;
}

export interface Report {
  id: number;
  reporterId?: number;
  reporterName?: string;
  targetType: ReportTargetType;
  targetId: number;
  reason: ReportReason;
  description?: string;
  status: ReportStatus;
  priority: ReportPriority;
  assignedModeratorId?: number;
  assignedModeratorName?: string;
  resolution?: string;
  duplicateOfId?: number;
  resolvedById?: number;
  resolvedByName?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ReportMeta {
  targetTypes: ReportTargetType[];
  reasons: ReportReason[];
  statuses: ReportStatus[];
  priorities: ReportPriority[];
  maxDescriptionLength: number;
}

export interface ReportEvent {
  id: number;
  actorId?: number;
  actorName?: string;
  eventType: string;
  fromStatus?: ReportStatus;
  toStatus?: ReportStatus;
  internalNote?: string | null;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface ReportDetails {
  report: Report;
  events: ReportEvent[];
}

export interface Chat {
  id: number;
  participantId: number;
  participantName: string;
  participantAvatarUrl?: string;
  lastMessage?: string;
  lastMessageAt?: string;
  unreadCount: number;
}

export interface Message {
  id: number;
  chatId: number;
  senderId: number;
  senderName: string;
  senderAvatarUrl?: string;
  content: string;
  createdAt: string;
  isRead: boolean;
  attachmentUrls?: string[];
  editedAt?: string;
  editCount?: number;
  /** Last moment the sender may still edit this message. */
  editableUntil?: string;
}

export interface AuthResponse {
  accessToken: string;
  userId: number;
  email: string;
  role: 'USER' | 'ADMIN';
  expiresIn: number;
  refreshToken?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  phone: string;
  city: string;
}

export interface CreatePostRequest {
  title: string;
  description: string;
  category: string;
  imageUrls?: string[];
}

export interface UpdatePostRequest {
  title?: string;
  description?: string;
  authorName?: string;
  imageUrls?: string[];
}

export interface CreateCommentRequest {
  content: string;
  parentCommentId?: number;
}

export interface CreateChatRequest {
  participantId: number;
}

export interface CreateMessageRequest {
  content: string;
}

export interface WsMessageEnvelope {
  type:
    | 'NEW_MESSAGE'
    | 'MESSAGE_UPDATED'
    | 'MESSAGE_DELETED'
    | 'MESSAGES_READ'
    | 'CHAT_DELETED'
    | 'CHAT_UPDATED'
    | 'NOTIFICATION'
    | 'ERROR';
  message?: Message;
  chat?: Chat;
  chatId?: number;
  lastReadMessageId?: number;
  notification?: Notification;
  unreadCount?: number;
  error?: string;
  timestamp?: string;
}

/** Event types that are part of the public notification API. */
export type NotificationType =
  | 'NEW_HELP_RESPONSE'
  | 'HELP_ACCEPTED'
  | 'HELP_COMPLETED'
  | 'HELP_CONFIRMED'
  | 'HELP_CANCELLED'
  | 'NEW_REVIEW'
  | 'REVIEW_REPLIED'
  | 'REVIEW_UPDATED'
  | 'HELP_SLA_REMINDER'
  | 'PAID_FEATURE_EXPIRING'
  | 'NEW_REPORT'
  | 'REPORT_ACCEPTED'
  | 'REPORT_REJECTED'
  | 'MODERATION_ACTION'
  | 'ACCOUNT_BLOCKED'
  | 'ACCOUNT_UNBLOCKED'
  | 'COINS_EARNED'
  | 'COINS_SPENT'
  | 'ACHIEVEMENT_EARNED';

export type NotificationChannel = 'IN_APP' | 'EMAIL' | 'TELEGRAM';

/**
 * Canonical notification row. `dedupeKey` is deliberately not exposed: the
 * server owns it and the UI must never build one itself.
 */
export interface Notification {
  id: number;
  userId: number;
  type: NotificationType;
  title: string;
  body: string;
  payload?: Record<string, unknown>;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface MarkAllReadResponse {
  updated: number;
  unreadCount: number;
}

export interface NotificationPreferenceDto {
  type: NotificationType;
  channel: NotificationChannel;
  enabled: boolean;
}

/**
 * The server always returns the full matrix (every type × every channel), so
 * the settings screen never has to guess a default.
 */
export interface NotificationPreferencesResponse {
  preferences: NotificationPreferenceDto[];
  types: NotificationType[];
  channels: NotificationChannel[];
}

export interface WsNotificationEnvelope {
  type: 'NOTIFICATION';
  notification: Notification;
  unreadCount: number;
  timestamp?: string;
}


export interface WsTicket {
  ticket: string;
  expiresInSeconds: number;
}

export interface Help {
  id: number;
  postId: number;
  postTitle: string;
  helperId: number;
  helperName: string;
  receiverId: number;
  receiverName: string;
  status: 'PENDING' | 'ACCEPTED' | 'COMPLETED' | 'CONFIRMED' | 'CANCELLED';
  acceptedAt: string;
  completedAt?: string;
  confirmedAt?: string;
  createdAt: string;
}

export interface HelpRequest {
  postId: number;
  helperId: number;
}

export interface HelpGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
  /** Confirmed help acts in the selected subgraph, before edge limiting. */
  totalHelps: number;
  /** Edges actually returned in `edges`. */
  returnedHelps: number;
  totalUsers: number;
  /** True when `maxEdges` limited the response. */
  truncated: boolean;
  buildTimeMs: number;
}

export interface GraphNode {
  id: number;
  name: string;
  avatarUrl?: string;
  helpedCount: number;
  /** Owner/admin projection only; the public graph omits it. */
  debtCount?: number;
  rating: number;
}

export interface GraphEdge {
  id: number;
  fromUserId: number;
  fromUserName: string;
  toUserId: number;
  toUserName: string;
  /** Owner/admin projection only; the public graph omits it. */
  postId?: number;
  /** Owner/admin projection only; the public graph omits it. */
  postTitle?: string;
  /** Owner/admin projection only; the public graph omits it. */
  status?: string;
  confirmedAt: string;
}
