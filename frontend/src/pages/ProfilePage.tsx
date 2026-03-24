import { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { chatApi } from '../api/chatApi';
import { useAuth } from '../context/AuthContext';
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

  if (loading) return <div>Загрузка...</div>;
  if (!profileUser) return <div>Пользователь не найден</div>;

  const fullUser = profileUser as User;

  return (
    <div style={{ maxWidth: 600, margin: '50px auto', padding: 20 }}>
      <Link to="/">← На главную</Link>
      <h1>{isOwnProfile ? 'Профиль' : `Профиль: ${profileUser.name}`}</h1>
      
      {isEditing ? (
        <div style={{ border: '1px solid #ddd', padding: 20, borderRadius: 8 }}>
          <h3>Редактирование профиля</h3>
          <div style={{ marginBottom: 15 }}>
            <label>Имя:</label>
            <input
              type="text"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
              style={{ width: '100%', padding: 8 }}
            />
          </div>
          <div style={{ marginBottom: 15 }}>
            <label>Email:</label>
            <input
              type="email"
              value={editForm.email}
              onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
              style={{ width: '100%', padding: 8 }}
            />
          </div>
          <div style={{ marginBottom: 15 }}>
            <label>Телефон:</label>
            <input
              type="text"
              value={editForm.phone}
              onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
              style={{ width: '100%', padding: 8 }}
            />
          </div>
          <div style={{ marginBottom: 15 }}>
            <label>Город:</label>
            <input
              type="text"
              value={editForm.city}
              onChange={(e) => setEditForm({ ...editForm, city: e.target.value })}
              style={{ width: '100%', padding: 8 }}
            />
          </div>
          <button onClick={handleSaveProfile} style={{ marginRight: 10, padding: '8px 16px' }}>
            Сохранить
          </button>
          <button onClick={() => setIsEditing(false)} style={{ padding: '8px 16px' }}>
            Отмена
          </button>
        </div>
      ) : (
        <div style={{ border: '1px solid #ddd', padding: 20, borderRadius: 8 }}>
          <p><strong>Имя:</strong> {profileUser.name}</p>
          
          {'email' in profileUser && (
            <p><strong>Email:</strong> {profileUser.email}</p>
          )}
          
          {'phone' in fullUser && fullUser.phone && (
            <p><strong>Телефон:</strong> {fullUser.phone}</p>
          )}
          
          {'city' in fullUser && fullUser.city && (
            <p><strong>Город:</strong> {fullUser.city}</p>
          )}
          
          <p><strong>Рейтинг:</strong> {profileUser.rating}</p>
          
          {'helpedCount' in profileUser && (
            <>
              <p><strong>Помог:</strong> {profileUser.helpedCount} раз</p>
              <p><strong>Долгов:</strong> {profileUser.debtCount}</p>
            </>
          )}
          
          {'role' in profileUser && (
            <>
              <p><strong>Роль:</strong> {profileUser.role}</p>
              <p><strong>Telegram:</strong> {profileUser.telegramChatId || 'Не подключён'}</p>
            </>
          )}
        </div>
      )}

      {isOwnProfile && !isEditing && (
        <button 
          onClick={() => setIsEditing(true)}
          style={{ marginTop: 20, padding: '10px 20px', cursor: 'pointer' }}
        >
          Редактировать профиль
        </button>
      )}

      {!isOwnProfile && (
        <button 
          onClick={handleStartChat}
          style={{ marginTop: 20, padding: '10px 20px', cursor: 'pointer' }}
        >
          Написать сообщение
        </button>
      )}
    </div>
  );
};
