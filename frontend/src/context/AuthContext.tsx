import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { User } from '../types';
import { authApi } from '../api/authApi';
import { clearAuthTokens, refreshAccessToken } from '../api/axios';
import { getAccessToken, setAccessToken } from '../api/tokenStore';
import { notifyWalletUpdated, walletApi } from '../api/walletApi';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (accessToken: string, user: User) => void;
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
      setToken(getAccessToken());
    };
    window.addEventListener('auth:logout', handleLogout);
    window.addEventListener('auth:tokens-refreshed', handleTokenRefresh);
    return () => {
      window.removeEventListener('auth:logout', handleLogout);
      window.removeEventListener('auth:tokens-refreshed', handleTokenRefresh);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    // No token in storage any more: restore the session from the HttpOnly
    // cookie. A 401 here simply means "anonymous" and must not redirect.
    refreshAccessToken({ silent: true })
      .then((accessToken) => {
        if (cancelled) return;
        setToken(accessToken);
        return authApi.getCurrentUser().then((userData) => {
          if (cancelled) return;
          setUser(userData);
          walletApi
            .claimDailyBonus(userData.id)
            .then((bonus) => notifyWalletUpdated(bonus.balance))
            .catch(() => {});
        });
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const login = (accessToken: string, newUser: User) => {
    setAccessToken(accessToken);
    setToken(accessToken);
    setUser(newUser);
    walletApi
      .claimDailyBonus(newUser.id)
      .then((bonus) => notifyWalletUpdated(bonus.balance))
      .catch(() => {});
  };

  const logout = () => {
    void authApi.logout().catch((error: unknown) => {
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
