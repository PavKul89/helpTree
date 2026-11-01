import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, Clock, Flame, MapPin, MapPinOff } from 'lucide-react';
import type { Post } from '../types';
import { getCategoryIcon } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';
import { getRelativeTime } from '../utils/dateUtils';
import { hasCoordinates } from '../utils/coordinates';

interface MapPostCardProps {
  post: Post;
  variant: 'list' | 'spotlight';
  isSelected?: boolean;
  onSelect?: (post: Post) => void;
  onClose?: () => void;
}

export const MapPostCard = ({
  post,
  variant,
  isSelected = false,
  onSelect,
  onClose,
}: MapPostCardProps) => {
  const [imageFailed, setImageFailed] = useState(false);

  const CategoryIcon = getCategoryIcon(post.category);
  const preview = (post.imageUrls || []).find(Boolean);
  const showImage = Boolean(preview) && !imageFailed;
  const located = hasCoordinates(post);
  const statusLabel = getStatusLabel(post.status);
  const createdAtLabel = new Date(post.createdAt).toLocaleString('ru-RU');
  const cityLabel = post.userCity?.trim() || 'Город не указан';

  const thumbnail = (
    <span className={`map-card-thumb${showImage ? '' : ' map-card-thumb--placeholder'}`}>
      {showImage ? (
        <img
          src={preview}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setImageFailed(true)}
        />
      ) : (
        <CategoryIcon size={variant === 'spotlight' ? 22 : 24} strokeWidth={1.6} aria-hidden="true" />
      )}
      {post.boosted && (
        <span className="map-card-boost" title="Заявка поднята в топ">
          <Flame size={10} aria-hidden="true" />
          Топ
        </span>
      )}
    </span>
  );

  const categoryBadge = (
    <span className="map-card-category">
      <CategoryIcon size={12} aria-hidden="true" />
      <span>{post.category}</span>
    </span>
  );

  const meta = (
    <span className="map-card-meta">
      <span title={post.userCity || 'Город не указан'}>
        <MapPin size={12} aria-hidden="true" />
        {cityLabel}
      </span>
      <span title={createdAtLabel}>
        <Clock size={12} aria-hidden="true" />
        {getRelativeTime(post.createdAt)}
      </span>
    </span>
  );

  if (variant === 'spotlight') {
    return (
      <article className="map-card map-card--spotlight" aria-label={`Заявка: ${post.title}`}>
        <button
          type="button"
          className="map-card-close"
          onClick={onClose}
          aria-label="Закрыть карточку заявки"
        >
          <span aria-hidden="true">×</span>
        </button>

        <div className="map-card-spotlight-head">
          {thumbnail}
          <div className="map-card-spotlight-titles">
            <div className="map-card-badges">
              {categoryBadge}
              <span className={`map-card-status map-card-status--${post.status.toLowerCase()}`}>
                {statusLabel}
              </span>
            </div>
            <h3 className="map-card-title">{post.title}</h3>
          </div>
        </div>

        {post.description?.trim() && <p className="map-card-desc">{post.description.trim()}</p>}

        {meta}

        <div className="map-card-spotlight-foot">
          <span className="map-card-author" title={post.authorName}>
            {post.authorName?.trim() || 'Автор'}
          </span>
          <Link className="map-card-cta" to={`/posts/${post.id}`}>
            Открыть заявку
            <ArrowUpRight size={14} aria-hidden="true" />
          </Link>
        </div>
      </article>
    );
  }

  return (
    <article className={`map-card map-card--list${isSelected ? ' is-selected' : ''}`}>
      <button
        type="button"
        className="map-card-select"
        onClick={() => onSelect?.(post)}
        disabled={!located}
        aria-pressed={isSelected}
        aria-label={
          located
            ? `Показать заявку «${post.title}» на карте`
            : `Заявка «${post.title}» без координат`
        }
      >
        {thumbnail}

        <span className="map-card-body">
          <span className="map-card-badges">
            {categoryBadge}
            <span className={`map-card-status map-card-status--${post.status.toLowerCase()}`}>
              {statusLabel}
            </span>
          </span>

          <span className="map-card-title">{post.title}</span>

          {meta}

          {!located && (
            <span className="map-card-note">
              <MapPinOff size={12} aria-hidden="true" />
              Без координат — только в списке
            </span>
          )}
        </span>
      </button>

      <div className="map-card-foot">
        <span className="map-card-author" title={post.authorName}>
          {post.authorName?.trim() || 'Автор'}
        </span>
        <Link
          className="map-card-link"
          to={`/posts/${post.id}`}
          aria-label={`Открыть заявку: ${post.title}`}
        >
          Открыть
          <ArrowUpRight size={13} aria-hidden="true" />
        </Link>
      </div>
    </article>
  );
};
