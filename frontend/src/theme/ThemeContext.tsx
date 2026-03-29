import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

export const darkTheme = {
  colors: {
    primary: '#059669',
    primaryLight: '#10B981',
    primaryLighter: '#34D399',
    primaryDark: '#047857',
    primaryDarker: '#065F46',
    
    accent: '#06b6d4',
    accentLight: '#22d3ee',
    accentDark: '#0891b2',
    
    background: '#022c22',
    backgroundLight: '#064E3B',
    backgroundCard: '#065F46',
    
    text: '#ffffff',
    textSecondary: 'rgba(255, 255, 255, 0.85)',
    textMuted: 'rgba(255, 255, 255, 0.7)',
    
    border: 'rgba(255, 255, 255, 0.2)',
    borderFocus: '#38bdf8',
    
    error: '#EF4444',
    errorLight: '#FCA5A5',
    success: '#10B981',
    warning: '#F59E0B',
  },
  select: {
    backgroundColor: '#065F46',
    color: '#ffffff',
    border: '1px solid rgba(255, 255, 255, 0.2)',
  },
  
  shadows: {
    card: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    cardHover: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    button: '0 4px 14px -3px rgba(6, 182, 212, 0.5), 0 0 20px rgba(6, 182, 212, 0.2)',
    buttonHover: '0 6px 20px -3px rgba(34, 211, 238, 0.6), 0 0 30px rgba(34, 211, 238, 0.3)',
  },
  
  gradients: {
    background: 'linear-gradient(180deg, #0c4a6e 0%, #022c22 50%, #065F46 100%)',
    card: 'linear-gradient(160deg, #047857 0%, #0e7490 50%, #164e63 100%)',
    button: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
    buttonHover: 'linear-gradient(135deg, #22d3ee 0%, #06b6d4 100%)',
  },
  
  borderRadius: {
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '20px',
  },
  
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
  },
  mode: 'dark' as const,
};

export const lightTheme = {
  colors: {
    primary: '#059669',
    primaryLight: '#10B981',
    primaryLighter: '#34D399',
    primaryDark: '#047857',
    primaryDarker: '#065F46',
    
    accent: '#0891b2',
    accentLight: '#06b6d4',
    accentDark: '#0e7490',
    
    background: '#f0fdfa',
    backgroundLight: '#ccfbf1',
    backgroundCard: '#ffffff',
    
    text: '#134e4a',
    textSecondary: '#334155',
    textMuted: '#64748b',
    
    border: 'rgba(0, 0, 0, 0.1)',
    borderFocus: '#06b6d4',
    
    error: '#dc2626',
    errorLight: '#fca5a5',
    success: '#059669',
    warning: '#d97706',
  },
  select: {
    backgroundColor: '#ffffff',
    color: '#134e4a',
    border: '1px solid rgba(0, 0, 0, 0.1)',
  },
  
  shadows: {
    card: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    cardHover: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    button: '0 4px 14px -3px rgba(6, 182, 212, 0.4), 0 0 15px rgba(6, 182, 212, 0.15)',
    buttonHover: '0 6px 20px -3px rgba(6, 182, 212, 0.5), 0 0 25px rgba(6, 182, 212, 0.2)',
  },
  
  gradients: {
    background: 'linear-gradient(180deg, #f0fdfa 0%, #ccfbf1 50%, #99f6e4 100%)',
    card: 'linear-gradient(160deg, #ffffff 0%, #f0fdfa 50%, #ccfbf1 100%)',
    button: 'linear-gradient(135deg, #0891b2 0%, #06b6d4 100%)',
    buttonHover: 'linear-gradient(135deg, #06b6d4 0%, #22d3ee 100%)',
  },
  
  borderRadius: {
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '20px',
  },
  
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
  },
  mode: 'light' as const,
};

type ThemeType = typeof darkTheme;

interface ThemeContextType {
  theme: ThemeType;
  isDark: boolean;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isDark, setIsDark] = useState(() => {
    const saved = localStorage.getItem('theme');
    return saved ? saved === 'dark' : true;
  });

  useEffect(() => {
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
  }, [isDark]);

  const toggleTheme = () => setIsDark(!isDark);

  const theme = isDark ? darkTheme : lightTheme;

  return (
    <ThemeContext.Provider value={{ theme, isDark, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
