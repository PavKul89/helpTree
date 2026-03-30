import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { User } from '../types';
import { authApi } from '../api/authApi';
import { notificationApi } from '../api/notificationApi';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (token: string, user: User) => void;
  logout: () => void;
  setUser: (user: User | null) => void;
  isLoading: boolean;
  newResponsesCount: number;
  checkNewResponses: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [newResponsesCount, setNewResponsesCount] = useState(0);

  const checkNewResponses = async () => {
    try {
      const count = await notificationApi.getNewResponsesCount();
      setNewResponsesCount(count);
    } catch (error) {
      console.error('Failed to check new responses:', error);
    }
  };

  useEffect(() => {
    const savedToken = localStorage.getItem('accessToken');
    if (savedToken) {
      setToken(savedToken);
      authApi.getCurrentUser()
        .then(setUser)
        .catch(() => {
          localStorage.removeItem('accessToken');
          setToken(null);
        })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      checkNewResponses();
    }
  }, [user]);

  const login = (newToken: string, newUser: User) => {
    localStorage.setItem('accessToken', newToken);
    setToken(newToken);
    setUser(newUser);
    setTimeout(() => {
      checkNewResponses();
    }, 1000);
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
    setToken(null);
    setUser(null);
    setNewResponsesCount(0);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, setUser, isLoading, newResponsesCount, checkNewResponses }}>
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
