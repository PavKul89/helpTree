import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { activityApi, Activity } from '../api/activityApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Spinner } from '../components/Spinner';
import { theme } from '../theme';

export const ActivityPage = () => {
  const { userId } = useParams<{ userId?: string }>();
  const { user: currentUser } = useAuth();
  const [activities, setActivities] = useState<Activity[]>([]);
  const [loading, setLoading] = useState(true);
  const [isOwnProfile, setIsOwnProfile] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const targetUserId = userId && !isNaN(Number(userId)) 
      ? Number(userId) 
      : currentUser?.id;

    if (!targetUserId) {
      setLoading(false);
      return;
    }

    setIsOwnProfile(currentUser?.id === targetUserId);

    const loadActivities = async () => {
      setLoading(true);
      try {
        const data = await activityApi.getUserActivities(targetUserId, 50);
        if (!cancelled) {
          setActivities(data);
        }
      } catch (err) {
        console.error('Error loading activities:', err);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    loadActivities();
    return () => { cancelled = true; };
  }, [userId, currentUser?.id]);

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Только что';
    if (diffMins < 60) return `${diffMins} мин. назад`;
    if (diffHours < 24) return `${diffHours} ч. назад`;
    if (diffDays < 7) return `${diffDays} дн. назад`;
    
    return date.toLocaleDateString('ru-RU', { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric' 
    });
  };

  const getActivityColor = (type: string) => {
    switch (type) {
      case 'HELP_GIVEN': return '#10b981';
      case 'HELP_RECEIVED': return '#06b6d4';
      case 'POST_CREATED': return '#f59e0b';
      case 'ACHIEVEMENT_EARNED': return '#a855f7';
      default: return '#6b7280';
    }
  };

  const getStatusBadge = (status?: string) => {
    if (!status) return null;
    const colors: Record<string, string> = {
      'OPEN': '#22c55e',
      'IN_PROGRESS': '#f59e0b',
      'COMPLETED': '#06b6d4',
      'CANCELLED': '#ef4444',
    };
    return (
      <span style={{
        ...styles.statusBadge,
        backgroundColor: colors[status] || '#6b7280',
      }}>
        {status}
      </span>
    );
  };

  if (loading) return <Spinner message="Загрузка активности..." />;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        <div style={styles.header}>
          <h1 style={styles.title}>История активности</h1>
          {isOwnProfile && (
            <span style={styles.subtitle}>Ваша лента событий</span>
          )}
        </div>

        <div style={styles.content}>
          {activities.length === 0 ? (
            <div style={styles.emptyState}>
              <div style={styles.emptyIcon}>📋</div>
              <div>Пока нет активности</div>
              <div style={styles.emptyHint}>
                Здесь будут отображаться ваши действия: помощь другим, полученная помощь, созданные запросы и достижения.
              </div>
            </div>
          ) : (
            <div style={styles.timeline}>
              {activities.map((activity, index) => (
                <div key={index} style={styles.activityItem}>
                  <div style={styles.timelineLine}>
                    <div style={{
                      ...styles.timelineDot,
                      backgroundColor: getActivityColor(activity.type),
                    }}>
                      {activity.emoji}
                    </div>
                    {index < activities.length - 1 && <div style={styles.timelineConnector} />}
                  </div>
                  
                  <div style={styles.activityContent}>
                    <div style={styles.activityHeader}>
                      <span style={{
                        ...styles.activityType,
                        color: getActivityColor(activity.type),
                      }}>
                        {activity.typeLabel}
                      </span>
                      <span style={styles.activityTime}>
                        {formatDate(activity.timestamp)}
                      </span>
                    </div>
                    
                    <div style={styles.activityTitle}>{activity.title}</div>
                    
                    {activity.description && (
                      <div style={styles.activityDescription}>
                        {activity.description}
                      </div>
                    )}
                    
                    <div style={styles.activityMeta}>
                      {activity.relatedPostTitle && (
                        <Link 
                          to={`/posts/${activity.relatedPostId}`}
                          style={styles.metaLink}
                        >
                          📝 {activity.relatedPostTitle}
                        </Link>
                      )}
                      
                      {activity.relatedUserName && activity.type !== 'ACHIEVEMENT_EARNED' && (
                        <Link 
                          to={`/profile/${activity.relatedUserId}`}
                          style={styles.metaLink}
                        >
                          👤 {activity.relatedUserName}
                        </Link>
                      )}
                      
                      {activity.status && getStatusBadge(activity.status)}
                      
                      {activity.category && (
                        <span style={styles.categoryBadge}>
                          {activity.category}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 800,
    margin: '0 auto',
    padding: '24px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
    display: 'inline-block',
    marginBottom: '16px',
  },
  mainCard: {
    padding: '0',
    overflow: 'hidden',
  },
  header: {
    padding: '24px 32px',
    borderBottom: `1px solid ${theme.colors.border}`,
    background: 'linear-gradient(180deg, rgba(6, 182, 212, 0.08) 0%, transparent 100%)',
  },
  title: {
    color: theme.colors.text,
    fontSize: '24px',
    fontWeight: 700,
    margin: 0,
  },
  subtitle: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    marginTop: '4px',
    display: 'block',
  },
  content: {
    padding: '24px 32px',
  },
  emptyState: {
    textAlign: 'center',
    padding: '40px',
    color: theme.colors.textMuted,
    fontSize: '15px',
  },
  emptyIcon: {
    fontSize: '64px',
    marginBottom: '16px',
    opacity: 0.8,
  },
  emptyHint: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    marginTop: '8px',
    maxWidth: 400,
    margin: '8px auto 0',
    lineHeight: 1.5,
  },
  timeline: {
    display: 'flex',
    flexDirection: 'column',
  },
  activityItem: {
    display: 'flex',
    gap: '16px',
  },
  timelineLine: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    width: '40px',
    flexShrink: 0,
  },
  timelineDot: {
    width: '36px',
    height: '36px',
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '18px',
    flexShrink: 0,
    boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
  },
  timelineConnector: {
    width: '2px',
    flex: 1,
    minHeight: '20px',
    backgroundColor: theme.colors.border,
    marginTop: '8px',
  },
  activityContent: {
    flex: 1,
    paddingBottom: '24px',
  },
  activityHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '4px',
  },
  activityType: {
    fontSize: '12px',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
  },
  activityTime: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  activityTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 500,
    marginBottom: '4px',
  },
  activityDescription: {
    color: theme.colors.textSecondary,
    fontSize: '14px',
    marginBottom: '8px',
    lineHeight: 1.4,
  },
  activityMeta: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '8px',
    alignItems: 'center',
  },
  metaLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '13px',
    transition: 'opacity 0.2s',
  },
  statusBadge: {
    padding: '2px 8px',
    borderRadius: '4px',
    fontSize: '11px',
    fontWeight: 600,
    color: '#fff',
    textTransform: 'uppercase',
  },
  categoryBadge: {
    padding: '2px 8px',
    borderRadius: '4px',
    fontSize: '11px',
    fontWeight: 500,
    backgroundColor: 'rgba(255,255,255,0.1)',
    color: theme.colors.textMuted,
    textTransform: 'uppercase',
  },
};
