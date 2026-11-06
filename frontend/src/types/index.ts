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

export type ReportReason = 'SPAM' | 'ABUSE' | 'HARASSMENT' | 'INAPPROPRIATE' | 'OTHER';

export interface CreateCommentReportRequest {
  reason: ReportReason;
  details?: string;
}

export interface Report {
  id: number;
  reporterId?: number;
  reporterName?: string;
  targetType: 'POST' | 'USER' | 'COMMENT' | 'MESSAGE' | 'HELP' | 'CHAT';
  targetId: number;
  reason: ReportReason;
  details?: string;
  status: 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  resolution?: string;
  resolvedById?: number;
  resolvedByName?: string;
  resolvedAt?: string;
  createdAt: string;
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
  type: 'NEW_MESSAGE' | 'MESSAGE_DELETED' | 'MESSAGES_READ' | 'CHAT_DELETED' | 'CHAT_UPDATED' | 'ERROR';
  message?: Message;
  chat?: Chat;
  chatId?: number;
  lastReadMessageId?: number;
  error?: string;
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
