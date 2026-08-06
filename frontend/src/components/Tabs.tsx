import React from 'react';
import { theme } from '../theme';

interface Tab {
  key: string;
  label: string;
  count?: number;
}

interface TabsProps {
  tabs: Tab[];
  activeTab: string;
  onChange: (key: string) => void;
  style?: React.CSSProperties;
}

export const Tabs: React.FC<TabsProps> = ({ tabs, activeTab, onChange, style }) => (
  <div style={{ display: 'flex', gap: '4px', borderBottom: `1px solid ${theme.colors.border}`, ...style }}>
    {tabs.map((tab) => {
      const isActive = activeTab === tab.key;
      return (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          style={{
            padding: '12px 24px',
            background: isActive
              ? 'linear-gradient(180deg, rgba(34,211,238,0.15) 0%, transparent 100%)'
              : 'transparent',
            border: 'none',
            borderBottom: isActive ? '2px solid #22d3ee' : '2px solid transparent',
            color: isActive ? '#22d3ee' : theme.colors.textMuted,
            fontSize: '14px',
            fontWeight: isActive ? 700 : 500,
            cursor: 'pointer',
            transition: 'all 0.2s',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          {tab.label}
          {tab.count !== undefined && (
            <span style={{
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '12px',
              fontWeight: 700,
              backgroundColor: isActive ? 'rgba(34,211,238,0.2)' : 'rgba(255,255,255,0.1)',
              color: isActive ? '#22d3ee' : theme.colors.textMuted,
            }}>
              {tab.count}
            </span>
          )}
        </button>
      );
    })}
  </div>
);
