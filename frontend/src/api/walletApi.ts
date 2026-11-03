import api from './axios';
import { createIdempotencyKey } from '../utils/idempotency';

export const WALLET_UPDATED_EVENT = 'helptree:wallet-updated';

export interface WalletDto {
  userId: number;
  balance: number;
  totalEarned: number;
  totalSpent: number;
  vipUntil?: string;
  vipActive?: boolean;
  nicknameColor?: string;
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
  claimed: boolean;
  balance: number;
  message: string;
}

/** Server-owned economy catalog. Never duplicate these values in the UI. */
export interface WalletPricingDto {
  boostPrice: number;
  boostHours: number;
  vipPrice: number;
  vipDays: number;
  nicknameColorPrice: number;
  accountUnblockPrice: number;
  accountUnblockDebtReduction: number;
  dailyBonusCoins: number;
}

export type WalletPurchaseType = 'VIP' | 'ACCOUNT_UNBLOCK' | 'NICKNAME_COLOR';
export type TransactionDirection = 'IN' | 'OUT';

export interface WalletTransactionsQuery {
  page?: number;
  size?: number;
  type?: string;
  direction?: TransactionDirection;
  from?: string;
  to?: string;
}

export interface WalletOperationResponse {
  success: boolean;
  operation: string;
  applied: boolean;
  spentAmount: number;
  newBalance: number;
  expiresAt?: string;
  color?: string;
  debtCount?: number;
  blocked?: boolean;
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

  getPricing: async (userId: number, signal?: AbortSignal): Promise<WalletPricingDto> => {
    const response = await api.get<WalletPricingDto>(`/api/users/${userId}/wallet/pricing`, {
      signal,
    });
    return response.data;
  },

  getTransactions: async (
    userId: number,
    query: WalletTransactionsQuery = {},
    signal?: AbortSignal
  ): Promise<PageResponse<CoinTransactionDto>> => {
    const { page = 0, size = 20, type, direction, from, to } = query;
    const response = await api.get<PageResponse<CoinTransactionDto>>(
      `/api/users/${userId}/wallet/transactions`,
      {
        params: {
          page,
          size,
          ...(type ? { type } : {}),
          ...(direction ? { direction } : {}),
          ...(from ? { from } : {}),
          ...(to ? { to } : {}),
        },
        signal,
      }
    );
    return response.data;
  },

  claimDailyBonus: async (userId: number): Promise<DailyBonusResponse> => {
    const response = await api.post<DailyBonusResponse>(
      `/api/users/${userId}/wallet/daily-bonus`
    );
    return response.data;
  },

  /**
   * Server-priced purchase. The client only chooses the operation; price and
   * duration come from the backend. The same idempotency key must be reused
   * when retrying the exact same user action.
   */
  purchase: async (
    userId: number,
    type: WalletPurchaseType,
    options: { color?: string; idempotencyKey?: string } = {}
  ): Promise<WalletOperationResponse> => {
    const response = await api.post<WalletOperationResponse>(
      `/api/users/${userId}/wallet/spend`,
      null,
      {
        params: { type, ...(options.color ? { color: options.color } : {}) },
        headers: {
          'Idempotency-Key': options.idempotencyKey ?? createIdempotencyKey(),
        },
      }
    );
    return response.data;
  },

  changeNicknameColor: async (
    userId: number,
    color: string,
    idempotencyKey: string = createIdempotencyKey()
  ): Promise<WalletOperationResponse> => {
    const response = await api.post<WalletOperationResponse>(
      `/api/users/${userId}/wallet/nickname-color`,
      null,
      { params: { color }, headers: { 'Idempotency-Key': idempotencyKey } }
    );
    return response.data;
  },
};
