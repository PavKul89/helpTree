import React from 'react';
import { ArrowRight } from 'lucide-react';

export type SettingsSectionId = 'profile' | 'avatar' | 'account';

export interface SettingsNavigationItem {
  id: SettingsSectionId;
  label: string;
  description: string;
  icon: React.ReactNode;
}

interface SettingsNavigationProps {
  items: SettingsNavigationItem[];
  activeSection: SettingsSectionId;
  dirtySections: Record<SettingsSectionId, boolean>;
  onSelect: (section: SettingsSectionId) => void;
}

export const SettingsNavigation: React.FC<SettingsNavigationProps> = ({
  items,
  activeSection,
  dirtySections,
  onSelect,
}) => (
  <nav className="settings-nav" aria-label="Разделы настроек">
    <span className="settings-nav-title">Разделы</span>
    <div className="settings-nav-list">
      {items.map((item) => {
        const isActive = item.id === activeSection;
        const isDirty = dirtySections[item.id];

        return (
          <button
            key={item.id}
            type="button"
            className={`settings-nav-item${isActive ? ' is-active' : ''}`}
            onClick={() => onSelect(item.id)}
            aria-current={isActive ? 'page' : undefined}
          >
            <span className="settings-nav-icon" aria-hidden="true">
              {item.icon}
            </span>
            <span className="settings-nav-copy">
              <strong>
                {item.label}
                {isDirty && (
                  <>
                    <span className="settings-nav-dot" aria-hidden="true" />
                    <span className="settings-visually-hidden"> — есть несохранённые изменения</span>
                  </>
                )}
              </strong>
              <small>{item.description}</small>
            </span>
            <ArrowRight className="settings-nav-arrow" size={16} aria-hidden="true" />
          </button>
        );
      })}
    </div>
  </nav>
);
