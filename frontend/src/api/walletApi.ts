import axios from 'axios';

const API_URL = 'http://localhost:8080';

export interface WalletDto {
  userId: number;
  balance: number;
  totalEarned: number;
  totalSpent: number;
}

export interface CoinTransactionDto {
  id: number;
  type: string;
  amount: number;
  description: string;
  relatedUserId?: number;
  relatedPostId?: number;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const createApi = () => {
  const instance = axios.create({
    baseURL: API_URL,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  return instance;
};

const api = createApi();

export const walletApi = {
  getWallet: async (userId: number): Promise<WalletDto> => {
    const response = await api.get<WalletDto>(`/api/users/${userId}/wallet`);
    return response.data;
  },

  getTransactions: async (
    userId: number,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<CoinTransactionDto>> => {
    const response = await api.get<PageResponse<CoinTransactionDto>>(
      `/api/users/${userId}/wallet/transactions`,
      { params: { page, size } }
    );
    return response.data;
  },

  claimDailyBonus: async (userId: number): Promise<{ success: boolean; balance: number; message: string }> => {
    const response = await api.post<{ success: boolean; balance: number; message: string }>(
      `/api/users/${userId}/wallet/daily-bonus`
    );
    return response.data;
  },

  spend: async (userId: number, type: string, description: string): Promise<{ success: boolean; newBalance: number }> => {
    const prices: Record<string, number> = { 'VIP': 50, 'BOOST': 5, 'UNBLOCK': 100 };
    const price = prices[type] || 0;
    const response = await api.post<{ success: boolean; newBalance: number }>(
      `/api/users/${userId}/wallet/spend`,
      null,
      { params: { amount: price, type, description } }
    );
    return response.data;
  },

  changeNicknameColor: async (userId: number, color: string): Promise<{ success: boolean; newBalance: number; color: string }> => {
    const response = await api.post<{ success: boolean; newBalance: number; color: string }>(
      `/api/users/${userId}/wallet/nickname-color`,
      null,
      { params: { color } }
    );
    return response.data;
  },
};
