import React, { useEffect, useRef } from 'react';
import { Button } from './Button';
import { AlertTriangle, Info } from 'lucide-react';

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
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  const handleClose = (e?: React.MouseEvent) => {
    if (e) e.preventDefault();
    onClose();
  };

  const handleConfirm = () => {
    onConfirm();
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div 
      ref={overlayRef}
      style={styles.overlay} 
      onClick={handleClose}
    >
      <div 
        style={styles.modal} 
        onClick={(e) => e.stopPropagation()}
      >
        <div style={styles.icon}>
          {variant === 'danger' ? <AlertTriangle size={48} /> : <Info size={48} />}
        </div>
        <h3 style={styles.title}>{title}</h3>
        <p style={styles.message}>{message}</p>
        <div style={styles.buttons}>
          <Button variant="outline" onClick={handleClose}>
            {cancelText}
          </Button>
          <Button 
            variant={variant} 
            onClick={handleConfirm}
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
    background: 'rgba(0, 0, 0, 0.8)',
    backdropFilter: 'blur(8px)',
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
    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.5), 0 0 40px rgba(34, 211, 238, 0.2)',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    animation: 'slideUp 0.3s ease',
  },
  icon: {
    marginBottom: '16px',
    color: '#f59e0b',
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
