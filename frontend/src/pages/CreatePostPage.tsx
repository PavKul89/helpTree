import { useState, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { imagesApi } from '../api/imagesApi';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';

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
      {error && <p style={styles.error}>{error}</p>}
      <Card>
        <form onSubmit={handleSubmit}>
          <div style={styles.field}>
            <label style={styles.label}>Заголовок</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              style={styles.input}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Описание</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              required
              style={{ ...styles.input, height: 150 }}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Категория</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              style={styles.select}
            >
              <option value="Дрова">Дрова</option>
              <option value="Уборка">Уборка</option>
              <option value="Ремонт">Ремонт</option>
              <option value="Доставка">Доставка</option>
              <option value="Покупки">Покупки</option>
              <option value="Другое">Другое</option>
            </select>
          </div>
          <div style={styles.field}>
            <label style={styles.label}>Картинки</label>
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleImageChange}
              accept="image/*"
              multiple
              style={{ display: 'none' }}
            />
            <Button type="button" onClick={() => fileInputRef.current?.click()}>
              Выбрать картинки
            </Button>
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
          <Button type="submit" disabled={loading}>
            {loading ? 'Создание...' : 'Создать'}
          </Button>
        </form>
      </Card>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 600,
    margin: '0 auto',
    padding: '24px',
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
  error: {
    color: theme.colors.error,
    marginBottom: '16px',
  },
  field: {
    marginBottom: '20px',
  },
  label: {
    display: 'block',
    color: theme.colors.textSecondary,
    marginBottom: '8px',
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
  select: {
    width: '100%',
    padding: '12px',
    fontSize: '15px',
    backgroundColor: theme.select.backgroundColor,
    border: theme.select.border,
    borderRadius: theme.borderRadius.md,
    color: theme.select.color,
    outline: 'none',
  },
  previewGrid: {
    display: 'flex',
    gap: '12px',
    marginTop: '16px',
    flexWrap: 'wrap',
  },
  previewItem: {
    position: 'relative',
  },
  previewImage: {
    width: 100,
    height: 100,
    objectFit: 'cover',
    borderRadius: theme.borderRadius.md,
  },
  removeBtn: {
    position: 'absolute',
    top: -8,
    right: -8,
    background: theme.colors.error,
    color: 'white',
    border: 'none',
    borderRadius: '50%',
    width: 24,
    height: 24,
    cursor: 'pointer',
    fontSize: '16px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
};
