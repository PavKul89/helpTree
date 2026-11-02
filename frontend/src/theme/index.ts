export const theme = {
  colors: {
    primary: 'var(--forest-green)',
    primaryLight: 'var(--forest-lime)',
    primaryLighter: 'var(--forest-lime-hover)',
    primaryDark: 'var(--forest-green)',
    primaryDarker: '#4E7A34',

    accent: 'var(--forest-lime)',
    accentLight: 'var(--forest-lime-hover)',
    accentDark: '#A8D95A',

    background: 'var(--forest-bg)',
    backgroundLight: 'var(--forest-bg-secondary)',
    backgroundCard: 'var(--forest-surface)',

    text: 'var(--forest-text)',
    textSecondary: 'var(--forest-text-muted)',
    textMuted: 'var(--forest-text-subtle)',

    border: 'var(--forest-border)',
    borderFocus: 'var(--forest-lime)',

    error: 'var(--forest-error)',
    errorLight: '#FFB3B3',
    success: 'var(--forest-success)',
    warning: 'var(--forest-warning)',
  },
  select: {
    backgroundColor: 'var(--forest-surface)',
    color: 'var(--forest-text)',
    border: '1px solid var(--forest-border)',
  },
  
  shadows: {
    card: 'var(--forest-shadow-card)',
    cardHover: 'var(--forest-shadow-card-hover)',
    button: 'var(--forest-shadow-button)',
    buttonHover: 'var(--forest-shadow-button-hover)',
  },
  
  gradients: {
    background: 'linear-gradient(180deg, var(--forest-bg) 0%, var(--forest-bg-secondary) 50%, var(--forest-bg) 100%)',
    card: 'linear-gradient(160deg, var(--forest-surface) 0%, var(--forest-bg-secondary) 100%)',
    button: 'linear-gradient(135deg, var(--forest-lime) 0%, var(--forest-green) 100%)',
    buttonHover: 'linear-gradient(135deg, var(--forest-lime-hover) 0%, var(--forest-green-hover) 100%)',
  },
  
  borderRadius: {
    sm: 'var(--forest-radius-sm)',
    md: 'var(--forest-radius-md)',
    lg: 'var(--forest-radius-lg)',
    xl: 'var(--forest-radius-xl)',
  },
  
  spacing: {
    xs: 'var(--forest-space-1)',
    sm: 'var(--forest-space-2)',
    md: 'var(--forest-space-4)',
    lg: 'var(--forest-space-5)',
    xl: 'var(--forest-space-6)',
  },
};

export type Theme = typeof theme;
