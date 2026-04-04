import React from 'react';
import { theme } from '../theme';
import { Footer } from './Footer';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  return (
    <div style={styles.container}>
      <div style={styles.overlay}>
        {children}
        <Footer />
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    background: `
      linear-gradient(180deg, rgba(2, 44, 34, 0.95) 0%, rgba(6, 95, 70, 0.9) 50%, rgba(2, 44, 34, 0.95) 100%),
      url('https://images.unsplash.com/photo-1448375240586-882707db888b?w=1920&q=80')
    `,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundAttachment: 'fixed',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    color: theme.colors.text,
  },
  overlay: {
    background: 'rgba(0, 0, 0, 0.3)',
    minHeight: '100vh',
  },
};
