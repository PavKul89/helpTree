import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileText,
  User,
  HandHeart,
  Gift,
  Plus,
  Trophy,
  Ban,
} from 'lucide-react';
import { activityApi, Activity } from '../api/activityApi';
import { useAuth } from '../context/AuthContext';
import { Spinner } from '../components/Spinner';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';
import { getStatusLabel } from '../utils/statusHelpers';

const ACTIVITY_ICONS: Record<string, React.ElementType> = {
  'HELP_GIVEN': HandHeart,
  'HELP_RECEIVED': Gift,
  'POST_CREATED': Plus,
  'ACHIEVEMENT_EARNED': Trophy,
  'USER_BLOCKED': Ban,
};

const ACTIVITY_FILTERS = [
  { value: 'ALL', label: 'Все события' },
  { value: 'HELP_GIVEN', label: 'Помог' },
  { value: 'HELP_RECEIVED', label: 'Получил помощь' },
  { value: 'POST_CREATED', label: 'Создал запрос' },
  { value: 'ACHIEVEMENT_EARNED', label: 'Достижения' },
  { value: 'USER_BLOCKED', label: 'Блокировки' },
];

const PAGE_SIZE = 20;

export const ActivityPage = () => {
  const { userId } = useParams<{ userId?: string }>();
  const { user: currentUser } = useAuth();
  const [activities, setActivities] = useState<Activity[]>([]);
  const [loading, setLoading] = useState(true);
  const [isOwnProfile, setIsOwnProfile] = useState(false);
  const [filterType, setFilterType] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);

  useEffect(() => {
    setPage(0);
  }, [userId]);

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
        const data = await activityApi.getUserActivities(targetUserId, {
          page,
          size: PAGE_SIZE,
          type: filterType,
        });
        if (!cancelled) {
          setActivities(data.content);
          setTotalPages(data.totalPages);
          setTotalElements(data.totalElements);
          setHasNext(data.hasNext);
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
  }, [userId, currentUser?.id, filterType, page]);

  const getActivityColor = (type: string) => {
    switch (type) {
      case 'HELP_GIVEN': return '#8ccb55';
      case 'HELP_RECEIVED': return '#84b9a8';
      case 'POST_CREATED': return '#c8f56a';
      case 'ACHIEVEMENT_EARNED': return '#b7a3df';
      case 'USER_BLOCKED': return '#d48c83';
      default: return '#829688';
    }
  };

  const getStatusBadge = (status?: string) => {
    if (!status) return null;
    const colors: Record<string, string> = {
      OPEN: 'rgba(140, 203, 85, 0.22)',
      IN_PROGRESS: 'rgba(200, 245, 106, 0.18)',
      COMPLETED: 'rgba(132, 185, 168, 0.22)',
      CANCELLED: 'rgba(212, 140, 131, 0.2)',
    };
    return (
      <span style={{
        ...styles.statusBadge,
        backgroundColor: colors[status] || '#6b7280',
      }}>
        {getStatusLabel(status)}
      </span>
    );
  };

  if (loading) return <Spinner message="Загрузка активности..." />;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>

      <div style={styles.mainCard}>
        <div style={styles.header}>
          <h1 style={styles.title}>История активности</h1>
          {isOwnProfile && (
            <span style={styles.subtitle}>Ваша лента событий</span>
          )}
          <div style={styles.filters}>
            <label style={styles.filterLabel} htmlFor="activity-type">Тип события</label>
            <select
              id="activity-type"
              value={filterType}
              onChange={(event) => {
                setFilterType(event.target.value);
                setPage(0);
              }}
              style={styles.filterSelect}
            >
              {ACTIVITY_FILTERS.map((filter) => (
                <option key={filter.value} value={filter.value}>
                  {filter.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div style={styles.content}>
          {activities.length === 0 ? (
            <div style={styles.emptyState}>
              <ClipboardList size={64} color={theme.colors.accent} style={styles.emptyIcon as React.CSSProperties} />
              <div>Пока нет активности</div>
              <div style={styles.emptyHint}>
                Здесь будут отображаться ваши действия: помощь другим, полученная помощь, созданные запросы и достижения.
              </div>
            </div>
          ) : (
            <div style={styles.timeline}>
              {activities.map((activity, index) => {
                const relativeTime = getRelativeTime(activity.timestamp);
                return (
                  <div key={`${activity.type}-${activity.timestamp}-${index}`} style={styles.activityItem}>
                    <div style={styles.timelineLine}>
                      <div
                        style={{
                          ...styles.timelineDot,
                          backgroundColor: getActivityColor(activity.type),
                        }}
                      >
                        {(() => {
                          const IconComp = ACTIVITY_ICONS[activity.type];
                          return IconComp ? <IconComp size={16} color="#fff" /> : <Trophy size={16} color="#fff" />;
                        })()}
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
                        {relativeTime && (
                          <time dateTime={activity.timestamp || undefined} style={styles.activityTime}>
                            {relativeTime}
                          </time>
                        )}
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
                            <FileText size={14} style={{ marginRight: 4 }} />
                            {activity.relatedPostTitle}
                          </Link>
                        )}

                        {activity.relatedUserName && activity.type !== 'ACHIEVEMENT_EARNED' && (
                          <Link
                            to={`/profile/${activity.relatedUserId}`}
                            style={styles.metaLink}
                          >
                            <User size={14} style={{ marginRight: 4 }} />
                            {activity.relatedUserName}
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
                );
              })}
            </div>
          )}

          {(page > 0 || hasNext) && (
            <div style={styles.pagination}>
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
                style={{
                  ...styles.pageButton,
                  opacity: page === 0 ? 0.45 : 1,
                  cursor: page === 0 ? 'default' : 'pointer',
                }}
              >
                <ChevronLeft size={16} /> Назад
              </button>
              <span style={styles.pageInfo}>
                Страница {page + 1} из {Math.max(totalPages, 1)} · {totalElements} событий
              </span>
              <button
                type="button"
                onClick={() => setPage((current) => current + 1)}
                disabled={!hasNext}
                style={{
                  ...styles.pageButton,
                  opacity: hasNext ? 1 : 0.45,
                  cursor: hasNext ? 'pointer' : 'default',
                }}
              >
                Вперёд <ChevronRight size={16} />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 980,
    margin: '0 auto',
    padding: '28px 24px 56px',
    color: '#f5f4ea',
  },
  backLink: {
    color: '#dfeee0',
    textDecoration: 'none',
    fontSize: '15px',
    display: 'inline-block',
    marginBottom: '16px',
    opacity: 0.9,
  },
  mainCard: {
    background: 'linear-gradient(155deg, rgba(16, 45, 32, 0.96), rgba(11, 36, 25, 0.98))',
    border: '1px solid rgba(180, 225, 155, 0.18)',
    borderRadius: '22px',
    overflow: 'hidden',
    boxShadow: '0 20px 46px rgba(0, 0, 0, 0.17)',
  },
  header: {
    padding: '26px 30px 22px',
    borderBottom: '1px solid rgba(180, 225, 155, 0.11)',
    background: 'linear-gradient(180deg, rgba(200, 245, 106, 0.035), transparent)',
  },
  title: {
    color: '#f5f4ea',
    fontSize: 'clamp(1.75rem, 3vw, 2.25rem)',
    fontWeight: 750,
    lineHeight: 1.15,
    letterSpacing: '-0.035em',
    margin: 0,
  },
  subtitle: {
    color: '#b7c5b8',
    fontSize: '13px',
    marginTop: '8px',
    display: 'block',
  },
  filters: {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '10px',
    marginTop: '20px',
  },
  filterLabel: {
    color: '#b7c5b8',
    fontSize: '12px',
    fontWeight: 700,
  },
  filterSelect: {
    background: 'rgba(7, 27, 19, 0.72)',
    color: '#f5f4ea',
    border: '1px solid rgba(180, 225, 155, 0.2)',
    borderRadius: '11px',
    padding: '8px 11px',
    fontSize: '13px',
    outline: 'none',
  },
  content: {
    padding: '18px 26px 16px',
  },
  emptyState: {
    textAlign: 'center',
    padding: '40px 20px 30px',
    color: '#b7c5b8',
    fontSize: '15px',
  },
  emptyIcon: {
    marginBottom: '16px',
    opacity: 0.8,
  },
  emptyHint: {
    color: '#b7c5b8',
    fontSize: '14px',
    marginTop: '8px',
    maxWidth: 420,
    margin: '8px auto 0',
    lineHeight: 1.6,
  },
  timeline: {
    display: 'flex',
    flexDirection: 'column',
  },
  activityItem: {
    display: 'flex',
    gap: '14px',
    minHeight: '96px',
  },
  timelineLine: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    width: '32px',
    flexShrink: 0,
  },
  timelineDot: {
    width: '30px',
    height: '30px',
    borderRadius: '10px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    boxShadow: '0 0 0 5px rgba(200, 245, 106, 0.035)',
    border: '1px solid rgba(255,255,255,0.06)',
  },
  timelineConnector: {
    width: '2px',
    flex: 1,
    minHeight: '20px',
    background: 'linear-gradient(180deg, rgba(180, 225, 155, 0.14), rgba(180, 225, 155, 0.06))',
    marginTop: '7px',
  },
  activityContent: {
    flex: 1,
    padding: '1px 0 18px',
  },
  activityHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '12px',
    marginBottom: '5px',
  },
  activityType: {
    fontSize: '11px',
    fontWeight: 750,
    letterSpacing: '0.015em',
  },
  activityTime: {
    color: '#829688',
    fontSize: '11px',
    whiteSpace: 'nowrap',
    textDecoration: 'none',
  },
  activityTitle: {
    color: '#f5f4ea',
    fontSize: '15px',
    fontWeight: 600,
    lineHeight: 1.4,
    marginBottom: '5px',
  },
  activityDescription: {
    color: '#b7c5b8',
    fontSize: '13px',
    marginBottom: '9px',
    lineHeight: 1.5,
  },
  activityMeta: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '8px',
    alignItems: 'center',
    minHeight: '20px',
  },
  metaLink: {
    color: '#dfeee0',
    textDecoration: 'none',
    fontSize: '13px',
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    opacity: 0.96,
  },
  statusBadge: {
    padding: '4px 8px',
    borderRadius: '8px',
    fontSize: '10px',
    fontWeight: 700,
    color: '#f5f4ea',
    border: '1px solid rgba(255,255,255,0.08)',
    letterSpacing: '0.01em',
  },
  categoryBadge: {
    padding: '4px 8px',
    borderRadius: '8px',
    fontSize: '10px',
    fontWeight: 600,
    background: 'rgba(200, 245, 106, 0.07)',
    color: '#b7c5b8',
    letterSpacing: '0.01em',
  },
  pagination: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '12px',
    padding: '18px 12px 10px',
    borderTop: '1px solid rgba(180, 225, 155, 0.12)',
    marginTop: '8px',
  },
  pageButton: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    background: 'rgba(255,255,255,0.06)',
    color: '#f5f4ea',
    border: '1px solid rgba(180, 225, 155, 0.22)',
    borderRadius: '10px',
    padding: '8px 12px',
    fontSize: '13px',
    fontWeight: 600,
  },
  pageInfo: {
    color: '#b7c5b8',
    fontSize: '13px',
  },
};
