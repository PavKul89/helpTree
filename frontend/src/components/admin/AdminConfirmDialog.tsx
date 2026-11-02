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
  onConfirm,
  onClose,
}) => (
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
        disabled={busy}
      >
        {busy ? <Loader2 size={16} className="admin-spinner" aria-hidden="true" /> : <Check size={16} aria-hidden="true" />}
        {busy ? 'Выполняется…' : confirmText}
      </button>
    </div>
  </AdminDialog>
);
