import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TreeDeciduous, Users, Heart, FileText, Loader2 } from 'lucide-react';
import { theme } from '../theme';
import './Footer.css';

interface NavItem {
  label: string;
  path: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Посты', path: '/' },
  { label: 'Достижения', path: '/achievements' },
  { label: 'Карта', path: '/map' },
  { label: 'Мои заказы', path: '/my-orders' },
];

export const Footer: React.FC = () => {
  const [stats, setStats] = useState<{ totalUsers: number; totalHelps: number; activePosts: number } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStats = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/stats');
        if (response.ok) {
          const data = await response.json();
          setStats(data);
        }
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
              <TreeDeciduous size={28} color={theme.colors.accent} />
              <span className="footer-logo-text">helpTree</span>
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
                <Users size={18} color={theme.colors.accent} />
                <span className="footer-stat-value">
                  {loading ? <Loader2 size={14} className="footer-spinner" /> : stats?.totalUsers.toLocaleString('ru-RU')}
                </span>
                <span className="footer-stat-label">пользователей</span>
              </div>
              <div className="footer-stat-item">
                <Heart size={18} color="#10b981" />
                <span className="footer-stat-value">
                  {loading ? <Loader2 size={14} className="footer-spinner" /> : stats?.totalHelps.toLocaleString('ru-RU')}
                </span>
                <span className="footer-stat-label">актов помощи</span>
              </div>
              <div className="footer-stat-item">
                <FileText size={18} color="#f59e0b" />
                <span className="footer-stat-value">
                  {loading ? <Loader2 size={14} className="footer-spinner" /> : stats?.activePosts.toLocaleString('ru-RU')}
                </span>
                <span className="footer-stat-label">активных постов</span>
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
