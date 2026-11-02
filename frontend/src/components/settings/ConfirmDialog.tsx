import React, { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, Info, Loader2 } from 'lucide-react';

type ConfirmDialogTone = 'danger' | 'warning';

interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  busyLabel?: string;
  tone?: ConfirmDialogTone;
  isBusy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: React.ReactNode;
}

/**
 * Accessible confirmation dialog with a simple focus trap.
 * Used for discarding changes and for irreversible account actions.
 */
export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Отмена',
  busyLabel = 'Выполняем…',
  tone = 'warning',
  isBusy = false,
  onConfirm,
  onClose,
  children,
}) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const onCloseRef = useRef(onClose);
  const isBusyRef = useRef(isBusy);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    isBusyRef.current = isBusy;
  }, [isBusy]);

  useEffect(() => {
    if (!isOpen) return;

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusTimer = window.setTimeout(() => cancelRef.current?.focus(), 0);

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        if (!isBusyRef.current) onCloseRef.current();
        return;
      }

      if (event.key !== 'Tab' || !dialogRef.current) return;

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )
      ).filter((element) => element.offsetParent !== null);

      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

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
      window.clearTimeout(focusTimer);
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const titleId = 'settings-confirm-title';
  const descriptionId = 'settings-confirm-description';
  const Icon = tone === 'danger' ? AlertTriangle : Info;

  return createPortal(
    <div
      className="settings-dialog-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isBusy) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className={`settings-dialog settings-dialog--${tone}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
      >
        <span className="settings-dialog-icon" aria-hidden="true">
          <Icon size={24} />
        </span>
        <h2 id={titleId}>{title}</h2>
        <p id={descriptionId}>{description}</p>
        {children && <div className="settings-dialog-content">{children}</div>}
        <div className="settings-dialog-actions">
          <button
            ref={cancelRef}
            type="button"
            className="settings-button settings-button--secondary"
            onClick={onClose}
            disabled={isBusy}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`settings-button ${
              tone === 'danger' ? 'settings-button--danger' : 'settings-button--primary'
            }`}
            onClick={onConfirm}
            disabled={isBusy}
            aria-busy={isBusy}
          >
            {isBusy && <Loader2 className="settings-spinner" size={16} aria-hidden="true" />}
            {isBusy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
};
