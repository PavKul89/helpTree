import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bookmark, Clock, Flame, MapPin, Star } from 'lucide-react';
import type { Post } from '../types';
import { getCategoryIcon } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';
import { getRelativeTime } from '../utils/dateUtils';

const AVATAR_COLORS = ['#8CCB55', '#A9D874', '#7BB98A', '#C8F56A', '#6FAE7A'];

const getInitials = (name: string): string =>
  name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

const getAvatarColor = (name: string): string => {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
};

interface PostCatalogCardProps {
  post: Post;
  view: 'grid' | 'list';
  index: number;
  isFavorite: boolean;
  canFavorite: boolean;
  onToggleFavorite: (post: Post) => void;
}

export const PostCatalogCard = ({
  post,
  view,
  index,
  isFavorite,
  canFavorite,
  onToggleFavorite,
}: PostCatalogCardProps) => {
  const [imageFailed, setImageFailed] = useState(false);
  const [avatarFailed, setAvatarFailed] = useState(false);

  const images = (post.imageUrls || []).filter(Boolean);
  const hasImage = images.length > 0 && !imageFailed;
  const CategoryIcon = getCategoryIcon(post.category);
  const authorName = post.authorName?.trim() || 'Автор';
  const hasAvatar = Boolean(post.authorAvatarUrl) && !avatarFailed;

  return (
    <Link
      to={`/posts/${post.id}`}
      className={`catalog-card${view === 'list' ? ' catalog-card--list' : ''}`}
      style={{ animationDelay: `${Math.min(index, 9) * 35}ms` }}
      aria-label={`Открыть заявку: ${post.title}`}
    >
      <div className={`catalog-card-media${hasImage ? '' : ' catalog-card-media--placeholder'}`}>
        {hasImage ? (
          <img
            src={images[0]}
            alt=""
            loading="lazy"
            decoding="async"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <div className="catalog-card-placeholder" aria-hidden="true">
            <CategoryIcon size={40} strokeWidth={1.5} />
            <span>{post.category}</span>
          </div>
        )}

        <span className={`catalog-status catalog-status--${post.status.toLowerCase()}`}>
          {getStatusLabel(post.status)}
        </span>

        {post.boosted && (
          <span className="catalog-boost" title="Заявка поднята в топ">
            <Flame size={11} aria-hidden="true" />
            В топе
          </span>
        )}

        {hasImage && images.length > 1 && (
          <span className="catalog-card-image-count" aria-label={`Изображений: ${images.length}`}>
            +{images.length - 1}
          </span>
        )}
      </div>

      <div className="catalog-card-body">
        <div className="catalog-card-top">
          <span className="catalog-card-category">
            <CategoryIcon size={12} aria-hidden="true" />
            {post.category}
          </span>

          {canFavorite && (
            <span
              role="button"
              tabIndex={0}
              className={`catalog-card-fav${isFavorite ? ' is-active' : ''}`}
              aria-label={isFavorite ? 'Убрать из избранного' : 'Добавить в избранное'}
              aria-pressed={isFavorite}
              title={isFavorite ? 'Убрать из избранного' : 'В избранное'}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                onToggleFavorite(post);
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  event.stopPropagation();
                  onToggleFavorite(post);
                }
              }}
            >
              <Bookmark size={16} fill={isFavorite ? 'currentColor' : 'none'} aria-hidden="true" />
            </span>
          )}
        </div>

        <h3 className="catalog-card-title">{post.title}</h3>
        {post.description?.trim() && (
          <p className="catalog-card-desc">{post.description.trim()}</p>
        )}

        <div className="catalog-card-footer">
          <div className="catalog-card-author">
            <span
              className="catalog-card-avatar"
              style={hasAvatar ? undefined : { background: getAvatarColor(authorName) }}
            >
              {hasAvatar ? (
                <img
                  src={post.authorAvatarUrl}
                  alt=""
                  loading="lazy"
                  decoding="async"
                  onError={() => setAvatarFailed(true)}
                />
              ) : (
                <span aria-hidden="true">{getInitials(authorName)}</span>
              )}
            </span>
            <span className="catalog-card-author-name" title={authorName}>
              {authorName}
            </span>
            {post.authorRating > 0 && (
              <span className="catalog-card-rating" title={`Рейтинг автора: ${post.authorRating.toFixed(1)}`}>
                <Star size={11} fill="currentColor" aria-hidden="true" />
                {post.authorRating.toFixed(1)}
              </span>
            )}
          </div>

          <div className="catalog-card-meta">
            {post.userCity && (
              <span title={post.userCity}>
                <MapPin size={12} aria-hidden="true" />
                {post.userCity}
              </span>
            )}
            <span title={new Date(post.createdAt).toLocaleString('ru-RU')}>
              <Clock size={12} aria-hidden="true" />
              {getRelativeTime(post.createdAt)}
            </span>
          </div>
        </div>
      </div>
    </Link>
  );
};
