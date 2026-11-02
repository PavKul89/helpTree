import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TreeDeciduous, Users, Heart, FileText, LoaderCircle } from 'lucide-react';
import { statsApi } from '../api/statsApi';
import './Footer.css';

interface NavItem {
  label: string;
  path: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Главная', path: '/' },
  { label: 'Все заявки', path: '/posts' },
  { label: 'Карта', path: '/map' },
  { label: 'Граф помощи', path: '/graph' },
  { label: 'Достижения', path: '/achievements' },
  { label: 'Безопасность', path: '/safety' },
  { label: 'Мои заказы', path: '/my-orders' },
];

interface Stats {
  totalUsers: number;
  totalHelps: number;
  activePosts: number;
}

export const Footer: React.FC = () => {
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStats = async () => {
      try {
        const data = await statsApi.getStats();
        setStats(data);
      } catch (err) {
        console.error('Error loading stats:', err);
      } finally {
        setLoading(false);
      }
    };
    loadStats();
  }, []);

  return (
    <footer className="footer">
      <div className="footer-container">
        <div className="footer-grid">
          <div className="footer-brand">
            <div className="footer-logo">
              <span className="footer-logo-mark" aria-hidden="true">
                <TreeDeciduous size={22} strokeWidth={1.8} />
              </span>
              <span className="footer-logo-text">
                help<span>Tree</span>
              </span>
            </div>
            <p className="footer-description">
              Платформа взаимопомощи для сообщества. Помогайте другим и получайте помощь в ответ.
            </p>
          </div>

          <div className="footer-nav">
            <h4 className="footer-nav-title">Навигация</h4>
            <div className="footer-nav-links">
              {NAV_ITEMS.map((item) => (
                <Link key={item.path} to={item.path} className="footer-nav-link">
                  {item.label}
                </Link>
              ))}
            </div>
          </div>

          <div className="footer-stats">
            <h4 className="footer-stats-title">Сообщество</h4>
            <div className="footer-stats-list">
              <div className="footer-stat-item">
                <Users size={18} aria-hidden="true" />
                <span className="footer-stat-value">
                  {loading ? (
                    <LoaderCircle size={14} className="footer-spinner" />
                  ) : (
                    stats?.totalUsers.toLocaleString('ru-RU') ?? '—'
                  )}
                </span>
                <span className="footer-stat-label">участников</span>
              </div>
              <div className="footer-stat-item">
                <Heart size={18} aria-hidden="true" />
                <span className="footer-stat-value">
                  {loading ? (
                    <LoaderCircle size={14} className="footer-spinner" />
                  ) : (
                    stats?.totalHelps.toLocaleString('ru-RU') ?? '—'
                  )}
                </span>
                <span className="footer-stat-label">актов помощи</span>
              </div>
              <div className="footer-stat-item">
                <FileText size={18} aria-hidden="true" />
                <span className="footer-stat-value">
                  {loading ? (
                    <LoaderCircle size={14} className="footer-spinner" />
                  ) : (
                    stats?.activePosts.toLocaleString('ru-RU') ?? '—'
                  )}
                </span>
                <span className="footer-stat-label">открытых заявок</span>
              </div>
            </div>
          </div>
        </div>

        <div className="footer-bottom">
          <div className="footer-divider" />
          <div className="footer-copyright">
            © 2026 helpTree. Платформа взаимопомощи.
          </div>
        </div>
      </div>
    </footer>
  );
};
