import { useState, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { postsApi } from '../api/postsApi';
import { imagesApi } from '../api/imagesApi';

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
    <div style={{ maxWidth: 600, margin: '50px auto', padding: 20 }}>
      <Link to="/">← Назад</Link>
      <h1>Создать пост</h1>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 15 }}>
          <label>Заголовок</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            style={{ width: '100%', padding: 8, display: 'block' }}
          />
        </div>
        <div style={{ marginBottom: 15 }}>
          <label>Описание</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            style={{ width: '100%', height: 150, padding: 8, display: 'block' }}
          />
        </div>
        <div style={{ marginBottom: 15 }}>
          <label>Категория</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            style={{ width: '100%', padding: 8, display: 'block' }}
          >
            <option value="Дрова">Дрова</option>
            <option value="Уборка">Уборка</option>
            <option value="Ремонт">Ремонт</option>
            <option value="Доставка">Доставка</option>
            <option value="Покупки">Покупки</option>
            <option value="Другое">Другое</option>
          </select>
        </div>
        <div style={{ marginBottom: 15 }}>
          <label>Картинки</label>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleImageChange}
            accept="image/*"
            multiple
            style={{ display: 'none' }}
          />
          <button type="button" onClick={() => fileInputRef.current?.click()} style={{ padding: '8px 16px' }}>
            Выбрать картинки
          </button>
          {previews.length > 0 && (
            <div style={{ display: 'flex', gap: 10, marginTop: 10, flexWrap: 'wrap' }}>
              {previews.map((preview, index) => (
                <div key={index} style={{ position: 'relative' }}>
                  <img src={preview} alt="" style={{ width: 100, height: 100, objectFit: 'cover', borderRadius: 8 }} />
                  <button
                    type="button"
                    onClick={() => removeImage(index)}
                    style={{
                      position: 'absolute',
                      top: -5,
                      right: -5,
                      background: 'red',
                      color: 'white',
                      border: 'none',
                      borderRadius: '50%',
                      width: 20,
                      height: 20,
                      cursor: 'pointer',
                    }}
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
        <button type="submit" disabled={loading} style={{ padding: '10px 20px' }}>
          {loading ? 'Создание...' : 'Создать'}
        </button>
      </form>
    </div>
  );
};
