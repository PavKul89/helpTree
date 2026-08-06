import React from 'react';
import { theme } from '../theme';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  style?: React.CSSProperties;
}

export const Pagination: React.FC<PaginationProps> = ({ page, totalPages, onPageChange, style }) => {
  if (totalPages <= 1) return null;

  const pages: (number | '...')[] = [];
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (page > 3) pages.push('...');
    for (let i = Math.max(2, page - 1); i <= Math.min(totalPages - 1, page + 1); i++) {
      pages.push(i);
    }
    if (page < totalPages - 2) pages.push('...');
    pages.push(totalPages);
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '6px', marginTop: '24px', ...style }}>
      <button
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        style={navBtnStyle(page > 1)}
      >
        ‹
      </button>
      {pages.map((p, i) =>
        p === '...' ? (
          <span key={`dots-${i}`} style={dotsStyle}>…</span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            style={page === p ? activePageStyle : pageBtnStyle}
          >
            {p}
          </button>
        )
      )}
      <button
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
        style={navBtnStyle(page < totalPages)}
      >
        ›
      </button>
    </div>
  );
};

const baseBtn: React.CSSProperties = {
  minWidth: 36,
  height: 36,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  border: 'none',
  borderRadius: theme.borderRadius.sm,
  fontSize: '14px',
  fontWeight: 600,
  cursor: 'pointer',
  transition: 'all 0.2s',
};

const pageBtnStyle: React.CSSProperties = {
  ...baseBtn,
  backgroundColor: 'rgba(255,255,255,0.08)',
  color: theme.colors.textMuted,
};

const activePageStyle: React.CSSProperties = {
  ...baseBtn,
  background: theme.gradients.button,
  color: '#fff',
  boxShadow: theme.shadows.button,
};

const navBtnStyle = (enabled: boolean): React.CSSProperties => ({
  ...baseBtn,
  backgroundColor: enabled ? 'rgba(255,255,255,0.1)' : 'transparent',
  color: enabled ? theme.colors.text : 'rgba(255,255,255,0.3)',
  cursor: enabled ? 'pointer' : 'not-allowed',
  fontSize: '18px',
});

const dotsStyle: React.CSSProperties = {
  color: theme.colors.textMuted,
  fontSize: '14px',
  padding: '0 4px',
};
