import api from './axios';

export const WALLET_UPDATED_EVENT = 'helptree:wallet-updated';

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

/** Spring Data Page. The wallet history endpoint returns exactly this shape. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface DailyBonusResponse {
  success: boolean;
  balance: number;
  message: string;
}

/**
 * Lets the navbar (and any other balance indicator) refresh after a
 * server-confirmed wallet change without issuing another request.
 */
export const notifyWalletUpdated = (balance?: number): void => {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(
    new CustomEvent<{ balance?: number }>(WALLET_UPDATED_EVENT, {
      detail: { balance },
    })
  );
};

export const walletApi = {
  getWallet: async (userId: number, signal?: AbortSignal): Promise<WalletDto> => {
    const response = await api.get<WalletDto>(`/api/users/${userId}/wallet`, { signal });
    return response.data;
  },

  getTransactions: async (
    userId: number,
    page: number = 0,
    size: number = 20,
    signal?: AbortSignal
  ): Promise<PageResponse<CoinTransactionDto>> => {
    const response = await api.get<PageResponse<CoinTransactionDto>>(
      `/api/users/${userId}/wallet/transactions`,
      { params: { page, size }, signal }
    );
    return response.data;
  },

  claimDailyBonus: async (userId: number): Promise<DailyBonusResponse> => {
    const response = await api.post<DailyBonusResponse>(
      `/api/users/${userId}/wallet/daily-bonus`
    );
    return response.data;
  },
};
