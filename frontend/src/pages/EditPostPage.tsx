import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  FileQuestion,
  Lock,
  PencilLine,
  RefreshCw,
} from 'lucide-react';
import { postsApi } from '../api/postsApi';
import { PostForm } from '../components/PostForm';
import { useToast } from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import type { Post } from '../types';
import { getStatusLabel } from '../utils/statusHelpers';
import '../components/PostForm.css';

type PageState = 'loading' | 'ready' | 'notfound' | 'forbidden' | 'error';

interface StateCardProps {
  variant: 'notfound' | 'forbidden' | 'error';
  postId?: number;
  onRetry?: () => void;
}

const StateCard = ({ variant, postId, onRetry }: StateCardProps) => {
  const navigate = useNavigate();

  const content = {
    notfound: {
      icon: <FileQuestion size={26} aria-hidden="true" />,
      title: 'Заявка не найдена',
      message:
        'Возможно, её удалили или ссылка устарела. Вернитесь в каталог и выберите другую заявку.',
      primary: postId
        ? { label: 'Открыть заявку', onClick: () => navigate(`/posts/${postId}`) }
        : null,
    },
    forbidden: {
      icon: <Lock size={26} aria-hidden="true" />,
      title: 'Нет доступа к редактированию',
      message:
        'Редактировать заявку может только её автор или администратор. Если вы считаете, что это ошибка, обновите страницу или войдите под другим аккаунтом.',
      primary: postId
        ? { label: 'Открыть заявку', onClick: () => navigate(`/posts/${postId}`) }
        : null,
    },
    error: {
      icon: <AlertTriangle size={26} aria-hidden="true" />,
      title: 'Не удалось загрузить заявку',
      message:
        'Сервер не ответил или произошла ошибка соединения. Проверьте интернет и попробуйте ещё раз — введённые данные не потеряны.',
      primary: onRetry ? { label: 'Попробовать снова', onClick: onRetry } : null,
    },
  }[variant];

  return (
    <div
      className={`post-editor-state${variant === 'notfound' ? '' : ' post-editor-state--danger'}`}
      role="alert"
    >
      <span className="post-editor-state-icon">{content.icon}</span>
      <h1>{content.title}</h1>
      <p>{content.message}</p>
      <div className="post-editor-state-actions">
        {content.primary && (
          <button type="button" className="post-form-btn post-form-btn--primary" onClick={content.primary.onClick}>
            {variant === 'error' && <RefreshCw size={16} aria-hidden="true" />}
            {content.primary.label}
          </button>
        )}
        <button
          type="button"
          className="post-form-btn post-form-btn--ghost"
          onClick={() => navigate('/posts')}
        >
          В каталог
        </button>
      </div>
    </div>
  );
};

