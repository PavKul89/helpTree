import React from 'react';

interface SettingsSectionProps {
  id: string;
  eyebrow: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

/**
 * Shared card shell for every settings subsection.
 */
export const SettingsSection: React.FC<SettingsSectionProps> = ({
  id,
  eyebrow,
  title,
  description,
  icon,
  children,
  className = '',
}) => {
  const titleId = `settings-${id}-title`;

  return (
    <section
      className={`settings-section ${className}`.trim()}
      aria-labelledby={titleId}
    >
      <header className="settings-section-head">
        <span className="settings-section-icon" aria-hidden="true">
          {icon}
        </span>
        <div className="settings-section-copy">
          <span className="settings-eyebrow">{eyebrow}</span>
          <h2 id={titleId} className="settings-section-title" tabIndex={-1}>
            {title}
          </h2>
          <p>{description}</p>
        </div>
      </header>
      <div className="settings-section-body">{children}</div>
    </section>
  );
};
