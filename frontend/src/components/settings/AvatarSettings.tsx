import React, { useEffect, useRef, useState } from 'react';
import {
  AlertTriangle,
  Camera,
  Check,
  ImagePlus,
  Loader2,
  Upload,
  X,
} from 'lucide-react';
import { authApi } from '../../api/authApi';
import type { User } from '../../types';
import { getApiErrorInfo, isGenericValidationMessage } from '../../utils/apiErrors';
import { Avatar } from '../Avatar';
import { useToast } from '../Toast';
import { SettingsSection } from './SettingsSection';

const MAX_AVATAR_SIZE = 10 * 1024 * 1024;
const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/gif'];
const ALLOWED_AVATAR_EXTENSIONS = '.jpg,.jpeg,.png,.gif';

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} КБ`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
};

const validateAvatarFile = (file: File): string | null => {
  if (!file.size) {
    return 'Файл пустой. Выберите другое изображение.';
  }
  if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
    return 'Поддерживаются только JPG, PNG и GIF.';
  }
  if (file.size > MAX_AVATAR_SIZE) {
    return 'Размер файла не должен превышать 10 МБ.';
  }
  return null;
};

interface AvatarSettingsProps {
  user: User;
  onUpdated: (user: User) => void;
  onDirtyChange: (dirty: boolean) => void;
}

export const AvatarSettings: React.FC<AvatarSettingsProps> = ({
  user,
  onUpdated,
  onDirtyChange,
}) => {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (!selectedFile) {
      setPreviewUrl(null);
      return;
    }

    const objectUrl = URL.createObjectURL(selectedFile);
    setPreviewUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [selectedFile]);

  useEffect(() => {
    onDirtyChange(Boolean(selectedFile));
  }, [selectedFile, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    const validationError = validateAvatarFile(file);
    setSuccess(false);
    setError(validationError);
    setSelectedFile(validationError ? null : file);
  };

  const handleCancel = () => {
    if (uploading) return;
    setSelectedFile(null);
    setError(null);
    setSuccess(false);
  };

  const handleUpload = async () => {
    if (!selectedFile || uploading) return;

    setUploading(true);
    setError(null);
    setSuccess(false);

    try {
      const avatarUrl = await authApi.uploadAvatar(user.id, selectedFile);
      onUpdated({ ...user, avatarUrl });
      setSelectedFile(null);
      setSuccess(true);
      showToast('Фото профиля обновлено', 'success');
    } catch (uploadError: unknown) {
      const info = getApiErrorInfo(uploadError);
      const fieldMessage = info.fields.file;

      if (fieldMessage) {
        setError(fieldMessage);
      } else if (info.status === 413) {
        setError('Размер файла не должен превышать 10 МБ.');
      } else if (info.status === 403) {
        setError('Недостаточно прав для изменения фото профиля.');
      } else if (!info.status) {
        setError('Не удалось связаться с сервером. Проверьте подключение и повторите попытку.');
      } else if (info.message && !isGenericValidationMessage(info.message)) {
        setError(info.message);
      } else {
        setError('Не удалось загрузить фото. Попробуйте другое изображение.');
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <SettingsSection
      id="avatar"
      eyebrow="Фотография профиля"
      title="Аватар"
      description="Фото отображается в профиле, меню и рядом с вашими сообщениями. Поддерживаются JPG, PNG и GIF размером до 10 МБ."
      icon={<Camera size={22} />}
    >
      <div className="settings-avatar-layout">
        <div className="settings-avatar-preview">
          <div className="settings-avatar-frame">
            <Avatar
              name={user.name}
              avatarUrl={previewUrl ?? user.avatarUrl}
              size="xlarge"
            />
            {selectedFile && <span className="settings-avatar-badge">Предпросмотр</span>}
          </div>
          <p className="settings-avatar-caption">
            {selectedFile
              ? 'Новое фото ещё не загружено'
              : user.avatarUrl
                ? 'Текущее фото профиля'
                : 'Фото пока не загружено'}
          </p>
        </div>

        <div className="settings-avatar-copy">
          <div className="settings-avatar-heading">
            <span className="settings-avatar-icon" aria-hidden="true">
              <ImagePlus size={18} />
            </span>
            <div>
              <h3>{selectedFile ? 'Проверьте новое фото' : 'Выберите фотографию'}</h3>
              <p>
                {selectedFile
                  ? 'После загрузки новое изображение заменит текущее во всём приложении.'
                  : 'Используйте квадратное изображение — так оно аккуратнее смотрится в круглой рамке.'}
              </p>
            </div>
          </div>

          <input
            ref={fileInputRef}
            type="file"
            className="settings-file-input"
            accept={`${ALLOWED_AVATAR_EXTENSIONS},${ALLOWED_AVATAR_TYPES.join(',')}`}
            onChange={handleFileChange}
            tabIndex={-1}
            aria-hidden="true"
          />

          {selectedFile && (
            <p className="settings-avatar-file">
              <span>{selectedFile.name}</span>
              <small>{formatFileSize(selectedFile.size)}</small>
            </p>
          )}

          {error && (
            <div className="settings-alert" role="alert">
              <AlertTriangle size={17} aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}

          {success && (
            <div className="settings-success" role="status">
              <Check size={16} aria-hidden="true" />
              <span>Фото профиля обновлено. Оно уже отображается в меню и профиле.</span>
            </div>
          )}

          <div className="settings-avatar-actions">
            <button
              type="button"
              className="settings-button settings-button--secondary"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
            >
              <ImagePlus size={16} aria-hidden="true" />
              {selectedFile ? 'Выбрать другое' : 'Выбрать фото'}
            </button>

            {selectedFile && (
              <>
                <button
                  type="button"
                  className="settings-button settings-button--primary"
                  onClick={handleUpload}
                  disabled={uploading}
                  aria-busy={uploading}
                >
                  {uploading ? (
                    <Loader2 className="settings-spinner" size={16} aria-hidden="true" />
                  ) : (
                    <Upload size={16} aria-hidden="true" />
                  )}
                  {uploading ? 'Загружаем…' : 'Загрузить фото'}
                </button>
                <button
                  type="button"
                  className="settings-button settings-button--ghost"
                  onClick={handleCancel}
                  disabled={uploading}
                >
                  <X size={16} aria-hidden="true" />
                  Отменить
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </SettingsSection>
  );
};
