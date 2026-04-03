import { useState, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { 
  Axe, Trash2, Wrench, Truck, ShoppingCart, ChefHat, Flower2, 
  Car, Dog, Baby, Laptop, Scissors, Pill, Scale, BookOpen, GraduationCap,
  CarFront, Home, Sparkles, Package, Heart, Brain, Wifi, Camera, Music,
  Palette, Trophy, Plane, Bird, Plug, Shirt, Apple, Syringe, CreditCard,
  Shield, Building, Star, FileText, Image, FolderOpen, AlertTriangle,
  Loader2, Send
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { imagesApi } from '../api/imagesApi';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { theme } from '../theme';
import { useToast } from '../components/Toast';

const CATEGORY_ICONS: Record<string, React.ElementType> = {
  'Дрова': Axe,
  'Уборка': Trash2,
  'Ремонт': Wrench,
  'Доставка': Truck,
  'Покупки': ShoppingCart,
  'Готовка': ChefHat,
  'Садоводство': Flower2,
  'Перевозка': Car,
  'Уход за животными': Dog,
  'Помощь с детьми': Baby,
  'Компьютерная помощь': Laptop,
  'Стрижка': Scissors,
  'Медицинская помощь': Pill,
  'Юридическая консультация': Scale,
  'Обучение': BookOpen,
  'Репетитор': GraduationCap,
  'Транспорт': CarFront,
  'Строительство': Home,
  'Клининг': Sparkles,
  'Курьер': Package,
  'Волонтёрство': Heart,
  'Психологическая помощь': Brain,
  'Интернет и связь': Wifi,
  'Фото и видео': Camera,
  'Музыка': Music,
  'Искусство': Palette,
  'Спорт': Trophy,
  'Путешествия': Plane,
  'Питомцы': Bird,
  'Бытовая техника': Plug,
  'Одежда и обувь': Shirt,
  'Продукты': Apple,
  'Аптека': Syringe,
  'Банковские услуги': CreditCard,
  'Страхование': Shield,
  'Недвижимость': Building,
  'Другое': Star,
};

const CATEGORIES = Object.keys(CATEGORY_ICONS).map(value => ({
  value,
  label: value,
}));

export const CreatePostPage = () => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('Другое');
  const [images, setImages] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<{ title?: string; description?: string }>({});
  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const validate = () => {
    const newErrors: { title?: string; description?: string } = {};
    
    if (!title.trim()) {
      newErrors.title = 'Название обязательно';
    } else if (title.trim().length < 5) {
      newErrors.title = 'Название должно быть минимум 5 символов';
    } else if (title.trim().length > 100) {
      newErrors.title = 'Название должно быть не более 100 символов';
    }
    
    if (!description.trim()) {
      newErrors.description = 'Описание обязательно';
    } else if (description.trim().length < 20) {
      newErrors.description = 'Описание должно быть минимум 20 символов';
    } else if (description.trim().length > 2000) {
      newErrors.description = 'Описание должно быть не более 2000 символов';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

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
    
    if (!validate()) {
      return;
    }
    
    setLoading(true);
    try {
      let imageUrls: string[] = [];
      if (images.length > 0) {
        imageUrls = await imagesApi.uploadMultiple(images);
      }
      await postsApi.create({ title, description, category, imageUrls });
      showToast('Пост успешно создан!', 'success');
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка создания поста');
      showToast(err.response?.data?.message || 'Ошибка создания поста', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 className="page-title" style={styles.title}>Создать пост</h1>
      {error && <div style={styles.errorBox}><AlertTriangle size={18} color="#fca5a5" /> {error}</div>}
      <Card>
        <form onSubmit={handleSubmit}>
          <div style={styles.field}>
            <label style={styles.label}>
              <FileText size={18} style={styles.labelIcon as React.CSSProperties} /> Заголовок
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => { setTitle(e.target.value); setErrors({...errors, title: undefined}); }}
              placeholder="Например: Нужно наколоть дрова"
              style={{
                ...styles.input,
                ...(errors.title ? styles.inputError : {}),
              }}
            />
            {errors.title && <div style={styles.errorText}>{errors.title}</div>}
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <FileText size={18} style={styles.labelIcon as React.CSSProperties} /> Описание
            </label>
            <textarea
              value={description}
              onChange={(e) => { setDescription(e.target.value); setErrors({...errors, description: undefined}); }}
              placeholder="Подробно опишите, какая помощь нужна..."
              style={{
                ...styles.input,
                height: 150,
                ...(errors.description ? styles.inputError : {}),
              }}
            />
            {errors.description && <div style={styles.errorText}>{errors.description}</div>}
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <Sparkles size={18} style={styles.labelIcon as React.CSSProperties} /> Категория
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              style={styles.select}
            >
              {CATEGORIES.map((cat) => {
                const IconComponent = CATEGORY_ICONS[cat.value];
                return (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                );
              })}
            </select>
          </div>
          <div style={styles.field}>
            <label style={styles.label}>
              <Image size={18} style={styles.labelIcon as React.CSSProperties} /> Картинки
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
                <FolderOpen size={40} color={theme.colors.accent} style={{ marginBottom: 8 }} />
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
            {loading ? (
              <><Loader2 size={18} style={{ marginRight: 8, animation: 'spin 1s linear infinite' }} /> Создание...</>
            ) : (
              <><Send size={18} style={{ marginRight: 8 }} /> Создать пост</>
            )}
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
  inputError: {
    borderColor: '#ef4444',
  },
  errorText: {
    color: '#ef4444',
    fontSize: '13px',
    marginTop: '6px',
  },
  select: {
    width: '100%',
    padding: '14px 40px 14px 18px',
    fontSize: '16px',
    background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.15) 0%, rgba(8, 145, 178, 0.15) 100%)',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    borderRadius: theme.borderRadius.lg,
    color: theme.colors.text,
    outline: 'none',
    cursor: 'pointer',
    boxSizing: 'border-box',
    appearance: 'none',
    backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2322d3ee' d='M7 9L2 4h10z'/%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 14px center',
    transition: 'all 0.2s ease',
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
