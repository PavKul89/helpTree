import { useState, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { imagesApi } from '../api/imagesApi';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';

const CATEGORIES = [
  { value: 'Дрова', icon: '🪓', label: 'Дрова' },
  { value: 'Уборка', icon: '🧹', label: 'Уборка' },
  { value: 'Ремонт', icon: '🔧', label: 'Ремонт' },
  { value: 'Доставка', icon: '🚚', label: 'Доставка' },
  { value: 'Покупки', icon: '🛒', label: 'Покупки' },
  { value: 'Другое', icon: '✨', label: 'Другое' },
];

export const CreatePostPage = () => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('TECHNICAL');
  const [images, setImages] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    setImages([...images, ...files]);
    const newPreviews = files.map((file) => URL.createObjectURL(file));
    setPreviews([...previews, ...newPreviews]);
  };

  const removeImage = (index: number) => {
    setImages(images.filter((_, i) => i !== index));
    setPreviews(previews.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      let imageUrls: string[] = [];
      if (images.length > 0) {
        imageUrls = await imagesApi.uploadMultiple(images);
      }
      await postsApi.create({ title, description, category, imageUrls });
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка создания поста');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 style={styles.title}>Создать пост</h1>
      {error && <div style={styles.errorBox}><span>⚠️</span> {error}</div>}
      <Card>
        <form onSubmit={handleSubmit}>
          <div style={styles.field}>
            <label style={styles.label}>
              <span style={styles.labelIcon}>📝</span> Заголовок
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              placeholder="Например: Нужно наколоть дрова"
              style={styles.input}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <span style={styles.labelIcon}>📄</span> Описание
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              required
              placeholder="Подробно опишите, какая помощь нужна..."
              style={{ ...styles.input, height: 150 }}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <span style={styles.labelIcon}>🏷️</span> Категория
            </label>
            <div style={styles.categoryGrid}>
              {CATEGORIES.map((cat) => (
                <div
                  key={cat.value}
                  style={{
                    ...styles.categoryItem,
                    ...(category === cat.value ? styles.categoryItemActive : {}),
                  }}
                  onClick={() => setCategory(cat.value)}
                >
                  <span style={styles.categoryIcon}>{cat.icon}</span>
                  <span>{cat.label}</span>
                </div>
              ))}
            </div>
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <span style={styles.labelIcon}>📷</span> Картинки
            </label>
            <div 
              style={styles.uploadArea}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleImageChange}
                accept="image/*"
                multiple
                style={{ display: 'none' }}
              />
              <div style={styles.uploadContent}>
                <span style={styles.uploadIcon}>📁</span>
                <span style={styles.uploadText}>Нажмите или перетащите изображения</span>
                <span style={styles.uploadHint}>PNG, JPG до 10MB</span>
              </div>
            </div>
            {previews.length > 0 && (
              <div style={styles.previewGrid}>
                {previews.map((preview, index) => (
                  <div key={index} style={styles.previewItem}>
                    <img src={preview} alt="" style={styles.previewImage} />
                    <button
                      type="button"
                      onClick={() => removeImage(index)}
                      style={styles.removeBtn}
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
          <Button type="submit" disabled={loading} style={styles.submitBtn}>
            {loading ? '⏳ Создание...' : '✨ Создать пост'}
          </Button>
        </form>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 700,
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
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    marginBottom: '24px',
    fontWeight: 700,
  },
  errorBox: {
    background: 'rgba(239, 68, 68, 0.15)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: theme.borderRadius.md,
    padding: '14px 18px',
    color: '#FCA5A5',
    marginBottom: '20px',
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  field: {
    marginBottom: '24px',
  },
  label: {
    display: 'flex',
    alignItems: 'center',
    color: theme.colors.textSecondary,
    marginBottom: '10px',
    fontSize: '15px',
    fontWeight: 500,
  },
  labelIcon: {
    marginRight: '8px',
    fontSize: '16px',
  },
  input: {
    width: '100%',
    padding: '14px 18px',
    fontSize: '16px',
    background: 'rgba(255,255,255,0.08)',
    border: '1px solid rgba(34, 211, 238, 0.2)',
    borderRadius: theme.borderRadius.md,
    color: theme.colors.text,
    outline: 'none',
    transition: 'all 0.3s ease',
    boxSizing: 'border-box',
  },
  categoryGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(3, 1fr)',
    gap: '12px',
  },
  categoryItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
    padding: '14px 16px',
    background: 'rgba(255,255,255,0.08)',
    border: '1px solid rgba(34, 211, 238, 0.2)',
    borderRadius: theme.borderRadius.md,
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    fontSize: '14px',
    color: theme.colors.textSecondary,
  },
  categoryItemActive: {
    background: 'linear-gradient(135deg, rgba(34, 211, 238, 0.3) 0%, rgba(6, 182, 212, 0.3) 100%)',
    border: '1px solid #22d3ee',
    color: '#fff',
    boxShadow: '0 0 15px rgba(34, 211, 238, 0.3)',
  },
  categoryIcon: {
    fontSize: '20px',
  },
  uploadArea: {
    border: '2px dashed rgba(34, 211, 238, 0.3)',
    borderRadius: theme.borderRadius.lg,
    padding: '32px',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    background: 'rgba(255,255,255,0.03)',
  },
  uploadContent: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '8px',
  },
  uploadIcon: {
    fontSize: '40px',
    marginBottom: '8px',
  },
  uploadText: {
    color: theme.colors.text,
    fontSize: '15px',
  },
  uploadHint: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  previewGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(100px, 1fr))',
    gap: '12px',
    marginTop: '16px',
  },
  previewItem: {
    position: 'relative',
    borderRadius: theme.borderRadius.md,
    overflow: 'hidden',
    aspectRatio: '1',
  },
  previewImage: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  },
  removeBtn: {
    position: 'absolute',
    top: '4px',
    right: '4px',
    background: theme.colors.error,
    color: 'white',
    border: '2px solid rgba(255,255,255,0.8)',
    borderRadius: '50%',
    width: 24,
    height: 24,
    cursor: 'pointer',
    fontSize: '14px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'all 0.2s ease',
  },
  submitBtn: {
    width: '100%',
    padding: '16px',
    fontSize: '16px',
    fontWeight: 700,
    marginTop: '8px',
  },
};
