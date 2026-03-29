import React from 'react';
import { useTheme } from '../theme';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { theme, isDark } = useTheme();
  
  return (
    <div style={{
      ...styles.container,
      background: isDark 
        ? `linear-gradient(180deg, rgba(2, 44, 34, 0.95) 0%, rgba(6, 95, 70, 0.9) 50%, rgba(2, 44, 34, 0.95) 100%), url('https://images.unsplash.com/photo-1448375240586-882707db888b?w=1920&q=80')`
        : theme.gradients.background,
      color: theme.colors.text,
    }}>
      <div style={{
        ...styles.overlay,
        background: isDark ? 'rgba(0, 0, 0, 0.3)' : 'rgba(255, 255, 255, 0.5)',
      }}>
        {children}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundAttachment: 'fixed',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
  overlay: {
    minHeight: '100vh',
  },
};
