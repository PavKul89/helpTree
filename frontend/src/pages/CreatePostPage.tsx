import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Leaf, ShieldCheck } from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { PostForm } from '../components/PostForm';
import { useToast } from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import '../components/PostForm.css';

export const CreatePostPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showToast } = useToast();

  const initialValues = useMemo(
    () => ({ title: '', description: '', category: '', imageUrls: [] }),
    []
  );

  const previewAuthor = useMemo(
    () => ({
      name: user?.name || 'Вы',
      avatarUrl: user?.avatarUrl,
      rating: user?.rating,
      city: user?.city,
    }),
    [user]
  );

  const handleSubmit = async (payload: {
    title: string;
    description: string;
    category: string;
    imageUrls: string[];
  }) => {
    const created = await postsApi.create({
      title: payload.title,
      description: payload.description,
      category: payload.category,
      imageUrls: payload.imageUrls,
    });

    showToast('Заявка опубликована', 'success');
    navigate(`/posts/${created.id}`, { replace: true });
  };

  return (
    <div className="post-editor-page">
      <header className="post-editor-hero">
        <Link to="/posts" className="post-editor-back">
          <ArrowLeft size={15} aria-hidden="true" />
          Назад в каталог
        </Link>

        <span className="post-editor-eyebrow">
          <Leaf size={13} aria-hidden="true" />
          Новая заявка
        </span>

        <h1>Расскажите, какая помощь вам нужна</h1>
        <p>
          Опишите свою ситуацию — возможно, рядом уже есть человек, который сможет помочь
        </p>

        <div className="post-editor-meta">
          <span>
            <ShieldCheck size={13} aria-hidden="true" />
            Публикация бесплатна
          </span>
          <span className="post-editor-meta-dot" aria-hidden="true" />
          <span>Заявка появится в каталоге сразу после публикации</span>
        </div>
      </header>

      <PostForm
        mode="create"
        initialValues={initialValues}
        previewAuthor={previewAuthor}
        locationLabel={user?.city}
        status="OPEN"
        onSubmit={handleSubmit}
        onCancel={() => navigate('/posts')}
      />
    </div>
  );
};
