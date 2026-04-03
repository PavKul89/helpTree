import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TreeDeciduous, Users, Heart, FileText, Loader2 } from 'lucide-react';
import { statsApi } from '../api/statsApi';
import { theme } from '../theme';

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
    <footer style={styles.footer}>
      <div style={styles.container}>
        <div style={styles.grid}>
          <div style={styles.brand}>
            <div style={styles.logo}>
              <TreeDeciduous size={28} color={theme.colors.accent} />
              <span style={styles.logoText}>helpTree</span>
            </div>
            <p style={styles.description}>
              Платформа взаимопомощи для сообщества. Помогайте другим и получайте помощь в ответ.
            </p>
          </div>

          <div style={styles.nav}>
            <h4 style={styles.navTitle}>Навигация</h4>
            <div style={styles.navLinks}>
              {NAV_ITEMS.map((item) => (
                <Link key={item.path} to={item.path} style={styles.navLink}>
                  {item.label}
                </Link>
              ))}
            </div>
          </div>

          <div style={styles.stats}>
            <h4 style={styles.statsTitle}>Сообщество</h4>
            <div style={styles.statsList}>
              <div style={styles.statItem}>
                <Users size={18} color={theme.colors.accent} />
                <span style={styles.statValue}>
                  {loading ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : stats?.totalUsers.toLocaleString('ru-RU')}
                </span>
                <span style={styles.statLabel}>пользователей</span>
              </div>
              <div style={styles.statItem}>
                <Heart size={18} color="#10b981" />
                <span style={styles.statValue}>
                  {loading ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : stats?.totalHelps.toLocaleString('ru-RU')}
                </span>
                <span style={styles.statLabel}>актов помощи</span>
              </div>
              <div style={styles.statItem}>
                <FileText size={18} color="#f59e0b" />
                <span style={styles.statValue}>
                  {loading ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : stats?.activePosts.toLocaleString('ru-RU')}
                </span>
                <span style={styles.statLabel}>активных постов</span>
              </div>
            </div>
          </div>
        </div>

        <div style={styles.bottom}>
          <div style={styles.divider} />
          <div style={styles.copyright}>
            © 2026 helpTree. Платформа взаимопомощи.
          </div>
        </div>
      </div>
    </footer>
  );
};

const styles: Record<string, React.CSSProperties> = {
  footer: {
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.95) 0%, rgba(2, 44, 34, 0.98) 100%)',
    borderTop: '1px solid rgba(6, 182, 212, 0.15)',
    padding: '48px 24px 24px',
    marginTop: '48px',
  },
  container: {
    maxWidth: '1200px',
    margin: '0 auto',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
    gap: '40px',
    marginBottom: '32px',
  },
  brand: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  logo: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  logoText: {
    color: theme.colors.text,
    fontSize: '22px',
    fontWeight: 700,
    letterSpacing: '-0.5px',
  },
  description: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    lineHeight: 1.6,
    margin: 0,
    maxWidth: '280px',
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  navTitle: {
    color: theme.colors.text,
    fontSize: '14px',
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    margin: 0,
  },
  navLinks: {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
  },
  navLink: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    textDecoration: 'none',
    transition: 'color 0.2s ease',
    cursor: 'pointer',
  },
  stats: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  statsTitle: {
    color: theme.colors.text,
    fontSize: '14px',
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    margin: 0,
  },
  statsList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  statItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  statValue: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 700,
  },
  statLabel: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    marginLeft: '2px',
  },
  bottom: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  divider: {
    height: '1px',
    background: 'linear-gradient(90deg, transparent 0%, rgba(6, 182, 212, 0.2) 50%, transparent 100%)',
  },
  copyright: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    textAlign: 'center' as const,
  },
};
