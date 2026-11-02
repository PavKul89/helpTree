import React, { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

interface AdminDialogProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  busy?: boolean;
  tone?: 'default' | 'danger';
  children: React.ReactNode;
}

/**
 * Accessible admin dialog: Escape and overlay close, body scroll lock,
 * focus moved into the dialog and restored to the trigger on close.
 */
export const AdminDialog: React.FC<AdminDialogProps> = ({
  open,
  title,
  description,
  onClose,
  busy = false,
  tone = 'default',
  children,
}) => {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const busyRef = useRef(busy);

  onCloseRef.current = onClose;
  busyRef.current = busy;

  useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busyRef.current) {
        event.preventDefault();
        onCloseRef.current();
        return;
      }

      if (event.key !== 'Tab' || !dialogRef.current) return;

      const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusable.length === 0) return;

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
    const focusTimer = window.setTimeout(() => {
      const target = dialogRef.current?.querySelector<HTMLElement>(
        'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), a[href]'
      );
      target?.focus();
    }, 30);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      window.clearTimeout(focusTimer);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div
      className="admin-dialog-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className={`admin-dialog${tone === 'danger' ? ' admin-dialog--danger' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
      >
        <div className="admin-dialog-head">
          <div>
            <h2 id={titleId}>{title}</h2>
            {description && <p id={descriptionId}>{description}</p>}
          </div>
          <button
            type="button"
            className="admin-icon-btn"
            onClick={onClose}
            disabled={busy}
            aria-label="Закрыть диалог"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>
        <div className="admin-dialog-body">{children}</div>
      </div>
    </div>,
    document.body
  );
};
