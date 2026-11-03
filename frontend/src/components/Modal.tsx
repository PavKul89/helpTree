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
  cancelText?: string | null;
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

  const hasCancel = Boolean(cancelText && cancelText.trim());

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
        <div style={styles.iconWrap}>
          <div style={styles.icon}>
            {variant === 'danger' ? <AlertTriangle size={42} strokeWidth={2.5} /> : <Info size={42} strokeWidth={2.5} />}
          </div>
        </div>
        <h3 id="confirmation-modal-title" style={styles.title}>{title}</h3>
        <p id="confirmation-modal-message" style={styles.message}>{message}</p>
        <div style={styles.buttons}>
          {hasCancel && (
            <Button variant="outline" onClick={handleClose} style={styles.cancelButton}>
              {cancelText}
            </Button>
          )}
          <Button 
            variant={variant} 
            onClick={handleConfirm}
            style={styles.confirmButton}
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
    background: 'rgba(0, 0, 0, 0.72)',
    backdropFilter: 'blur(8px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    animation: 'fadeIn 0.2s ease',
  },
  modal: {
    background: 'linear-gradient(155deg, rgba(12, 40, 31, 0.96), rgba(8, 31, 24, 0.98))',
    borderRadius: '22px',
    padding: '28px 26px 24px',
    maxWidth: '560px',
    width: 'min(560px, calc(100vw - 32px))',
    textAlign: 'center',
    boxShadow: '0 20px 60px rgba(0, 0, 0, 0.5)',
    border: '1px solid rgba(180, 225, 155, 0.18)',
    animation: 'slideUp 0.3s ease',
  },
  iconWrap: {
    display: 'flex',
    justifyContent: 'center',
    marginBottom: '18px',
  },
  icon: {
    display: 'grid',
    placeItems: 'center',
    width: '72px',
    height: '72px',
    borderRadius: '18px',
    color: '#f5b93c',
    background: 'rgba(200, 245, 106, 0.04)',
  },
  title: {
    color: '#f3f1e8',
    fontSize: 'clamp(2rem, 2.5vw, 2.5rem)',
    lineHeight: 1.2,
    letterSpacing: '-0.05em',
    fontWeight: 700,
    margin: '0 0 12px 0',
  },
  message: {
    color: '#c9d8cb',
    fontSize: '16px',
    margin: '0 0 20px 0',
    lineHeight: 1.5,
    whiteSpace: 'pre-line',
  },
  buttons: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '12px',
  },
  cancelButton: {
    minWidth: '112px',
    minHeight: '52px',
    borderRadius: '12px',
    borderColor: 'rgba(255,255,255,0.18)',
    background: 'rgba(255,255,255,0.02)',
    color: '#f3f1e8',
  },
  confirmButton: {
    minWidth: '140px',
    minHeight: '52px',
    borderRadius: '12px',
    boxShadow: '0 6px 18px rgba(239, 119, 119, 0.22)',
    fontSize: '15px',
    fontWeight: 700,
    padding: '0 22px',
  },
};
