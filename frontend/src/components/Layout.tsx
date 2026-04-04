import React from 'react';
import { theme } from '../theme';
import { Footer } from './Footer';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  return (
    <>
      <div style={styles.container}>
        <div style={styles.overlay}>
          <div style={styles.content}>
            {children}
          </div>
        </div>
        <Footer />
      </div>
    </>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    background: 'linear-gradient(180deg, rgba(1, 28, 18, 0.98) 0%, rgba(2, 44, 34, 0.98) 50%, rgba(1, 28, 18, 0.98) 100%)',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    color: theme.colors.text,
  },
  overlay: {
    background: `
      linear-gradient(180deg, rgba(2, 44, 34, 0.95) 0%, rgba(6, 95, 70, 0.9) 50%, rgba(2, 44, 34, 0.95) 100%),
      url('https://images.unsplash.com/photo-1448375240586-882707db888b?w=1920&q=80')
    `,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundAttachment: 'fixed',
  },
  content: {
    maxWidth: '1200px',
    margin: '0 auto',
    minHeight: 'calc(100vh - 80px)',
    boxShadow: '0 0 80px rgba(0,0,0,0.5)',
  },
};
