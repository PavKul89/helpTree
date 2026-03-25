export const theme = {
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
};

export type Theme = typeof theme;
