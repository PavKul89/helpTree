import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Trophy, HandHeart, Zap, MessageCircle, Scale, PartyPopper, Sprout, Clock, Sun, MessageSquare, FileText, Scale3D, Star, Shield, Gem, Crown, Snowflake, Ghost, Egg } from 'lucide-react';
import { achievementApi, Achievement } from '../api/achievementApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Spinner } from '../components/Spinner';
import { theme } from '../theme';

interface AchievementWithProgress extends Achievement {
  earned: boolean;
  earnedAt?: string;
  currentProgress: number;
  targetValue: number;
}

const CATEGORY_ICONS: Record<string, React.ElementType> = {
  help: HandHeart,
  speed: Zap,
  communication: MessageCircle,
  debt: Scale,
  holiday: PartyPopper,
};

const ACHIEVEMENT_ICONS: Record<string, React.ElementType> = {
  SEEDLING: Sprout,
  FIRST_HELP: HandHeart,
  HELPER_5: HandHeart,
  HELPER_10: HandHeart,
  HELPER_25: HandHeart,
  HELPER_50: HandHeart,
  HELPER_100: Crown,
  FAST_HELP: Zap,
  NIGHT_OWL: Clock,
  WEEKEND_HERO: Sun,
  FIRST_CHAT: MessageSquare,
  CHATTER_10: MessageCircle,
  FIRST_POST: FileText,
  POSTER_10: FileText,
  DEBT_FREE: Shield,
  BALANCED: Scale3D,
  REPUTATION_5: Star,
  NEW_YEAR_WIZARD: Gem,
  EASTER_BUNNY: Egg,
  HALLOWEEN_HERO: Ghost,
  BIRTHDAY_HERO: PartyPopper,
  WINTER_HELPER: Snowflake,
};

const CATEGORIES = {
  help: { name: 'По количеству помощи' },
  speed: { name: 'По скорости' },
  communication: { name: 'По общению' },
  debt: { name: 'По долгам и рейтингу' },
  holiday: { name: 'Праздничные' },
};

const ACHIEVEMENT_CATEGORIES: Record<string, string[]> = {
  help: ['SEEDLING', 'FIRST_HELP', 'HELPER_5', 'HELPER_10', 'HELPER_25', 'HELPER_50', 'HELPER_100'],
  speed: ['FAST_HELP', 'NIGHT_OWL', 'WEEKEND_HERO'],
  communication: ['FIRST_CHAT', 'CHATTER_10', 'FIRST_POST', 'POSTER_10'],
  debt: ['DEBT_FREE', 'BALANCED', 'REPUTATION_5'],
  holiday: ['NEW_YEAR_WIZARD', 'EASTER_BUNNY', 'HALLOWEEN_HERO', 'BIRTHDAY_HERO', 'WINTER_HELPER'],
};

