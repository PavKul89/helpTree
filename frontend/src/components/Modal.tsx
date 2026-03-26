import React, { useEffect, useState } from 'react';
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
  const [isClosing, setIsClosing] = useState(false);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (isOpen && !isVisible) {
      setIsVisible(true);
    }
    if (!isOpen && isVisible && !isClosing) {
      setIsClosing(true);
      setTimeout(() => {
        setIsVisible(false);
        setIsClosing(false);
      }, 200);
    }
  }, [isOpen, isVisible, isClosing]);

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      onClose();
    }, 200);
  };

  const handleConfirm = () => {
    setIsClosing(true);
    setTimeout(() => {
      onConfirm();
      onClose();
      setIsClosing(false);
      setIsVisible(false);
    }, 200);
  };

  if (!isVisible) return null;

  return (
    <div 
      style={{
        ...styles.overlay,
        opacity: isClosing ? 0 : 1,
        pointerEvents: isClosing ? 'none' : 'auto',
      }} 
      onClick={handleClose}
    >
      <div 
        style={{
          ...styles.modal,
          transform: isClosing ? 'scale(0.9) translateY(10px)' : 'scale(1) translateY(0)',
          opacity: isClosing ? 0 : 1,
        }} 
        onClick={(e) => e.stopPropagation()}
      >
        <div style={styles.icon}>
          {variant === 'danger' ? '⚠️' : 'ℹ️'}
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
    background: 'rgba(0, 0, 0, 0.7)',
    backdropFilter: 'blur(4px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    transition: 'opacity 0.2s ease',
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
    transition: 'transform 0.2s ease, opacity 0.2s ease',
  },
  icon: {
    fontSize: '48px',
    marginBottom: '16px',
    animation: 'bounce 0.5s ease',
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
