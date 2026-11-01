import { ChevronDown, LayoutGrid, MapPin, User, X } from 'lucide-react';
import type { Post } from '../types';
import { CATEGORIES, getCategoryIcon } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';

export const POST_STATUSES: Array<Post['status']> = [
  'OPEN',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
];

interface CatalogFiltersProps {
  idPrefix?: string;
  category: string;
  status: string;
  city: string;
  author: string;
  userCity?: string;
  onCategoryChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onCityChange: (value: string) => void;
  onAuthorChange: (value: string) => void;
  onUseUserCity?: () => void;
  onSubmit?: () => void;
}

/**
 * Single source of filter controls for both the desktop panel and the
 * mobile bottom sheet. Every control maps to a real `GET /api/posts` query
 * parameter (category, status, city, authorName).
 */
export const CatalogFilters = ({
  idPrefix = 'catalog',
  category,
  status,
  city,
  author,
  userCity,
  onCategoryChange,
  onStatusChange,
  onCityChange,
  onAuthorChange,
  onUseUserCity,
  onSubmit,
}: CatalogFiltersProps) => {
  const CategoryIcon = category ? getCategoryIcon(category) : LayoutGrid;

  const handleEnter = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      onSubmit?.();
    }
  };

  return (
    <div className="catalog-filters">
      <div className="catalog-field catalog-field--category">
        <label className="catalog-field-label" htmlFor={`${idPrefix}-category`}>
          Категория
        </label>
        <div className="catalog-select-wrap">
          <span className="catalog-select-icon" aria-hidden="true">
            <CategoryIcon size={16} />
          </span>
          <select
            id={`${idPrefix}-category`}
            className="catalog-select"
            value={category}
            onChange={(event) => onCategoryChange(event.target.value)}
          >
            <option value="">Все категории</option>
            {CATEGORIES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
          <ChevronDown className="catalog-select-chevron" size={16} aria-hidden="true" />
        </div>
      </div>

      <div className="catalog-field catalog-field--status">
        <span className="catalog-field-label" id={`${idPrefix}-status-label`}>
          Статус
        </span>
        <div className="catalog-status-chips" role="group" aria-labelledby={`${idPrefix}-status-label`}>
          <button
            type="button"
            className={`catalog-status-chip${status === '' ? ' is-active' : ''}`}
            aria-pressed={status === ''}
            onClick={() => onStatusChange('')}
          >
            Все статусы
          </button>
          {POST_STATUSES.map((item) => (
            <button
              key={item}
              type="button"
              className={`catalog-status-chip catalog-status-chip--${item.toLowerCase()}${
                status === item ? ' is-active' : ''
              }`}
              aria-pressed={status === item}
              onClick={() => onStatusChange(item)}
            >
              <span className="catalog-status-dot" aria-hidden="true" />
              {getStatusLabel(item)}
            </button>
          ))}
        </div>
      </div>

      <div className="catalog-field">
        <label className="catalog-field-label" htmlFor={`${idPrefix}-city`}>
          Местоположение
        </label>
        <div className="catalog-input-wrap">
          <MapPin size={15} aria-hidden="true" />
          <input
            id={`${idPrefix}-city`}
            type="text"
            value={city}
            placeholder="Город или местоположение"
            autoComplete="off"
            onChange={(event) => onCityChange(event.target.value)}
            onKeyDown={handleEnter}
          />
          {city && (
            <button
              type="button"
              className="catalog-input-clear"
              aria-label="Очистить город"
              onClick={() => onCityChange('')}
            >
              <X size={13} aria-hidden="true" />
            </button>
          )}
        </div>
        {userCity && userCity !== city && onUseUserCity && (
          <button type="button" className="catalog-use-city" onClick={onUseUserCity}>
            Мой город: {userCity}
          </button>
        )}
      </div>

      <div className="catalog-field">
        <label className="catalog-field-label" htmlFor={`${idPrefix}-author`}>
          Автор
        </label>
        <div className="catalog-input-wrap">
          <User size={15} aria-hidden="true" />
          <input
            id={`${idPrefix}-author`}
            type="text"
            value={author}
            placeholder="Имя автора"
            autoComplete="off"
            onChange={(event) => onAuthorChange(event.target.value)}
            onKeyDown={handleEnter}
          />
          {author && (
            <button
              type="button"
              className="catalog-input-clear"
              aria-label="Очистить автора"
              onClick={() => onAuthorChange('')}
            >
              <X size={13} aria-hidden="true" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
