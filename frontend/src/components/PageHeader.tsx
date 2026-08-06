import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { theme } from '../theme';

interface PageHeaderProps {
  title: string;
  backTo?: string;
  backLabel?: string;
  action?: React.ReactNode;
  style?: React.CSSProperties;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ title, backTo = '/', backLabel = '← На главную', action, style }) => (
  <div style={{ marginBottom: '24px', ...style }}>
    {backTo && (
      <Link to={backTo} style={backLinkStyle}>
        <ArrowLeft size={16} />
        {backLabel}
      </Link>
    )}
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
      <h1 className="page-title" style={titleStyle}>{title}</h1>
      {action}
    </div>
  </div>
);

const backLinkStyle: React.CSSProperties = {
  color: theme.colors.accentLight,
  textDecoration: 'none',
  fontSize: '14px',
  display: 'inline-flex',
  alignItems: 'center',
  gap: '6px',
  transition: 'opacity 0.2s',
};

const titleStyle: React.CSSProperties = {
  color: theme.colors.text,
  fontSize: '28px',
  fontWeight: 700,
  margin: 0,
};
