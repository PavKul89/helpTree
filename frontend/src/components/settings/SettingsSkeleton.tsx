import React from 'react';

const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <span className={`settings-skeleton-block ${className}`.trim()} aria-hidden="true" />
);

export const SettingsSkeleton: React.FC = () => (
  <div className="settings-page" aria-busy="true">
    <p className="settings-visually-hidden" role="status">
      Загружаем настройки…
    </p>
    <div className="settings-skeleton-hero">
      <div className="settings-skeleton-hero-copy">
        <SkeletonBlock className="is-eyebrow" />
        <SkeletonBlock className="is-title" />
        <SkeletonBlock className="is-text" />
        <SkeletonBlock className="is-text is-short" />
      </div>
      <SkeletonBlock className="is-account" />
    </div>
    <div className="settings-layout">
      <div className="settings-skeleton-nav">
        <SkeletonBlock className="is-nav-title" />
        {[0, 1, 2].map((item) => (
          <div className="settings-skeleton-nav-item" key={item}>
            <SkeletonBlock className="is-nav-icon" />
            <div>
              <SkeletonBlock className="is-nav-label" />
              <SkeletonBlock className="is-nav-description" />
            </div>
          </div>
        ))}
      </div>
      <div className="settings-skeleton-section">
        <div className="settings-skeleton-section-head">
          <SkeletonBlock className="is-section-icon" />
          <div>
            <SkeletonBlock className="is-eyebrow" />
            <SkeletonBlock className="is-section-title" />
            <SkeletonBlock className="is-text is-short" />
          </div>
        </div>
        <div className="settings-skeleton-form">
          {[0, 1, 2, 3].map((item) => (
            <div className="settings-skeleton-field" key={item}>
              <SkeletonBlock className="is-label" />
              <SkeletonBlock className="is-input" />
            </div>
          ))}
        </div>
        <div className="settings-skeleton-footer">
          <SkeletonBlock className="is-footer-text" />
          <SkeletonBlock className="is-footer-button" />
        </div>
      </div>
    </div>
  </div>
);
