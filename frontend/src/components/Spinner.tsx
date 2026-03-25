import React from 'react';
import { theme } from '../theme';

interface SpinnerProps {
  size?: 'small' | 'medium' | 'large';
  message?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({ size = 'medium', message }) => {
  const sizeValue = size === 'small' ? 24 : size === 'large' ? 48 : 32;
  
  return (
    <div style={styles.container}>
      <div style={{ ...styles.spinner, width: sizeValue, height: sizeValue }} />
      {message && <p style={styles.message}>{message}</p>}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '16px',
    padding: '40px',
  },
  spinner: {
    border: '3px solid rgba(34, 211, 238, 0.2)',
    borderTop: '3px solid #22d3ee',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  message: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    margin: 0,
  },
};
