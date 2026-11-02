/**
 * CSS-variable-backed Forest Premium tokens for the shared application shell.
 */
export const forest = {
  colors: {
    bg: 'var(--forest-bg)',
    bgSecondary: 'var(--forest-bg-secondary)',
    card: 'var(--forest-surface)',
    surface: 'var(--forest-light-surface)',
    surfaceAlt: 'var(--forest-light-surface-secondary)',

    lime: 'var(--forest-lime)',
    green: 'var(--forest-green)',

    text: 'var(--forest-text)',
    textSecondary: 'var(--forest-text-muted)',
    textMuted: 'var(--forest-text-subtle)',

    border: 'var(--forest-border)',
    borderStrong: 'var(--forest-border-strong)',

    error: 'var(--forest-error)',

    textOnLight: 'var(--forest-bg-secondary)',
    textOnLightSecondary: '#4E6458',
    textOnLightMuted: '#6B7F70',
  },
  fonts: {
    base: 'var(--forest-font)',
  },
  radius: {
    sm: 'var(--forest-radius-sm)',
    md: 'var(--forest-radius-md)',
    lg: 'var(--forest-radius-lg)',
    xl: 'var(--forest-radius-xl)',
  },
  shadows: {
    card: 'var(--forest-shadow-card)',
    glow: 'var(--forest-shadow-glow)',
  },
} as const;

export type Forest = typeof forest;
