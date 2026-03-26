import React from 'react';
import { Link } from 'react-router-dom';
import { theme } from '../theme';

interface AvatarProps {
  name: string;
  avatarUrl?: string;
  size?: 'small' | 'medium' | 'large';
  showName?: boolean;
  withRating?: number;
  clickable?: boolean;
  userId?: number;
}

const SIZES = {
  small: { container: 28, font: 11 },
  medium: { container: 40, font: 14 },
  large: { container: 64, font: 22 },
};

const COLORS = [
  '#22d3ee', '#06b6d4', '#0891b2', '#0e7490',
  '#10b981', '#059669', '#047857', '#065f46',
  '#f59e0b', '#d97706', '#f97316', '#ea580c',
  '#8b5cf6', '#7c3aed', '#6d28d9', '#5b21b6',
  '#ec4899', '#db2777', '#be185d', '#9d174d',
];

const getColorFromName = (name: string): string => {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return COLORS[Math.abs(hash) % COLORS.length];
};

const getInitials = (name: string): string => {
  return name
    .split(' ')
    .map(part => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
};

export const Avatar: React.FC<AvatarProps> = ({ 
  name, 
  avatarUrl,
  size = 'medium', 
  showName = false,
  withRating,
  clickable = false,
  userId
}) => {
  const sizeConfig = SIZES[size];
  const backgroundColor = getColorFromName(name);
  const initials = getInitials(name);

  const containerStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: size === 'small' ? '8px' : '10px',
  };

  const avatarStyle: React.CSSProperties = {
    width: sizeConfig.container,
    height: sizeConfig.container,
    borderRadius: '50%',
    background: avatarUrl ? 'transparent' : `linear-gradient(135deg, ${backgroundColor} 0%, ${backgroundColor}cc 100%)`,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: sizeConfig.font,
    fontWeight: 700,
    color: '#022c22',
    flexShrink: 0,
    overflow: 'hidden',
    backgroundImage: avatarUrl ? `url(${avatarUrl})` : undefined,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    cursor: clickable ? 'pointer' : 'default',
    transition: 'transform 0.2s ease, box-shadow 0.2s ease',
  };

  const nameStyle: React.CSSProperties = {
    color: theme.colors.text,
    fontSize: size === 'small' ? '13px' : '15px',
    fontWeight: 500,
    cursor: clickable ? 'pointer' : 'default',
    transition: 'color 0.2s ease',
  };

  const ratingStyle: React.CSSProperties = {
    color: '#fbbf24',
    fontSize: '12px',
    fontWeight: 600,
    marginLeft: '2px',
  };

  const handleMouseEnter = (e: React.MouseEvent<HTMLDivElement>) => {
    if (clickable) {
      e.currentTarget.style.transform = 'scale(1.05)';
      e.currentTarget.style.boxShadow = '0 0 10px rgba(34, 211, 238, 0.5)';
    }
  };

  const handleMouseLeave = (e: React.MouseEvent<HTMLDivElement>) => {
    if (clickable) {
      e.currentTarget.style.transform = 'scale(1)';
      e.currentTarget.style.boxShadow = 'none';
    }
  };

  const avatarContent = (
    <>
      <div 
        style={avatarStyle}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        {!avatarUrl && initials}
      </div>
      {showName && (
        <span style={clickable ? { ...nameStyle, color: '#22d3ee' } : nameStyle}>
          {name}
          {withRating !== undefined && <span style={ratingStyle}> ★ {withRating}</span>}
        </span>
      )}
    </>
  );

  if (clickable && userId) {
    return (
      <Link to={`/profile/${userId}`} style={{ textDecoration: 'none' }}>
        <div style={containerStyle}>
          {avatarContent}
        </div>
      </Link>
    );
  }

  return (
    <div style={containerStyle}>
      {avatarContent}
    </div>
  );
};