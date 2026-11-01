/**
 * Forest Premium palette.
 * Shared by the main page, navigation and footer so the whole shell
 * looks like one composition.
 */
export const forest = {
  colors: {
    bg: '#071B13',
    bgSecondary: '#0B2419',
    card: '#102D20',
    surface: '#F5F4EA',
    surfaceAlt: '#E9EFE2',

    lime: '#C8F56A',
    green: '#8CCB55',

    text: '#F5F8F1',
    textSecondary: '#B7C5B8',
    textMuted: '#829688',

    border: 'rgba(180, 225, 155, 0.20)',
    borderStrong: 'rgba(200, 245, 106, 0.45)',

    error: '#FF7777',

    textOnLight: '#0B2419',
    textOnLightSecondary: '#4E6458',
    textOnLightMuted: '#6B7F70',
  },
  fonts: {
    base: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
  },
  radius: {
    sm: '10px',
    md: '14px',
    lg: '20px',
    xl: '28px',
  },
  shadows: {
    card: '0 18px 40px rgba(0, 0, 0, 0.22)',
    glow: '0 0 0 1px rgba(200, 245, 106, 0.18), 0 12px 32px rgba(0, 0, 0, 0.28)',
  },
} as const;

export type Forest = typeof forest;
