import { useEffect, useState, useRef } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { Star, Camera, Trophy, Sprout, HandHeart, Crown, Zap, Clock, Sun, MessageSquare, MessageCircle, FileText, Shield, Scale3D, Gem, Egg, Ghost, PartyPopper, Snowflake } from 'lucide-react';
import { authApi } from '../api/authApi';
import { chatApi } from '../api/chatApi';
import { postsApi } from '../api/postsApi';
import { achievementApi, Achievement } from '../api/achievementApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { Spinner } from '../components/Spinner';
import { useToast } from '../components/Toast';
import { theme } from '../theme';
import type { User, UserPublic, Post } from '../types';
import { geocodeCity } from '../utils/geocoding';

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

export const ProfilePage = () => {
  const { userId } = useParams<{ userId?: string }>();
  const navigate = useNavigate();
  const { user: currentUser, setUser } = useAuth();
  const { showToast } = useToast();
  
  const [profileUser, setProfileUser] = useState<User | UserPublic | null>(null);
  const [userPosts, setUserPosts] = useState<Post[]>([]);
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [loading, setLoading] = useState(true);
  const [isOwnProfile, setIsOwnProfile] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [activeTab, setActiveTab] = useState<'info' | 'posts' | 'achievements'>('info');
  const [editForm, setEditForm] = useState({ name: '', email: '', phone: '', city: '', birthDate: '' });
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let cancelled = false;
    const currentUserId = currentUser?.id;
    
    const loadUser = async () => {
      setLoading(true);
      try {
        if (userId && !isNaN(Number(userId))) {
          const id = Number(userId);
          const userData = await authApi.getUserById(id);
          if (!cancelled) {
            setProfileUser(userData);
            setIsOwnProfile(currentUserId === id);
          }
        } else {
          const userData = await authApi.getCurrentUser();
          if (!cancelled) {
            setProfileUser(userData);
            setIsOwnProfile(true);
            setEditForm({
              name: userData.name,
              email: userData.email,
              phone: (userData as User).phone || '',
              city: (userData as User).city || '',
              birthDate: (userData as User).birthDate ? ((userData as User).birthDate || '').split('T')[0] : '',
            });
          }
        }
      } catch (err: any) {
        console.error('Error loading user:', err?.response?.data || err);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    const loadUserPosts = async () => {
      const targetUserId = userId && !isNaN(Number(userId)) 
        ? Number(userId) 
        : currentUser?.id;
      
      if (!targetUserId) return;
      
      try {
        const response = await postsApi.getAll({ size: 100 });
        const posts = Array.isArray(response) ? response : response.content || [];
        const userPosts = posts.filter((post: Post) => post.userId === targetUserId);
        if (!cancelled) {
          setUserPosts(userPosts);
        }
      } catch (err) {
        console.error('Error loading user posts:', err);
      }
    };

    const loadAchievements = async () => {
      const targetUserId = userId && !isNaN(Number(userId)) 
        ? Number(userId) 
        : currentUser?.id;
      
      if (!targetUserId) return;
      
      try {
        const allAchievements = await achievementApi.getAllAchievements(targetUserId);
        const earnedAchievements = allAchievements.filter(a => a.isEarned);
        if (!cancelled) {
          setAchievements(earnedAchievements);
          
          if (isOwnProfile && earnedAchievements.length > 0) {
            const lastSeenKey = 'lastSeenAchievementDate';
            const lastSeenDate = localStorage.getItem(lastSeenKey);
            const latestAchievement = earnedAchievements[0];
            
            if (latestAchievement.earnedAt) {
              if (lastSeenDate) {
                const lastSeen = new Date(lastSeenDate);
                const latest = new Date(latestAchievement.earnedAt);
                if (latest > lastSeen) {
                  showToast(`Получено достижение: ${latestAchievement.name}!`, 'success');
                }
              }
              localStorage.setItem(lastSeenKey, latestAchievement.earnedAt);
            }
          }
        }
      } catch (err) {
        console.error('Error loading achievements:', err);
      }
    };
    
    loadUser();
    loadUserPosts();
    loadAchievements();
    
    return () => { cancelled = true; };
  }, [userId, currentUser?.id]);

  const handleStartChat = async () => {
    if (!profileUser || isOwnProfile) return;
    try {
      const chat = await chatApi.createChat({ participantId: profileUser.id });
      navigate(`/chats/${chat.id}`);
    } catch (err) {
      console.error('Ошибка при создании чата:', err);
    }
  };

  const handleSaveProfile = async () => {
    if (!currentUser) return;
    try {
      let latitude: number | undefined;
      let longitude: number | undefined;
      
      if (editForm.city) {
        const coords = await geocodeCity(editForm.city);
        if (coords) {
          latitude = coords.lat;
          longitude = coords.lng;
        }
      }
      
      const updatedUser = await authApi.updateProfile(currentUser.id, {
        name: editForm.name,
        email: editForm.email,
        phone: editForm.phone || undefined,
        city: editForm.city || undefined,
        birthDate: editForm.birthDate ? new Date(editForm.birthDate).toISOString() : null,
        latitude,
        longitude,
      });
      setProfileUser(updatedUser);
      setUser(updatedUser);
      setIsEditing(false);
    } catch (err) {
      console.error('Ошибка при обновлении профиля:', err);
    }
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !currentUser) return;
    
    setUploadingAvatar(true);
    try {
      const avatarUrl = await authApi.uploadAvatar(currentUser.id, file);
      const updatedUser = { ...currentUser, avatarUrl };
      setProfileUser(updatedUser);
      setUser(updatedUser);
    } catch (err) {
      console.error('Ошибка при загрузке аватара:', err);
    } finally {
      setUploadingAvatar(false);
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ru-RU', { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric' 
    });
  };

  const getInitials = (name: string) => {
    return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'OPEN': return '#22c55e';
      case 'IN_PROGRESS': return '#f59e0b';
      case 'COMPLETED': return '#06b6d4';
      case 'CANCELLED': return '#ef4444';
      default: return '#6b7280';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'OPEN': return 'Открыт';
      case 'IN_PROGRESS': return 'В работе';
      case 'COMPLETED': return 'Завершён';
      case 'CANCELLED': return 'Отменён';
      default: return status;
    }
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

  if (loading) return <Spinner message="Загрузка профиля..." />;
  if (!profileUser) return <div style={styles.notFound}>Пользователь не найден</div>;

  const fullUser = profileUser as User;
  const isAdmin = 'role' in fullUser && fullUser.role === 'ADMIN';
  const userAvatar = 'avatarUrl' in profileUser ? (profileUser as UserPublic).avatarUrl : undefined;
  const isVip = fullUser.vipUntil && new Date(fullUser.vipUntil) > new Date();
  const vipDaysLeft = fullUser.vipUntil 
    ? Math.ceil((new Date(fullUser.vipUntil).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
    : 0;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      
      <Card style={styles.mainCard}>
        <div style={styles.header}>
        <div 
          style={{
            ...styles.avatarLarge,
            ...(isOwnProfile ? { cursor: 'pointer' } : {}),
            ...(isVip ? { 
              boxShadow: '0 0 20px rgba(251, 191, 36, 0.7), 0 0 40px rgba(251, 191, 36, 0.4)',
              border: '3px solid #fbbf24',
            } : {}),
          }} 
          onClick={() => isOwnProfile && avatarInputRef.current?.click()}
        >
          {userAvatar ? (
            <img src={userAvatar} alt="Avatar" style={styles.avatarImage} />
          ) : (
            getInitials(profileUser.name)
          )}
          {isOwnProfile && (
            <div style={styles.avatarOverlay}>
              {uploadingAvatar ? '...' : <Camera size={20} color="#fff" />}
            </div>
          )}
        </div>
          <input
            type="file"
            ref={avatarInputRef}
            onChange={handleAvatarChange}
            accept="image/*"
            style={{ display: 'none' }}
          />
          <div style={styles.headerInfo}>
            <h1 style={styles.name}>{profileUser.name}</h1>
            <div>
              {isAdmin && <span style={styles.adminBadge}>Администратор</span>}
              {isVip && <span style={styles.vipBadge}>⭐ VIP ({vipDaysLeft} дн.)</span>}
            </div>
            <div style={styles.metaRow}>
              <span style={styles.metaItem}>
                <Star size={14} color={theme.colors.accent} fill={theme.colors.accent} />
                <span style={styles.metaValue}>{profileUser.rating.toFixed(1)}</span>
                <span style={styles.metaLabel}>рейтинг</span>
              </span>
              {'createdAt' in fullUser && (
                <span style={styles.metaItem}>
                  <span style={styles.metaValue}>{formatDate(fullUser.createdAt)}</span>
                  <span style={styles.metaLabel}>на сайте</span>
                </span>
              )}
            </div>
          </div>
          <div style={styles.headerActions}>
            {isOwnProfile && !isEditing && (
              <Button onClick={() => setIsEditing(true)} variant="outline">
                Редактировать
              </Button>
            )}
            {!isOwnProfile && (
              <Button onClick={handleStartChat}>
                Написать
              </Button>
            )}
          </div>
        </div>

        <div style={styles.statsRow}>
          {'helpedCount' in profileUser && (
            <div style={{ ...styles.statBox, background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)' }}>
              <div style={styles.statValue}>{profileUser.helpedCount}</div>
              <div style={styles.statLabel}>помог людям</div>
            </div>
          )}
          <div style={{ 
            ...styles.statBox, 
            background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)' 
          }}>
            <div style={styles.statValue}>{Math.floor((profileUser.debtCount || 0) / 2)}</div>
            <div style={styles.statLabel}>мне помогли</div>
          </div>
          <div style={styles.statBox}>
            <div style={styles.statValue}>{userPosts.length}</div>
            <div style={styles.statLabel}>мои посты</div>
          </div>
        </div>

        <div style={{
          background: 'linear-gradient(135deg, rgba(139, 92, 246, 0.15) 0%, rgba(124, 58, 237, 0.1) 100%)',
          border: '1px solid rgba(139, 92, 246, 0.3)',
          borderRadius: '16px',
          padding: '16px 20px',
          marginBottom: '20px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
            <div style={{
              background: 'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)',
              borderRadius: '10px',
              padding: '8px 14px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}>
              <span style={{ fontSize: '18px', fontWeight: 700 }}>{profileUser.debtCount || 0}</span>
              <span style={{ fontSize: '12px', opacity: 0.9 }}>долг</span>
            </div>
          <div style={{ fontSize: '14px', color: 'rgba(255,255,255,0.8)', fontWeight: 500 }}>
            {(profileUser.debtCount || 0) === 0 
                ? 'Отлично! Вы в балансе' 
                : (profileUser.debtCount || 0) > 5 
                  ? 'Внимание! Вы заблокированы' 
                  : `Вам помогли на ${Math.floor((profileUser.debtCount || 0) / 2)} раз больше чем вы`}
          </div>
          <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.6)', lineHeight: 1.5 }}>
            💡 Правило: за каждую полученную помощь +2 к долгу, за помощь другим -1. 
            Если долг &gt; 5 — аккаунт заблокирован на 7 дней. 
            {(profileUser.debtCount || 0) > 0 ? `Помогите ${profileUser.debtCount} людям чтобы снизить долг.` : 'Продолжайте помогать!'}
          </div>
          </div>
        </div>

        <div style={styles.tabs}>
          <button 
            style={activeTab === 'info' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('info')}
          >
            Информация
          </button>
          <button 
            style={activeTab === 'posts' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('posts')}
          >
            Публикации ({userPosts.length})
          </button>
          <button 
            style={activeTab === 'achievements' ? styles.tabActive : styles.tab}
            onClick={() => setActiveTab('achievements')}
          >
            Достижения ({achievements.length})
          </button>
        </div>

        {activeTab === 'info' && (
          <div style={styles.content}>
            {isEditing ? (
              <div style={styles.editForm}>
                <div style={styles.formRow}>
                  <label style={styles.formLabel}>Имя</label>
                  <input
                    type="text"
                    value={editForm.name}
                    onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                    style={styles.input}
                  />
                </div>
                <div style={styles.formRow}>
                  <label style={styles.formLabel}>Email</label>
                  <input
                    type="email"
                    value={editForm.email}
                    onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                    style={styles.input}
                  />
                </div>
                <div style={styles.formRow}>
                  <label style={styles.formLabel}>Телефон</label>
                  <input
                    type="text"
                    value={editForm.phone}
                    onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                    style={styles.input}
                    placeholder="+7 (999) 123-45-67"
                  />
                </div>
                <div style={styles.formRow}>
                  <label style={styles.formLabel}>Город</label>
                  <input
                    type="text"
                    value={editForm.city}
                    onChange={(e) => setEditForm({ ...editForm, city: e.target.value })}
                    style={styles.input}
                  />
                </div>
                <div style={styles.formRow}>
                  <label style={styles.formLabel}>Дата рождения</label>
                  <input
                    type="date"
                    value={editForm.birthDate}
                    onChange={(e) => setEditForm({ ...editForm, birthDate: e.target.value })}
                    style={styles.input}
                  />
                </div>
                <div style={styles.formActions}>
                  <Button onClick={handleSaveProfile}>Сохранить</Button>
                  <Button variant="outline" onClick={() => setIsEditing(false)}>Отмена</Button>
                </div>
              </div>
            ) : (
              <div style={styles.infoList}>
                {'phone' in fullUser && fullUser.phone && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Телефон</span>
                    <span style={styles.infoValue}>{fullUser.phone}</span>
                  </div>
                )}
                {'city' in fullUser && fullUser.city && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Город</span>
                    <span style={styles.infoValue}>{fullUser.city}</span>
                  </div>
                )}
                {'birthDate' in fullUser && fullUser.birthDate && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Дата рождения</span>
                    <span style={styles.infoValue}>{formatDate(fullUser.birthDate as string)}</span>
                  </div>
                )}
                {'email' in profileUser && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Email</span>
                    <span style={styles.infoValue}>{profileUser.email}</span>
                  </div>
                )}
                {'role' in fullUser && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Telegram</span>
                    <span style={styles.infoValue}>
                      {fullUser.telegramChatId ? 'Подключён' : 'Не подключён'}
                    </span>
                  </div>
                )}
                {'createdAt' in fullUser && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>Дата регистрации</span>
                    <span style={styles.infoValue}>{formatDate(fullUser.createdAt)}</span>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {activeTab === 'posts' && (
          <div style={styles.content}>
            {userPosts.length === 0 ? (
              <div style={styles.emptyState}>Публикаций пока нет</div>
            ) : (
              <div style={styles.postsList}>
                {userPosts.map(post => (
                  <Link 
                    key={post.id} 
                    to={`/posts/${post.id}`}
                    style={styles.postItem}
                  >
                    <div style={styles.postStatus}>
                      <span style={{
                        ...styles.statusDot,
                        backgroundColor: getStatusColor(post.status)
                      }} />
                      <span style={styles.statusText}>{getStatusLabel(post.status)}</span>
                    </div>
                    <div style={styles.postTitle}>{post.title}</div>
                    <div style={styles.postMeta}>
                      <span>{post.category}</span>
                      <span>•</span>
                      <span>{formatDate(post.createdAt)}</span>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'achievements' && (
          <div style={styles.content}>
            {achievements.length === 0 ? (
              <div style={styles.emptyState}>
                <Trophy size={64} color={theme.colors.accent} style={styles.emptyIcon as React.CSSProperties} />
                <div>Пока нет достижений</div>
                <div style={styles.emptyHint}>Помогайте людям, чтобы получить первые достижения!</div>
              </div>
            ) : (
              <div style={styles.achievementsGrid}>
                {achievements.map(achievement => {
                  const AchievementIcon = ACHIEVEMENT_ICONS[achievement.type] || Trophy;
                  return (
                  <div 
                    key={achievement.type} 
                    style={{
                      ...styles.achievementCard,
                      borderColor: getRarityColor(achievement.rarity),
                    }}
                  >
                    <div style={{ ...styles.achievementIcon, color: getRarityColor(achievement.rarity) }}>
                      <AchievementIcon size={40} />
                    </div>
                    <div style={styles.achievementName}>{achievement.name}</div>
                    <div style={styles.achievementDesc}>{achievement.description}</div>
                    <div style={{
                      ...styles.achievementRarity,
                      color: getRarityColor(achievement.rarity),
                    }}>
                      {achievement.rarity}
                    </div>
                    <div style={styles.achievementDate}>
                      {achievement.earnedAt && formatDate(achievement.earnedAt)}
                    </div>
                  </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
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
  notFound: {
    textAlign: 'center',
    padding: '40px',
    color: theme.colors.textMuted,
    fontSize: '16px',
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
    display: 'flex',
    alignItems: 'flex-start',
    gap: '24px',
    padding: '32px',
    background: 'linear-gradient(180deg, rgba(34, 211, 238, 0.08) 0%, transparent 100%)',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  avatarLarge: {
    width: '96px',
    height: '96px',
    borderRadius: '50%',
    background: 'linear-gradient(135deg, #22d3ee 0%, #0891b2 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '28px',
    fontWeight: 700,
    color: '#042f3a',
    flexShrink: 0,
    boxShadow: '0 4px 20px rgba(34, 211, 238, 0.3)',
    position: 'relative',
    overflow: 'hidden',
    cursor: 'pointer',
  },
  avatarImage: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  },
  avatarOverlay: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    background: 'rgba(0,0,0,0.6)',
    color: '#fff',
    fontSize: '20px',
    padding: '4px',
    textAlign: 'center',
  },
  headerInfo: {
    flex: 1,
  },
  name: {
    color: theme.colors.text,
    fontSize: '26px',
    fontWeight: 700,
    margin: '0 0 8px 0',
  },
  adminBadge: {
    display: 'inline-block',
    background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
    borderRadius: '4px',
    padding: '4px 10px',
    fontSize: '12px',
    fontWeight: 600,
    color: '#fff',
    marginBottom: '12px',
  },
  vipBadge: {
    display: 'inline-block',
    background: 'linear-gradient(135deg, #fbbf24 0%, #f59e0b 50%, #fbbf24 100%)',
    borderRadius: '4px',
    padding: '4px 10px',
    fontSize: '12px',
    fontWeight: 600,
    color: '#1f2937',
    marginBottom: '12px',
    marginLeft: '8px',
    boxShadow: '0 0 10px rgba(251, 191, 36, 0.5)',
  },
  metaRow: {
    display: 'flex',
    gap: '24px',
    flexWrap: 'wrap',
  },
  metaItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  metaIcon: {
    color: theme.colors.accent,
    fontSize: '14px',
  },
  metaValue: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 600,
  },
  metaLabel: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  headerActions: {
    display: 'flex',
    gap: '8px',
  },
  statsRow: {
    display: 'flex',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  statBox: {
    flex: 1,
    padding: '20px',
    textAlign: 'center',
    borderRight: `1px solid ${theme.colors.border}`,
  },
  statValue: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
  },
  statLabel: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    marginTop: '4px',
    fontWeight: 500,
  },
  balanceRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: '16px',
    padding: '12px',
    background: 'rgba(0,0,0,0.2)',
    borderRadius: theme.borderRadius.md,
    gap: '8px',
  },
  balanceLabel: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  balanceValue: {
    fontSize: '16px',
    fontWeight: 600,
  },
  tabs: {
    display: 'flex',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  tab: {
    flex: 1,
    padding: '16px',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid transparent',
    color: theme.colors.textMuted,
    fontSize: '15px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  tabActive: {
    flex: 1,
    padding: '16px',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid theme.colors.accent',
    color: theme.colors.accent,
    fontSize: '15px',
    fontWeight: 600,
    cursor: 'pointer',
  },
  content: {
    padding: '24px 32px',
  },
  editForm: {
    maxWidth: 400,
  },
  formRow: {
    marginBottom: '20px',
  },
  formLabel: {
    display: 'block',
    color: theme.colors.textSecondary,
    marginBottom: '8px',
    fontSize: '14px',
    fontWeight: 500,
  },
  input: {
    width: '100%',
    padding: '12px 16px',
    fontSize: '15px',
    background: 'rgba(255,255,255,0.06)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    transition: 'all 0.2s ease',
    boxSizing: 'border-box',
  },
  formActions: {
    display: 'flex',
    gap: '12px',
    marginTop: '24px',
  },
  infoList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  infoRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: '16px',
    borderBottom: `1px solid ${theme.colors.border}`,
  },
  infoLabel: {
    color: theme.colors.textMuted,
    fontSize: '14px',
  },
  infoValue: {
    color: theme.colors.text,
    fontSize: '15px',
    fontWeight: 500,
  },
  emptyState: {
    textAlign: 'center',
    padding: '40px',
    color: theme.colors.textMuted,
    fontSize: '15px',
  },
  postsList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
  },
  postItem: {
    display: 'block',
    padding: '16px',
    background: 'rgba(255,255,255,0.04)',
    borderRadius: theme.borderRadius.md,
    border: `1px solid ${theme.colors.border}`,
    textDecoration: 'none',
    transition: 'all 0.2s ease',
  },
  postStatus: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    marginBottom: '8px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  statusText: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    fontWeight: 500,
    textTransform: 'uppercase',
  },
  postTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '6px',
  },
  postMeta: {
    display: 'flex',
    gap: '8px',
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  achievementsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
    gap: '16px',
  },
  achievementCard: {
    padding: '20px',
    background: 'rgba(255,255,255,0.04)',
    borderRadius: theme.borderRadius.md,
    border: '2px solid',
    textAlign: 'center',
    transition: 'all 0.3s ease',
    cursor: 'default',
    position: 'relative',
    overflow: 'hidden',
  },
  achievementIcon: {
    marginBottom: '8px',
    display: 'block',
  },
  achievementName: {
    color: theme.colors.text,
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '4px',
  },
  achievementDesc: {
    color: theme.colors.textMuted,
    fontSize: '13px',
    marginBottom: '8px',
    lineHeight: 1.4,
  },
  achievementRarity: {
    fontSize: '11px',
    fontWeight: 700,
    textTransform: 'uppercase',
    marginBottom: '8px',
    letterSpacing: '0.5px',
  },
  achievementDate: {
    color: theme.colors.textMuted,
    fontSize: '11px',
  },
  emptyIcon: {
    marginBottom: '16px',
    opacity: 0.8,
  },
  emptyHint: {
    color: theme.colors.textMuted,
    fontSize: '14px',
    marginTop: '8px',
  },
};
