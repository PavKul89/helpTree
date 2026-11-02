import React from 'react';

interface AdminAvatarProps {
  name: string;
  avatarUrl?: string;
  size?: 'small' | 'medium' | 'large';
}

const getInitials = (name: string): string =>
  name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();

export const AdminAvatar: React.FC<AdminAvatarProps> = ({ name, avatarUrl, size = 'medium' }) => (
  <span
    className={`admin-avatar admin-avatar--${size}`}
    style={avatarUrl ? { backgroundImage: `url(${avatarUrl})` } : undefined}
    aria-hidden="true"
  >
    {!avatarUrl && getInitials(name)}
  </span>
);