export const AchievementsPage = () => {
  const { user } = useAuth();
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        const data = await achievementApi.getAllAchievements(user?.id);
        setAchievements(data);
      } catch (err) {
        console.error('Error loading achievements:', err);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [user]);

  const getAchievementsWithStatus = (types: string[]): AchievementWithProgress[] => {
    return types.map(type => {
      const achievement = achievements.find(a => a.type === type);
      return {
        type: type,
        emoji: achievement?.emoji || '',
        name: achievement?.name || '',
        description: achievement?.description || '',
        rarity: achievement?.rarity || 'COMMON',
        earned: achievement?.isEarned || false,
        earnedAt: achievement?.earnedAt,
        currentProgress: achievement?.currentProgress || 0,
        targetValue: achievement?.targetValue || 1,
      };
    });
  };

  const getRarityColor = (rarity: string) => {
    switch (rarity) {
      case 'COMMON': return '#9ca3af';
      case 'UNCOMMON': return '#22c55e';
      case 'RARE': return '#3b82f6';
      case 'EPIC': return '#a855f7';
      case 'LEGENDARY': return '#f59e0b';
      default: return '#9ca3af';
    }
  };

  const getProgress = (category: string) => {
    const categoryTypes = ACHIEVEMENT_CATEGORIES[category] || [];
    const categoryAchievements = getAchievementsWithStatus(categoryTypes);
    const earned = categoryAchievements.filter(a => a.earned).length;
    return { earned, total: categoryTypes.length };
  };

  if (loading) return <Spinner message="Загрузка достижений..." />;

  const totalEarned = achievements.filter(a => a.isEarned).length;
  const totalAvailable = achievements.length;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>

      <Card style={styles.headerCard}>
        <div style={styles.headerContent}>
          <Trophy size={64} color="#f59e0b" />
          <div style={styles.headerText}>
            <h1 style={styles.title}>Достижения</h1>
            <p style={styles.subtitle}>
              {user ? (
                <>Вы заработали <strong>{totalEarned}</strong> из <strong>{totalAvailable}</strong> достижений</>
              ) : (
                <>Всего доступно <strong>{totalAvailable}</strong> достижений</>
              )}
            </p>
          </div>
        </div>
        {user && totalEarned > 0 && (
          <div style={styles.progressBar}>
            <div 
              style={{
                ...styles.progressFill,
                width: `${(totalEarned / totalAvailable) * 100}%`,
              }} 
            />
          </div>
        )}
      </Card>

      {Object.entries(CATEGORIES).map(([key, category]) => {
        const achievements = getAchievementsWithStatus(ACHIEVEMENT_CATEGORIES[key]);
        const progress = getProgress(key);

        return (
          <div key={key} style={styles.categorySection}>
            <div style={styles.categoryHeader}>
              {(() => {
                const IconComponent = CATEGORY_ICONS[key as keyof typeof CATEGORY_ICONS];
                return IconComponent ? <IconComponent size={24} color={theme.colors.accent} /> : null;
              })()}
              <h2 style={styles.categoryTitle}>{category.name}</h2>
              <span style={styles.categoryProgress}>
                {progress.earned}/{progress.total}
              </span>
            </div>
            
            <div style={styles.achievementsGrid}>
              {achievements.map(achievement => {
                const AchievementIcon = ACHIEVEMENT_ICONS[achievement.type] || Trophy;
                return (
                <div
                  key={achievement.type}
                  style={{
                    ...styles.achievementCard,
                    opacity: achievement.earned ? 1 : 0.6,
                    borderColor: getRarityColor(achievement.rarity),
                    background: achievement.earned 
                      ? 'rgba(255,255,255,0.08)' 
                      : 'rgba(255,255,255,0.03)',
                  }}
                >
                  <div style={{
                    ...styles.achievementIcon,
                    transform: achievement.earned ? 'scale(1.1)' : 'scale(0.9)',
                    color: getRarityColor(achievement.rarity),
                  }}>
                    <AchievementIcon size={48} />
                  </div>
                  <div style={styles.achievementName}>{achievement.name}</div>
                  <div style={styles.achievementDesc}>{achievement.description}</div>
                  
                  {!achievement.earned && achievement.targetValue && achievement.targetValue > 1 && (
                    <div style={styles.progressContainer}>
                      <div style={styles.achievementProgressBar}>
                        <div 
                          style={{
                            ...styles.achievementProgressFill,
                            width: `${Math.min((achievement.currentProgress / achievement.targetValue) * 100, 100)}%`,
                            background: getRarityColor(achievement.rarity),
                          }} 
                        />
                      </div>
                      <div style={styles.achievementProgressText}>
                        {achievement.currentProgress} / {achievement.targetValue}
                      </div>
                    </div>
                  )}
                  
                  <div style={{
                    ...styles.achievementRarity,
                    color: getRarityColor(achievement.rarity),
                  }}>
                    {achievement.rarity}
                  </div>
                  {achievement.earned && achievement.earnedAt && (
                    <div style={styles.achievementDate}>
                      Получено {new Date(achievement.earnedAt).toLocaleDateString('ru-RU')}
                    </div>
                  )}
                </div>
              );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 1200,
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
  headerCard: {
    padding: '32px',
    marginBottom: '24px',
    background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(234, 179, 8, 0.1) 100%)',
    border: '1px solid rgba(245, 158, 11, 0.3)',
  },
  headerContent: {
    display: 'flex',
    alignItems: 'center',
    gap: '24px',
    marginBottom: '20px',
  },
  headerText: {
    flex: 1,
  },
  title: {
    color: theme.colors.text,
    fontSize: '32px',
    fontWeight: 700,
    margin: '0 0 8px 0',
  },
  subtitle: {
    color: theme.colors.textMuted,
    fontSize: '16px',
    margin: 0,
  },
  progressBar: {
    height: '8px',
    background: 'rgba(255,255,255,0.1)',
    borderRadius: '4px',
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    background: 'linear-gradient(90deg, #f59e0b, #eab308)',
    borderRadius: '4px',
    transition: 'width 0.5s ease',
  },
  categorySection: {
    marginBottom: '32px',
  },
  categoryHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    marginBottom: '16px',
    paddingBottom: '12px',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  categoryTitle: {
    color: theme.colors.text,
    fontSize: '20px',
    fontWeight: 600,
    margin: 0,
    flex: 1,
  },
  categoryProgress: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    fontWeight: 500,
    background: 'rgba(255,255,255,0.1)',
    padding: '4px 12px',
    borderRadius: '12px',
  },
  achievementsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
    gap: '16px',
  },
  achievementCard: {
    padding: '20px',
    borderRadius: theme.borderRadius.md,
    border: '2px solid',
    textAlign: 'center',
    transition: 'all 0.3s ease',
  },
  achievementIcon: {
    marginBottom: '8px',
    display: 'block',
    transition: 'transform 0.3s ease',
  },
  achievementName: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 600,
    marginBottom: '4px',
  },
  achievementDesc: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    marginBottom: '8px',
    lineHeight: 1.4,
  },
  achievementRarity: {
    fontSize: '10px',
    fontWeight: 700,
    textTransform: 'uppercase',
    marginBottom: '8px',
    letterSpacing: '0.5px',
  },
  achievementDate: {
    color: '#22c55e',
    fontSize: '11px',
    fontWeight: 500,
  },
  progressContainer: {
    marginBottom: '8px',
  },
  achievementProgressBar: {
    height: '6px',
    background: 'rgba(255,255,255,0.1)',
    borderRadius: '3px',
    overflow: 'hidden',
    marginBottom: '4px',
  },
  achievementProgressFill: {
    height: '100%',
    borderRadius: '3px',
    transition: 'width 0.3s ease',
  },
  achievementProgressText: {
    color: theme.colors.textMuted,
    fontSize: '11px',
  },
};
