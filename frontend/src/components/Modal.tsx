import React, { useEffect } from 'react';
import { Button } from './Button';
import { theme } from '../theme';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'primary';
}

export const Modal: React.FC<ModalProps> = ({ 
  isOpen, 
  onClose, 
  onConfirm, 
  title, 
  message,
  confirmText = 'Подтвердить',
  cancelText = 'Отмена',
  variant = 'danger'
}) => {
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.icon}>
          {variant === 'danger' ? '⚠️' : 'ℹ️'}
        </div>
        <h3 style={styles.title}>{title}</h3>
        <p style={styles.message}>{message}</p>
        <div style={styles.buttons}>
          <Button variant="outline" onClick={onClose}>
            {cancelText}
          </Button>
          <Button 
            variant={variant} 
            onClick={() => { onConfirm(); onClose(); }}
          >
            {confirmText}
          </Button>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0, 0, 0, 0.7)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    animation: 'fadeIn 0.2s ease',
  },
  modal: {
    background: 'linear-gradient(160deg, #0e7490 0%, #065F46 50%, #164e63 100%)',
    borderRadius: '20px',
    padding: '32px',
    maxWidth: '400px',
    width: '90%',
    textAlign: 'center',
    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.5)',
    animation: 'slideUp 0.3s ease',
  },
  icon: {
    fontSize: '48px',
    marginBottom: '16px',
  },
  title: {
    color: '#fff',
    fontSize: '20px',
    fontWeight: 700,
    margin: '0 0 12px 0',
  },
  message: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: '15px',
    margin: '0 0 24px 0',
    lineHeight: 1.5,
  },
  buttons: {
    display: 'flex',
    gap: '12px',
    justifyContent: 'center',
  },
};
