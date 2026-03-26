export interface User {
  id: number;
  name: string;
  email: string;
  rating: number;
  role: 'USER' | 'ADMIN';
  telegramChatId?: string;
  phone?: string;
  city?: string;
  avatarUrl?: string;
  createdAt: string;
}

export interface UserPublic {
  id: number;
  name: string;
  rating: number;
  helpedCount: number;
  debtCount: number;
  avatarUrl?: string;
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
  helperId?: number;
  helperName?: string;
  imageUrls?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Review {
  id: number;
  helpId: number;
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
  content: string;
  parentCommentId?: number;
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
