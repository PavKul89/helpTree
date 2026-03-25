import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { chatApi } from '../api/chatApi';
import { useAuth } from '../context/AuthContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';
import type { User, UserPublic } from '../types';

export const ProfilePage = () => {
  const { userId } = useParams<{ userId?: string }>();
  const navigate = useNavigate();
  const { user: currentUser, setUser } = useAuth();
  
  const [profileUser, setProfileUser] = useState<User | UserPublic | null>(null);
  const [loading, setLoading] = useState(true);
  const [isOwnProfile, setIsOwnProfile] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({ name: '', email: '', phone: '', city: '' });

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
    
    loadUser();
    
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
      const updatedUser = await authApi.updateProfile(currentUser.id, {
        name: editForm.name,
        email: editForm.email,
        phone: editForm.phone || undefined,
        city: editForm.city || undefined,
      });
      setProfileUser(updatedUser);
      setUser(updatedUser);
      setIsEditing(false);
    } catch (err) {
      console.error('Ошибка при обновлении профиля:', err);
    }
  };

  if (loading) return <div style={styles.loading}>Загрузка...</div>;
  if (!profileUser) return <div style={styles.notFound}>Пользователь не найден</div>;

  const fullUser = profileUser as User;

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 style={styles.title}>{isOwnProfile ? 'Профиль' : `Профиль: ${profileUser.name}`}</h1>
      
      {isEditing ? (
        <Card>
          <h3 style={styles.sectionTitle}>Редактирование профиля</h3>
          <div style={styles.field}>
            <label style={styles.label}>Имя:</label>
            <input
              type="text"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Email:</label>
            <input
              type="email"
              value={editForm.email}
              onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Телефон:</label>
            <input
              type="text"
              value={editForm.phone}
              onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Город:</label>
            <input
              type="text"
              value={editForm.city}
              onChange={(e) => setEditForm({ ...editForm, city: e.target.value })}
              style={styles.input}
            />
          </div>
          <div style={styles.buttonGroup}>
            <Button onClick={handleSaveProfile} style={{ marginRight: 10 }}>
              Сохранить
            </Button>
            <Button variant="outline" onClick={() => setIsEditing(false)}>
              Отмена
            </Button>
          </div>
        </Card>
      ) : (
        <Card>
          <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Имя:</strong> {profileUser.name}</p>
          
          {'email' in profileUser && (
            <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Email:</strong> {profileUser.email}</p>
          )}
          
          {'phone' in fullUser && fullUser.phone && (
            <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Телефон:</strong> {fullUser.phone}</p>
          )}
          
          {'city' in fullUser && fullUser.city && (
            <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Город:</strong> {fullUser.city}</p>
          )}
          
          <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Рейтинг:</strong> ★ {profileUser.rating}</p>
          
          {'helpedCount' in profileUser && (
            <>
              <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Помог:</strong> {profileUser.helpedCount} раз</p>
              <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Долгов:</strong> {profileUser.debtCount}</p>
            </>
          )}
          
          {'role' in profileUser && (
            <>
              <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Роль:</strong> {profileUser.role}</p>
              <p style={styles.fieldRow}><strong style={styles.fieldLabel}>Telegram:</strong> {profileUser.telegramChatId || 'Не подключён'}</p>
            </>
          )}
        </Card>
      )}

      <div style={styles.actions}>
        {isOwnProfile && !isEditing && (
          <Button onClick={() => setIsEditing(true)}>
            Редактировать профиль
          </Button>
        )}

        {!isOwnProfile && (
          <Button onClick={handleStartChat}>
            Написать сообщение
          </Button>
        )}
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 600,
    margin: '0 auto',
    padding: '24px',
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '200px',
    color: theme.colors.text,
    fontSize: '18px',
  },
  notFound: {
    textAlign: 'center',
    padding: '40px',
    color: theme.colors.textMuted,
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    marginBottom: '24px',
  },
  sectionTitle: {
    color: theme.colors.text,
    marginBottom: '16px',
  },
  field: {
    marginBottom: '16px',
  },
  label: {
    display: 'block',
    color: theme.colors.textSecondary,
    marginBottom: '6px',
    fontSize: '14px',
  },
  input: {
    width: '100%',
    padding: '12px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: `1px solid ${theme.colors.border}`,
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
  },
  buttonGroup: {
    display: 'flex',
    gap: '12px',
    marginTop: '20px',
  },
  fieldRow: {
    marginBottom: '12px',
    color: theme.colors.text,
  },
  fieldLabel: {
    color: theme.colors.textSecondary,
    marginRight: '8px',
  },
  actions: {
    marginTop: '24px',
    display: 'flex',
    gap: '12px',
  },
};
