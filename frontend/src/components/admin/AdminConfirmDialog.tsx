import React from 'react';
import { AlertTriangle, Check, Loader2 } from 'lucide-react';
import { AdminDialog } from './AdminDialog';

interface AdminConfirmDialogProps {
  open: boolean;
  title: string;
  description?: string;
  consequences?: string[];
  confirmText?: string;
  cancelText?: string;
  busy?: boolean;
  error?: string | null;
  tone?: 'danger' | 'default';
  /** Optional form field for the mandatory audit reason of critical actions. */
  reason?: string;
  onReasonChange?: (value: string) => void;
  reasonLabel?: string;
  reasonPlaceholder?: string;
  reasonHint?: string;
  reasonRequired?: boolean;
  /** Extra fields for actions that need more than a reason (for example email confirmation). */
  children?: React.ReactNode;
  onConfirm: () => void;
  onClose: () => void;
}

export const AdminConfirmDialog: React.FC<AdminConfirmDialogProps> = ({
  open,
  title,
  description,
  consequences,
  confirmText = 'Подтвердить',
  cancelText = 'Отмена',
  busy = false,
  error,
  tone = 'danger',
  reason,
  onReasonChange,
  reasonLabel = 'Причина (обязательно)',
  reasonPlaceholder,
  reasonHint,
  reasonRequired = false,
  children,
  onConfirm,
  onClose,
}) => {
  const reasonMissing = reasonRequired && !reason?.trim();

  return (
    <AdminDialog
      open={open}
      title={title}
      description={description}
      onClose={onClose}
      busy={busy}
      tone={tone}
    >
      {consequences && consequences.length > 0 && (
        <ul className="admin-dialog-list">
          {consequences.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}

      {children}

      {onReasonChange && (
        <div className="admin-field admin-field--wide">
          <label htmlFor="admin-confirm-reason">
            {reasonLabel}
            <span className="admin-visually-hidden">, обязательное поле</span>
          </label>
          <textarea
            id="admin-confirm-reason"
            value={reason ?? ''}
            onChange={(event) => onReasonChange(event.target.value)}
            placeholder={reasonPlaceholder}
            rows={3}
            maxLength={1000}
            required={reasonRequired}
            aria-invalid={reasonMissing ? true : undefined}
          />
          {reasonHint && <span className="admin-field-hint">{reasonHint}</span>}
        </div>
      )}

      {error && (
        <div className="admin-inline-alert" role="alert">
          <AlertTriangle size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="admin-dialog-actions">
        <button type="button" className="admin-button admin-button--ghost" onClick={onClose} disabled={busy}>
          {cancelText}
        </button>
        <button
          type="button"
          className={`admin-button ${tone === 'danger' ? 'admin-button--danger' : 'admin-button--primary'}`}
          onClick={onConfirm}
          disabled={busy || reasonMissing}
        >
          {busy ? <Loader2 size={16} className="admin-spinner" aria-hidden="true" /> : <Check size={16} aria-hidden="true" />}
          {busy ? 'Выполняется…' : confirmText}
        </button>
      </div>
    </AdminDialog>
  );
};
