import React, { createContext, useContext, useState, useCallback } from 'react';

type ToastType = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, type: ToastType = 'info') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3000);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div style={styles.container} aria-live="polite" aria-atomic="false">
        {toasts.map(toast => (
          <div
            key={toast.id}
            style={{ ...styles.toast, ...styles[toast.type] }}
            role={toast.type === 'error' ? 'alert' : 'status'}
          >
            <span style={styles.icon}>
              {toast.type === 'success' ? '✓' : toast.type === 'error' ? '✕' : 'ℹ'}
            </span>
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    position: 'fixed',
    bottom: '24px',
    right: '24px',
    width: 'min(400px, calc(100vw - 32px))',
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    zIndex: 9999,
  },
  toast: {
    padding: '14px 20px',
    borderRadius: 'var(--forest-radius-md)',
    color: 'var(--forest-text)',
    fontSize: '14px',
    fontWeight: 500,
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    animation: 'slideIn 0.3s ease',
    boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
    whiteSpace: 'pre-wrap',
    width: '100%',
  },
  success: {
    background: 'linear-gradient(135deg, var(--forest-surface-raised) 0%, var(--forest-surface) 100%)',
    border: '1px solid var(--forest-green)',
  },
  error: {
    background: 'linear-gradient(135deg, var(--forest-surface-raised) 0%, var(--forest-surface) 100%)',
    border: '1px solid var(--forest-error)',
  },
  info: {
    background: 'linear-gradient(135deg, var(--forest-surface-raised) 0%, var(--forest-surface) 100%)',
    border: '1px solid var(--forest-border-strong)',
  },
  icon: {
    fontSize: '16px',
    fontWeight: 'bold',
  },
};