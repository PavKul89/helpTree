export const theme = {
  colors: {
    primary: '#8CCB55',
    primaryLight: '#C8F56A',
    primaryLighter: '#D7FF82',
    primaryDark: '#6FAE4A',
    primaryDarker: '#4E7A34',

    accent: '#C8F56A',
    accentLight: '#D7FF82',
    accentDark: '#A8D95A',

    background: '#071B13',
    backgroundLight: '#0B2419',
    backgroundCard: '#102D20',

    text: '#F5F8F1',
    textSecondary: '#B7C5B8',
    textMuted: '#829688',

    border: 'rgba(180, 225, 155, 0.2)',
    borderFocus: '#C8F56A',

    error: '#FF7777',
    errorLight: '#FFB3B3',
    success: '#8CCB55',
    warning: '#F5B85C',
  },
  select: {
    backgroundColor: '#102D20',
    color: '#F5F8F1',
    border: '1px solid rgba(180, 225, 155, 0.2)',
  },
  
  shadows: {
    card: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    cardHover: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    button: '0 4px 14px -3px rgba(200, 245, 106, 0.35), 0 0 20px rgba(200, 245, 106, 0.15)',
    buttonHover: '0 6px 20px -3px rgba(200, 245, 106, 0.45), 0 0 30px rgba(200, 245, 106, 0.22)',
  },
  
  gradients: {
    background: 'linear-gradient(180deg, #071B13 0%, #0B2419 50%, #071B13 100%)',
    card: 'linear-gradient(160deg, #102D20 0%, #0B2419 100%)',
    button: 'linear-gradient(135deg, #C8F56A 0%, #8CCB55 100%)',
    buttonHover: 'linear-gradient(135deg, #D7FF82 0%, #9ADB64 100%)',
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
