import React from 'react';
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
    background: 'var(--forest-bg)',
    fontFamily: 'var(--forest-font)',
    color: 'var(--forest-text)',
  },
  overlay: {
    background: `
      radial-gradient(circle at 12% 0%, rgba(140, 203, 85, 0.10) 0%, rgba(7, 27, 19, 0) 45%),
      radial-gradient(circle at 88% 12%, rgba(200, 245, 106, 0.06) 0%, rgba(7, 27, 19, 0) 40%),
      #071B13
    `,
    minHeight: 'calc(100vh - 72px)',
  },
  content: {
    maxWidth: '1200px',
    margin: '0 auto',
    minHeight: 'calc(100vh - 72px)',
  },
};
