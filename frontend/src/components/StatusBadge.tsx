import React from 'react';
import { getStatusColor, getStatusLabel } from '../utils/statusHelpers';

interface StatusBadgeProps {
  status: string;
  size?: 'small' | 'medium';
  type?: 'post' | 'help';
  style?: React.CSSProperties;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'small', type = 'post', style }) => {
  const color = getStatusColor(status, type);
  const label = getStatusLabel(status, type);
  const isSmall = size === 'small';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: isSmall ? '4px 10px' : '6px 14px',
        borderRadius: '20px',
        fontSize: isSmall ? '12px' : '14px',
        fontWeight: 600,
        color: '#fff',
        backgroundColor: color,
        ...style,
      }}
    >
      <span style={{
        width: isSmall ? 6 : 8,
        height: isSmall ? 6 : 8,
        borderRadius: '50%',
        backgroundColor: 'rgba(255,255,255,0.8)',
      }} />
      {label}
    </span>
  );
};

export { getStatusColor, getStatusLabel } from '../utils/statusHelpers';
