import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { User } from '../types';
import { authApi } from '../api/authApi';
import { clearAuthTokens } from '../api/axios';
import { notifyWalletUpdated, walletApi } from '../api/walletApi';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (accessToken: string, refreshToken: string, user: User) => void;
  logout: () => void;
  setUser: (user: User | null) => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const handleLogout = () => {
      setToken(null);
      setUser(null);
    };
    const handleTokenRefresh = () => {
      setToken(localStorage.getItem('accessToken'));
    };
    window.addEventListener('auth:logout', handleLogout);
    window.addEventListener('auth:tokens-refreshed', handleTokenRefresh);
    return () => {
      window.removeEventListener('auth:logout', handleLogout);
      window.removeEventListener('auth:tokens-refreshed', handleTokenRefresh);
    };
  }, []);

  useEffect(() => {
    const savedToken = localStorage.getItem('accessToken');
    if (savedToken) {
      setToken(savedToken);
      authApi.getCurrentUser()
        .then((userData) => {
          setUser(userData);
          walletApi
            .claimDailyBonus(userData.id)
            .then((bonus) => notifyWalletUpdated(bonus.balance))
            .catch(() => {});
        })
        .catch(() => {
          clearAuthTokens();
          setToken(null);
        })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = (accessToken: string, refreshToken: string, newUser: User) => {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    setToken(accessToken);
    setUser(newUser);
    walletApi
      .claimDailyBonus(newUser.id)
      .then((bonus) => notifyWalletUpdated(bonus.balance))
      .catch(() => {});
  };

  const logout = () => {
    const accessToken = localStorage.getItem('accessToken') || localStorage.getItem('token');
    const refreshToken = localStorage.getItem('refreshToken');
    void authApi.logout(refreshToken, accessToken).catch((error: unknown) => {
      console.warn('Не удалось завершить сессию на сервере', error);
    });
    clearAuthTokens();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, setUser, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