export const EditPostPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showToast } = useToast();

  const numericId = Number(id);
  const hasValidId = Number.isInteger(numericId) && numericId > 0;

  const [state, setState] = useState<PageState>(hasValidId ? 'loading' : 'notfound');
  const [post, setPost] = useState<Post | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    if (!hasValidId) {
      setState('notfound');
      return;
    }

    let active = true;
    setState('loading');

    postsApi
      .getById(numericId)
      .then((data) => {
        if (!active) return;
        setPost(data);
        if (user && user.id !== data.userId && user.role !== 'ADMIN') {
          setState('forbidden');
        } else {
          setState('ready');
        }
      })
      .catch((error: unknown) => {
        if (!active) return;
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 404) {
          setState('notfound');
        } else if (status === 403) {
          setState('forbidden');
        } else {
          setState('error');
        }
      });

    return () => {
      active = false;
    };
  }, [hasValidId, numericId, reloadTick, user]);

  const handleRetry = useCallback(() => {
    setPost(null);
    setReloadTick((tick) => tick + 1);
  }, []);

  const initialValues = useMemo(
    () => ({
      title: post?.title ?? '',
      description: post?.description ?? '',
      category: post?.category ?? '',
      imageUrls: post?.imageUrls ?? [],
    }),
    [post]
  );

  const previewAuthor = useMemo(
    () => ({
      name: post?.authorName || user?.name || 'Вы',
      avatarUrl: post?.authorAvatarUrl || user?.avatarUrl,
      rating: post?.authorRating ?? user?.rating,
      city: post?.userCity || user?.city,
    }),
    [post, user]
  );

  const handleSubmit = async (payload: {
    title: string;
    description: string;
    category: string;
    imageUrls: string[];
  }) => {
    if (!post) throw new Error('post-not-loaded');

    const updated = await postsApi.update(post.id, {
      title: payload.title,
      description: payload.description,
      imageUrls: payload.imageUrls,
    });

    showToast('Изменения сохранены', 'success');
    navigate(`/posts/${updated.id}`, { replace: true });
  };

  const backTarget = post ? `/posts/${post.id}` : '/posts';

  return (
    <div className="post-editor-page">
      <header className="post-editor-hero">
        <Link to={backTarget} className="post-editor-back">
          <ArrowLeft size={15} aria-hidden="true" />
          {post ? 'Назад к заявке' : 'Назад в каталог'}
        </Link>

        <span className="post-editor-eyebrow">
          <PencilLine size={13} aria-hidden="true" />
          Редактирование
        </span>

        <h1>Редактирование заявки</h1>
        <p>
          Обновите заголовок, описание или фотографии — изменения увидят все, кто откроет заявку
          после сохранения
        </p>

        {post && (
          <div className="post-editor-meta">
            <span>Заявка №{post.id}</span>
            <span className="post-editor-meta-dot" aria-hidden="true" />
            <span>{post.category}</span>
            <span className="post-editor-meta-dot" aria-hidden="true" />
            <span>{getStatusLabel(post.status)}</span>
          </div>
        )}
      </header>

      {state === 'loading' && (
        <div
          className="post-editor-skeleton"
          role="status"
          aria-label="Загрузка заявки"
          aria-busy="true"
        >
          <div className="post-editor-skeleton-main" aria-hidden="true">
            <div className="post-editor-skeleton-card">
              <div className="post-editor-skeleton-line post-editor-skeleton-line--title" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--field" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--field" />
            </div>
            <div className="post-editor-skeleton-card">
              <div className="post-editor-skeleton-line post-editor-skeleton-line--title" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--field" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--short" />
            </div>
            <div className="post-editor-skeleton-card">
              <div className="post-editor-skeleton-line post-editor-skeleton-line--title" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--upload" />
            </div>
          </div>
          <div className="post-editor-skeleton-aside" aria-hidden="true">
            <div className="post-editor-skeleton-card">
              <div className="post-editor-skeleton-line post-editor-skeleton-line--short" />
              <div className="post-editor-skeleton-line" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--short" />
            </div>
            <div className="post-editor-skeleton-card">
              <div className="post-editor-skeleton-line post-editor-skeleton-line--title" />
              <div className="post-editor-skeleton-line post-editor-skeleton-line--preview" />
              <div className="post-editor-skeleton-line" />
            </div>
          </div>
        </div>
      )}

      {state === 'notfound' && (
        <StateCard variant="notfound" postId={hasValidId ? numericId : undefined} />
      )}

      {state === 'forbidden' && (
        <StateCard variant="forbidden" postId={post?.id ?? numericId} />
      )}

      {state === 'error' && <StateCard variant="error" onRetry={handleRetry} />}

      {state === 'ready' && post && (
        <PostForm
          key={post.id}
          mode="edit"
          initialValues={initialValues}
          previewAuthor={previewAuthor}
          locationLabel={post.userCity}
          status={post.status}
          onSubmit={handleSubmit}
          onCancel={() => navigate(`/posts/${post.id}`)}
        />
      )}
    </div>
  );
};
