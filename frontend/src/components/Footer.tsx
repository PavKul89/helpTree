import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, FileText, HeartHandshake, TreeDeciduous, Users } from 'lucide-react';
import { statsApi } from '../api/statsApi';
import './Footer.css';

interface Stats {
  totalUsers: number;
  totalHelps: number;
  activePosts: number;
}

const FOOTER_LINK_GROUPS = [
  {
    title: 'Платформа',
    links: [
      { label: 'Все заявки', path: '/posts' },
      { label: 'Карта помощи', path: '/map' },
      { label: 'Граф сообщества', path: '/graph' },
    ],
  },
  {
    title: 'Сообщество',
    links: [
      { label: 'Участники', path: '/users' },
      { label: 'Достижения', path: '/achievements' },
      { label: 'Безопасность', path: '/safety' },
    ],
  },
  {
    title: 'Личный раздел',
    links: [
      { label: 'Мои заявки', path: '/my-orders' },
      { label: 'Избранное', path: '/favorites' },
      { label: 'Сообщения', path: '/chats' },
    ],
  },
];

export const Footer: React.FC = () => {
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    const loadStats = async () => {
      try {
        const data = await statsApi.getStats();
        if (active) setStats(data);
      } catch (error) {
        console.error('Error loading footer stats:', error);
      } finally {
        if (active) setLoading(false);
      }
    };

    void loadStats();
    return () => {
      active = false;
    };
  }, []);

  const formatStat = (value: number | undefined) =>
    typeof value === 'number' ? value.toLocaleString('ru-RU') : '—';

  return (
    <footer className="footer" aria-label="Информация и навигация">
      <div className="footer-container">
        <div className="footer-main">
          <div className="footer-brand">
            <Link to="/" className="footer-logo" aria-label="HelpTree — на главную">
              <TreeDeciduous size={24} strokeWidth={1.7} aria-hidden="true" />
              <span>help<span className="footer-logo-accent">Tree</span></span>
            </Link>
            <p className="footer-description">
              Помощь ближе, когда мы рядом.
              <br />
              Сообщество взаимопомощи HelpTree.
            </p>
            <Link to="/posts/new" className="footer-create-link">
              Рассказать, чем помочь
              <ArrowUpRight size={15} aria-hidden="true" />
            </Link>
          </div>

          <nav className="footer-navigation" aria-label="Ссылки в подвале">
            {FOOTER_LINK_GROUPS.map((group) => (
              <div className="footer-link-group" key={group.title}>
                <h2 className="footer-group-title">{group.title}</h2>
                <ul className="footer-link-list">
                  {group.links.map((item) => (
                    <li key={item.path}>
                      <Link to={item.path} className="footer-nav-link">
                        {item.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <section className="footer-community" aria-label="HelpTree в цифрах" aria-busy={loading}>
          <span className="footer-community-label">Сообщество растёт</span>
          <div className="footer-stats-list">
            <div className="footer-stat-item">
              <Users size={16} aria-hidden="true" />
              <span className="footer-stat-value">{loading ? '…' : formatStat(stats?.totalUsers)}</span>
              <span className="footer-stat-label">участников</span>
            </div>
            <div className="footer-stat-item">
              <HeartHandshake size={16} aria-hidden="true" />
              <span className="footer-stat-value">{loading ? '…' : formatStat(stats?.totalHelps)}</span>
              <span className="footer-stat-label">актов помощи</span>
            </div>
            <div className="footer-stat-item">
              <FileText size={16} aria-hidden="true" />
              <span className="footer-stat-value">{loading ? '…' : formatStat(stats?.activePosts)}</span>
              <span className="footer-stat-label">открытых заявок</span>
            </div>
          </div>
        </section>

        <div className="footer-bottom">
          <span className="footer-copyright">© {new Date().getFullYear()} HelpTree</span>
          <span className="footer-bottom-note">Вместе заботимся о том, что важно.</span>
          <Link to="/safety" className="footer-safety-link">Правила безопасности</Link>
        </div>
      </div>
    </footer>
  );
};
