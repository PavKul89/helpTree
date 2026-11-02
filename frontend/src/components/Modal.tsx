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
  const modalRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!isOpen) return;

    previousFocusRef.current = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusable = modalRef.current?.querySelector<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
    );
    (focusable ?? modalRef.current)?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }

      if (event.key !== 'Tab' || !modalRef.current) return;
      const items = Array.from(
        modalRef.current.querySelectorAll<HTMLElement>(
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
        )
      );
      if (items.length === 0) {
        event.preventDefault();
        modalRef.current.focus();
        return;
      }

      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
      previousFocusRef.current?.focus();
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
        ref={modalRef}
        style={styles.modal} 
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmation-modal-title"
        aria-describedby="confirmation-modal-message"
        tabIndex={-1}
      >
        <div style={styles.icon}>
          {variant === 'danger' ? <AlertTriangle size={48} /> : <Info size={48} />}
        </div>
        <h3 id="confirmation-modal-title" style={styles.title}>{title}</h3>
        <p id="confirmation-modal-message" style={styles.message}>{message}</p>
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
    background: 'linear-gradient(160deg, var(--forest-surface) 0%, var(--forest-bg-secondary) 100%)',
    borderRadius: 'var(--forest-radius-lg)',
    padding: 'clamp(20px, 5vw, 32px)',
    maxWidth: '400px',
    width: 'min(400px, calc(100vw - 32px))',
    textAlign: 'center',
    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.5)',
    border: '1px solid var(--forest-border)',
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
