import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  Check,
  Eye,
  ImagePlus,
  Leaf,
  Lightbulb,
  Loader2,
  Lock,
  MapPin,
  RotateCcw,
  Save,
  Send,
  Star,
  UploadCloud,
  X,
} from 'lucide-react';
import { imagesApi } from '../api/imagesApi';
import type { Post } from '../types';
import { useAuth } from '../context/AuthContext';
import { CATEGORIES, getCategoryIcon } from '../utils/categoryIcons';
import { getStatusLabel } from '../utils/statusHelpers';
import './PostForm.css';

const MAX_FILE_SIZE = 10 * 1024 * 1024;
const MAX_TOTAL_SIZE = 50 * 1024 * 1024;
const MAX_TITLE_LENGTH = 200;
const MAX_DESCRIPTION_LENGTH = 2000;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;

type PostFormMode = 'create' | 'edit';

interface PostFormValues {
  title: string;
  description: string;
  category: string;
  imageUrls: string[];
}

interface PostFormPayload {
  title: string;
  description: string;
  category: string;
  imageUrls: string[];
}

interface PostFormPreviewAuthor {
  name: string;
  avatarUrl?: string;
  rating?: number;
  city?: string;
}

interface PostFormProps {
  mode: PostFormMode;
  initialValues: PostFormValues;
  previewAuthor?: PostFormPreviewAuthor;
  locationLabel?: string;
  status?: Post['status'];
  onSubmit: (payload: PostFormPayload) => Promise<void>;
  onCancel: () => void;
}

interface FieldErrors {
  title?: string;
  description?: string;
  category?: string;
}

interface StagedImage {
  id: string;
  file: File;
  previewUrl: string;
}

type LeaveTarget =
  | { type: 'cancel' }
  | { type: 'history' }
  | { type: 'href'; href: string };

interface ServerErrorBody {
  message?: string;
  errors?: Record<string, string>;
}

const getInitials = (name: string): string =>
  name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2) || 'Я';

const getFileKey = (file: File): string => `${file.name}:${file.size}:${file.lastModified}`;

const isGenericValidationMessage = (message?: string): boolean =>
  Boolean(message && /see 'errors' for details/i.test(message));

export const PostForm = ({
  mode,
  initialValues,
  previewAuthor,
  locationLabel,
  status = 'OPEN',
  onSubmit,
  onCancel,
}: PostFormProps) => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [title, setTitle] = useState(initialValues.title);
  const [description, setDescription] = useState(initialValues.description);
  const [category, setCategory] = useState(initialValues.category);
  const [existingImages, setExistingImages] = useState<string[]>(initialValues.imageUrls);
  const [removedImages, setRemovedImages] = useState<string[]>([]);
  const [newFiles, setNewFiles] = useState<StagedImage[]>([]);
  const [stagedError, setStagedError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [bannerError, setBannerError] = useState<{ title: string; message: string } | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [uploadPhase, setUploadPhase] = useState<'idle' | 'uploading' | 'saving'>('idle');
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [leaveTarget, setLeaveTarget] = useState<LeaveTarget | null>(null);
  const [previewImageFailed, setPreviewImageFailed] = useState(false);
  const [previewAvatarFailed, setPreviewAvatarFailed] = useState(false);

  const titleRef = useRef<HTMLInputElement>(null);
  const descriptionRef = useRef<HTMLTextAreaElement>(null);
  const categoryGroupRef = useRef<HTMLFieldSetElement>(null);
  const stayButtonRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const dragCounterRef = useRef(0);
  const newFilesRef = useRef<StagedImage[]>([]);
  const dirtyRef = useRef(false);
  const bypassRef = useRef(false);
  const submittingRef = useRef(false);
  const guardPushedRef = useRef(false);
  const leaveTargetRef = useRef<LeaveTarget | null>(null);
  const onCancelRef = useRef(onCancel);

  useEffect(() => {
    onCancelRef.current = onCancel;
  }, [onCancel]);

  useEffect(() => {
    newFilesRef.current = newFiles;
  }, [newFiles]);

  useEffect(
    () => () => {
      newFilesRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
    },
    []
  );

  const isDirty = useMemo(() => {
    if (mode === 'edit') {
      return (
        title !== initialValues.title ||
        description !== initialValues.description ||
        existingImages.join('\n') !== initialValues.imageUrls.join('\n') ||
        removedImages.length > 0 ||
        newFiles.length > 0
      );
    }

    return (
      title !== '' ||
      description !== '' ||
      category !== '' ||
      newFiles.length > 0
    );
  }, [mode, title, description, category, existingImages, removedImages, newFiles, initialValues]);

  const pushGuardEntry = useCallback(() => {
    try {
      const state = (window.history.state || {}) as Record<string, unknown>;
      const index = typeof state.idx === 'number' ? state.idx : 0;
      window.history.pushState({ ...state, idx: index + 1 }, '', window.location.href);
    } catch {
      // History API can be unavailable in some embedded contexts — the other guards still work.
    }
  }, []);

  useEffect(() => {
    dirtyRef.current = isDirty;
    if (isDirty && !guardPushedRef.current && !bypassRef.current) {
      guardPushedRef.current = true;
      pushGuardEntry();
    }
  }, [isDirty, pushGuardEntry]);

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current || bypassRef.current) return;
      event.preventDefault();
      event.returnValue = '';
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      if (!dirtyRef.current || bypassRef.current) return;
      // The browser already moved one entry back (to the copy we pushed while dirty).
      // Keep the form on screen and let the user decide whether to leave.
      leaveTargetRef.current = { type: 'history' };
      setLeaveTarget({ type: 'history' });
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const cancelLeave = useCallback(() => {
    const wasHistory = leaveTargetRef.current?.type === 'history';
    leaveTargetRef.current = null;
    setLeaveTarget(null);
    if (wasHistory) pushGuardEntry();
  }, [pushGuardEntry]);

  const confirmLeave = useCallback(() => {
    const target = leaveTargetRef.current;
    leaveTargetRef.current = null;
    setLeaveTarget(null);
    bypassRef.current = true;
    dirtyRef.current = false;

    if (target?.type === 'href') {
      navigate(target.href);
    } else if (target?.type === 'history') {
      window.history.go(-1);
    } else {
      onCancelRef.current();
    }
  }, [navigate]);

  const requestLeave = useCallback(
    (target: LeaveTarget) => {
      if (submittingRef.current) return;

      if (!dirtyRef.current) {
        bypassRef.current = true;
        if (target.type === 'href') {
          navigate(target.href);
        } else if (target.type === 'history') {
          window.history.go(-1);
        } else {
          onCancelRef.current();
        }
        return;
      }

      leaveTargetRef.current = target;
      setLeaveTarget(target);
    },
    [navigate]
  );

  useEffect(() => {
    const handleLinkClick = (event: MouseEvent) => {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
      ) {
        return;
      }

      const target = event.target as HTMLElement | null;
      const anchor = target?.closest('a[href]') as HTMLAnchorElement | null;
      if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return;

      const href = anchor.getAttribute('href') || '';
      if (!href || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('tel:')) {
        return;
      }

      let url: URL;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }

      if (url.origin !== window.location.origin) return;
      if (
        url.pathname === window.location.pathname &&
        url.search === window.location.search &&
        url.hash === window.location.hash
      ) {
        return;
      }

      if (submittingRef.current) {
        event.preventDefault();
        return;
      }

      if (dirtyRef.current && !bypassRef.current) {
        event.preventDefault();
        requestLeave({ type: 'href', href: `${url.pathname}${url.search}${url.hash}` });
      }
    };

    document.addEventListener('click', handleLinkClick, true);
    return () => document.removeEventListener('click', handleLinkClick, true);
  }, [requestLeave]);

  useEffect(() => {
    if (!leaveTarget) return;

    previousFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    stayButtonRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        cancelLeave();
        return;
      }

      if (event.key !== 'Tab') return;

      const dialog = dialogRef.current;
      if (!dialog) return;

      const focusable = Array.from(
        dialog.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input, textarea, select')
      ).filter((element) => element.offsetParent !== null || element === document.activeElement);

      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;

      if (event.shiftKey) {
        if (active === first || !dialog.contains(active)) {
          event.preventDefault();
          last.focus();
        }
      } else if (active === last || !dialog.contains(active)) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;

      const previousFocus = previousFocusRef.current;
      previousFocusRef.current = null;
      if (previousFocus && document.contains(previousFocus)) {
        previousFocus.focus();
      }
    };
  }, [leaveTarget, cancelLeave]);

  useEffect(() => {
    setPreviewImageFailed(false);
  }, [existingImages, newFiles]);

  const trimmedTitle = title.trim();
  const titleValid = trimmedTitle.length >= 3 && trimmedTitle.length <= MAX_TITLE_LENGTH;
  const imagesCount = existingImages.length + newFiles.length;
  const requiredDone = Number(titleValid) + Number(Boolean(category));
  const readiness = requiredDone * 50;
  const previewImage = existingImages[0] || newFiles[0]?.previewUrl;
  const authorName = previewAuthor?.name || user?.name || 'Вы';
  const authorAvatarUrl = previewAuthor?.avatarUrl || user?.avatarUrl;
  const authorRating = previewAuthor?.rating ?? user?.rating ?? 0;
  const previewCity = locationLabel || previewAuthor?.city || user?.city;
  const PreviewCategoryIcon = getCategoryIcon(category);
  const showPreviewImage = Boolean(previewImage) && !previewImageFailed;
  const showPreviewAvatar = Boolean(authorAvatarUrl) && !previewAvatarFailed;

  const focusFirstError = useCallback((errors: FieldErrors) => {
    window.setTimeout(() => {
      if (errors.title) {
        titleRef.current?.focus();
      } else if (errors.description) {
        descriptionRef.current?.focus();
      } else if (errors.category) {
        const firstRadio = categoryGroupRef.current?.querySelector<HTMLInputElement>(
          'input[type="radio"]'
        );
        firstRadio?.focus();
      }
    }, 0);
  }, []);

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {};

    if (!trimmedTitle) {
      errors.title = 'Введите заголовок заявки';
    } else if (trimmedTitle.length < 3) {
      errors.title = 'Заголовок должен содержать минимум 3 символа';
    } else if (trimmedTitle.length > MAX_TITLE_LENGTH) {
      errors.title = 'Заголовок не может быть длиннее 200 символов';
    }

    if (description.trim().length > MAX_DESCRIPTION_LENGTH) {
      errors.description = 'Описание не может быть длиннее 2000 символов';
    }

    if (!category) {
      errors.category = 'Выберите категорию — без неё заявку нельзя опубликовать';
    }

    return errors;
  };

  const applyServerError = (error: unknown) => {
    const response = (
      error as {
        response?: { status?: number; data?: ServerErrorBody } | undefined;
      }
    )?.response;
    const statusCode = response?.status;
    const data = response?.data;
    const serverFields = data?.errors;

    const nextFieldErrors: FieldErrors = {};
    if (serverFields?.title) nextFieldErrors.title = serverFields.title;
    if (serverFields?.description) nextFieldErrors.description = serverFields.description;
    if (serverFields?.category) nextFieldErrors.category = serverFields.category;

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors((previous) => ({ ...previous, ...nextFieldErrors }));
      focusFirstError(nextFieldErrors);
    }

    let title = 'Не удалось отправить заявку';
    let message: string;

    if (!response) {
      message = 'Нет соединения с сервером. Проверьте интернет и попробуйте ещё раз.';
    } else if (statusCode === 413) {
      message = 'Фотографии слишком большие для отправки. Уменьшите их размер или количество.';
    } else if (statusCode === 401) {
      message = 'Сессия истекла. Войдите снова, чтобы сохранить заявку.';
    } else if (statusCode === 403) {
      title = 'Недостаточно прав';
      message =
        data?.message || 'У вас нет прав на это действие. Возможно, аккаунт заблокирован.';
    } else if (statusCode === 404) {
      title = 'Заявка не найдена';
      message = 'Возможно, заявку удалили. Вернитесь в каталог и попробуйте снова.';
    } else if (statusCode && statusCode >= 500) {
      title = 'Ошибка сервера';
      message =
        data?.message && !isGenericValidationMessage(data.message)
          ? data.message
          : 'Сервер не смог обработать запрос. Попробуйте ещё раз через минуту.';
    } else if (Object.keys(nextFieldErrors).length > 0) {
      title = 'Проверьте форму';
      message = isGenericValidationMessage(data?.message)
        ? 'Некоторые поля заполнены неверно — мы подсветили их.'
        : data?.message || 'Некоторые поля заполнены неверно — мы подсветили их.';
    } else {
      message =
        data?.message && !isGenericValidationMessage(data.message)
          ? data.message
          : 'Проверьте соединение и попробуйте ещё раз.';
    }

    setBannerError({ title, message });
  };

  const addFiles = (fileList: FileList | File[]) => {
    if (submittingRef.current) return;

    const files = Array.from(fileList);
    if (files.length === 0) return;

    const errors: string[] = [];
    const accepted: StagedImage[] = [];
    const seen = new Set(newFiles.map((item) => getFileKey(item.file)));
    let totalSize = newFiles.reduce((sum, item) => sum + item.file.size, 0);

    files.forEach((file) => {
      const key = getFileKey(file);

      if (!ALLOWED_IMAGE_TYPES.includes(file.type as (typeof ALLOWED_IMAGE_TYPES)[number])) {
        errors.push(`«${file.name}»: поддерживаются только JPG, PNG, WebP и GIF.`);
        return;
      }

      if (file.size > MAX_FILE_SIZE) {
        errors.push(`«${file.name}»: размер файла больше 10 МБ.`);
        return;
      }

      if (seen.has(key)) {
        errors.push(`«${file.name}»: этот файл уже добавлен.`);
        return;
      }

      if (totalSize + file.size > MAX_TOTAL_SIZE) {
        errors.push(`«${file.name}»: превышен общий лимит 50 МБ на одну отправку.`);
        return;
      }

      seen.add(key);
      totalSize += file.size;
      accepted.push({
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        file,
        previewUrl: URL.createObjectURL(file),
      });
    });

    if (accepted.length > 0) {
      setNewFiles((previous) => [...previous, ...accepted]);
    }
    setStagedError(errors.length > 0 ? errors.join(' ') : null);
  };

  const removeStagedImage = (id: string) => {
    const target = newFiles.find((item) => item.id === id);
    if (target) URL.revokeObjectURL(target.previewUrl);
    setNewFiles((previous) => previous.filter((item) => item.id !== id));
  };

  const removeExistingImage = (url: string) => {
    setExistingImages((previous) => previous.filter((item) => item !== url));
    setRemovedImages((previous) => (previous.includes(url) ? previous : [...previous, url]));
  };

  const restoreExistingImage = (url: string) => {
    setRemovedImages((previous) => previous.filter((item) => item !== url));
    setExistingImages((previous) => (previous.includes(url) ? previous : [...previous, url]));
  };

  const handleDrop = (event: React.DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    dragCounterRef.current = 0;
    setIsDragging(false);
    if (event.dataTransfer.files?.length) {
      addFiles(event.dataTransfer.files);
    }
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submittingRef.current) return;

    setBannerError(null);
    setStagedError(null);

    const clientErrors = validate();
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      focusFirstError(clientErrors);
      setBannerError({
        title: 'Проверьте форму',
        message: 'Некоторые поля заполнены неверно — мы подсветили их.',
      });
      return;
    }

    setFieldErrors({});
    submittingRef.current = true;
    setIsSubmitting(true);

    let finalImageUrls = existingImages;

    try {
      if (newFiles.length > 0) {
        setUploadPhase('uploading');
        setUploadProgress(0);

        const uploadedUrls = await imagesApi.uploadMultiple(
          newFiles.map((item) => item.file),
          (progressEvent) => {
            if (progressEvent.total) {
              setUploadProgress(
                Math.min(100, Math.round((progressEvent.loaded / progressEvent.total) * 100))
              );
            }
          }
        );

        if (uploadedUrls.length !== newFiles.length) {
          throw new Error('image-upload-incomplete');
        }

        finalImageUrls = [...existingImages, ...uploadedUrls];
        setExistingImages(finalImageUrls);
        newFiles.forEach((item) => URL.revokeObjectURL(item.previewUrl));
        setNewFiles([]);
        setUploadProgress(100);
      }

      setUploadPhase('saving');
      bypassRef.current = true;
      dirtyRef.current = false;

      await onSubmit({
        title: trimmedTitle,
        description: description.trim(),
        category,
        imageUrls: finalImageUrls,
      });

      // The page navigates away after a successful backend response.
      return;
    } catch (error) {
      bypassRef.current = false;
      dirtyRef.current = true;

      if (error instanceof Error && error.message === 'image-upload-incomplete') {
        setBannerError({
          title: 'Не удалось загрузить фотографии',
          message: 'Сервер вернул неполный список файлов. Попробуйте отправить форму ещё раз.',
        });
      } else {
        applyServerError(error);
      }
    } finally {
      if (!bypassRef.current) {
        submittingRef.current = false;
        setIsSubmitting(false);
        setUploadPhase('idle');
        setUploadProgress(null);
      }
    }
  };

  const titleCounterClass =
    title.length >= MAX_TITLE_LENGTH - 10 ? ' post-form-counter--warning' : '';
  const descriptionCounterClass =
    description.length >= MAX_DESCRIPTION_LENGTH - 100 ? ' post-form-counter--warning' : '';
  const submitLabel = mode === 'create' ? 'Опубликовать заявку' : 'Сохранить изменения';
  const submitBusyLabel =
    uploadPhase === 'uploading'
      ? 'Загружаем фотографии…'
      : mode === 'create'
        ? 'Публикуем…'
        : 'Сохраняем…';

  return (
    <form
      className="post-form"
      onSubmit={handleSubmit}
      noValidate
      aria-busy={isSubmitting}
    >
      {bannerError && (
        <div className="post-form-banner" role="alert">
          <AlertTriangle size={18} aria-hidden="true" />
          <div>
            <strong>{bannerError.title}</strong>
            <span>{bannerError.message}</span>
          </div>
        </div>
      )}

      <div className="post-form-layout">
        <div className="post-form-main">
          <section className="post-form-card" aria-labelledby="post-section-main">
            <div className="post-form-section-head">
              <span className="post-form-step" aria-hidden="true">
                01
              </span>
              <div>
                <h2 id="post-section-main">Основная информация</h2>
                <p>Коротко расскажите, что произошло и какая помощь нужна.</p>
              </div>
            </div>

            <div className={`post-form-field${fieldErrors.title ? ' has-error' : ''}`}>
              <div className="post-form-label-row">
                <label className="post-form-label" htmlFor="post-title">
                  Заголовок заявки
                  <span className="post-form-required">обязательно</span>
                </label>
                <span className={`post-form-counter${titleCounterClass}`}>
                  {title.length} / {MAX_TITLE_LENGTH}
                </span>
              </div>
              <input
                id="post-title"
                ref={titleRef}
                className="post-form-control"
                type="text"
                value={title}
                maxLength={MAX_TITLE_LENGTH}
                placeholder="Например: Нужна помощь с переездом в субботу"
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.title)}
                aria-describedby={
                  fieldErrors.title ? 'post-title-error post-title-hint' : 'post-title-hint'
                }
                onChange={(event) => {
                  setTitle(event.target.value);
                  setBannerError(null);
                  if (fieldErrors.title) {
                    setFieldErrors((previous) => ({ ...previous, title: undefined }));
                  }
                }}
              />
              <p className="post-form-hint" id="post-title-hint">
                От 3 до 200 символов. Напишите так, чтобы было понятно с первого взгляда.
              </p>
              {fieldErrors.title && (
                <p className="post-form-error" id="post-title-error">
                  <AlertCircle size={14} aria-hidden="true" />
                  {fieldErrors.title}
                </p>
              )}
            </div>

            <div className={`post-form-field${fieldErrors.description ? ' has-error' : ''}`}>
              <div className="post-form-label-row">
                <label className="post-form-label" htmlFor="post-description">
                  Описание
                  <span className="post-form-optional">необязательно</span>
                </label>
                <span className={`post-form-counter${descriptionCounterClass}`}>
                  {description.length} / {MAX_DESCRIPTION_LENGTH}
                </span>
              </div>
              <textarea
                id="post-description"
                ref={descriptionRef}
                className="post-form-control post-form-textarea"
                value={description}
                maxLength={MAX_DESCRIPTION_LENGTH}
                placeholder="Опишите ситуацию подробнее: что нужно сделать, когда, сколько это займёт и что важно учесть."
                aria-invalid={Boolean(fieldErrors.description)}
                aria-describedby={
                  fieldErrors.description
                    ? 'post-description-error post-description-hint'
                    : 'post-description-hint'
                }
                onChange={(event) => {
                  setDescription(event.target.value);
                  setBannerError(null);
                  if (fieldErrors.description) {
                    setFieldErrors((previous) => ({ ...previous, description: undefined }));
                  }
                }}
              />
              <p className="post-form-hint" id="post-description-hint">
                Чем конкретнее описание, тем проще человеку рядом понять, чем он может помочь. Не
                указывайте точный адрес и личные данные.
              </p>
              {fieldErrors.description && (
                <p className="post-form-error" id="post-description-error">
                  <AlertCircle size={14} aria-hidden="true" />
                  {fieldErrors.description}
                </p>
              )}
            </div>
          </section>

          <section className="post-form-card" aria-labelledby="post-section-category">
            <div className="post-form-section-head">
              <span className="post-form-step" aria-hidden="true">
                02
              </span>
              <div>
                <h2 id="post-section-category">Категория</h2>
                <p>
                  {mode === 'create'
                    ? 'Выберите тему — так заявку быстрее заметят нужные люди.'
                    : 'Категория фиксируется при публикации и не меняется при редактировании.'}
                </p>
              </div>
            </div>

            {mode === 'edit' ? (
              <div className="post-form-locked">
                <span className="post-form-locked-icon" aria-hidden="true">
                  <PreviewCategoryIcon size={18} />
                </span>
                <div>
                  <strong>{initialValues.category || 'Категория не указана'}</strong>
                  <span>Изменить категорию после публикации нельзя.</span>
                </div>
                <Lock size={15} aria-hidden="true" className="post-form-locked-badge" />
              </div>
            ) : (
              <fieldset
                ref={categoryGroupRef}
                className={`post-form-fieldset${fieldErrors.category ? ' has-error' : ''}`}
                aria-describedby={fieldErrors.category ? 'post-category-error' : undefined}
              >
                <legend className="post-form-legend">
                  Выберите одну категорию
                  <span className="post-form-required">обязательно</span>
                </legend>
                <div className="post-form-categories">
                  {CATEGORIES.map((item) => {
                    const CategoryIcon = getCategoryIcon(item);
                    const id = `post-category-${item}`;
                    return (
                      <div className="post-form-category" key={item}>
                        <input
                          id={id}
                          type="radio"
                          name="post-category"
                          value={item}
                          checked={category === item}
                          onChange={() => {
                            setCategory(item);
                            setBannerError(null);
                            if (fieldErrors.category) {
                              setFieldErrors((previous) => ({
                                ...previous,
                                category: undefined,
                              }));
                            }
                          }}
                        />
                        <label htmlFor={id}>
                          <CategoryIcon size={15} aria-hidden="true" />
                          <span>{item}</span>
                        </label>
                      </div>
                    );
                  })}
                </div>
                {fieldErrors.category && (
                  <p className="post-form-error" id="post-category-error">
                    <AlertCircle size={14} aria-hidden="true" />
                    {fieldErrors.category}
                  </p>
                )}
              </fieldset>
            )}

            <div className="post-form-note">
              <MapPin size={15} aria-hidden="true" />
              <span>
                {previewCity
                  ? `Местоположение берётся из вашего профиля: ${previewCity}.`
                  : 'В профиле не указан город — заявка будет без местоположения.'}
              </span>
            </div>
          </section>

          <section className="post-form-card" aria-labelledby="post-section-images">
            <div className="post-form-section-head">
              <span className="post-form-step" aria-hidden="true">
                03
              </span>
              <div>
                <h2 id="post-section-images">Фотографии</h2>
                <p>Добавьте снимки, которые помогут понять ситуацию. Первое фото станет обложкой.</p>
              </div>
            </div>

            <label
              className={`post-form-upload${isDragging ? ' is-dragging' : ''}${
                isSubmitting ? ' is-disabled' : ''
              }`}
              onDragEnter={(event) => {
                event.preventDefault();
                if (isSubmitting) return;
                dragCounterRef.current += 1;
                setIsDragging(true);
              }}
              onDragLeave={(event) => {
                event.preventDefault();
                if (isSubmitting) return;
                dragCounterRef.current -= 1;
                if (dragCounterRef.current <= 0) {
                  dragCounterRef.current = 0;
                  setIsDragging(false);
                }
              }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={handleDrop}
            >
              <input
                className="post-form-file-input"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                multiple
                disabled={isSubmitting}
                onChange={(event) => {
                  if (event.target.files) addFiles(event.target.files);
                  event.target.value = '';
                }}
              />
              <span className="post-form-upload-icon" aria-hidden="true">
                <UploadCloud size={24} />
              </span>
              <strong>Перетащите фотографии сюда</strong>
              <span>или нажмите, чтобы выбрать файлы на устройстве</span>
              <span className="post-form-upload-rules">
                JPG, PNG, WebP или GIF · до 10 МБ каждое · суммарно до 50 МБ
              </span>
            </label>

            {uploadPhase === 'uploading' && (
              <div className="post-form-upload-progress" role="status" aria-live="polite">
                <div className="post-form-progress-label">
                  <span>Загружаем фотографии…</span>
                  <span>{uploadProgress ?? 0}%</span>
                </div>
                <div
                  className="post-form-progress-track"
                  role="progressbar"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={uploadProgress ?? 0}
                  aria-label="Загрузка фотографий"
                >
                  <div
                    className="post-form-progress-bar"
                    style={{ width: `${uploadProgress ?? 0}%` }}
                  />
                </div>
              </div>
            )}

            {stagedError && (
              <p className="post-form-error post-form-image-error" role="alert">
                <AlertCircle size={14} aria-hidden="true" />
                {stagedError}
              </p>
            )}

            {imagesCount > 0 && (
              <div className="post-form-subhead">
                <span>
                  В заявке {imagesCount}{' '}
                  {imagesCount === 1 ? 'фотография' : imagesCount < 5 ? 'фотографии' : 'фотографий'}
                </span>
                <span>Нажмите ×, чтобы убрать фото до отправки</span>
              </div>
            )}

            {existingImages.length > 0 && (
              <div className="post-form-image-grid">
                {existingImages.map((url, index) => (
                  <div className="post-form-image" key={url}>
                    <img
                      src={url}
                      alt={index === 0 ? 'Обложка заявки' : `Фотография ${index + 1}`}
                      loading="lazy"
                    />
                    <span className="post-form-image-badge">Сохранено</span>
                    <button
                      type="button"
                      className="post-form-image-remove"
                      aria-label={`Убрать фотографию ${index + 1} из заявки`}
                      title="Убрать из заявки"
                      onClick={() => removeExistingImage(url)}
                    >
                      <X size={15} aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            {newFiles.length > 0 && (
              <div className="post-form-image-grid">
                {newFiles.map((item, index) => (
                  <div className="post-form-image" key={item.id}>
                    <img src={item.previewUrl} alt={`Новая фотография ${index + 1}`} />
                    <span className="post-form-image-badge post-form-image-badge--new">Новое</span>
                    <button
                      type="button"
                      className="post-form-image-remove"
                      aria-label={`Удалить новую фотографию ${index + 1}`}
                      title="Удалить фотографию"
                      onClick={() => removeStagedImage(item.id)}
                    >
                      <X size={15} aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            {removedImages.length > 0 && (
              <>
                <div className="post-form-subhead post-form-subhead--removed">
                  <span>
                    Будет откреплено от заявки: {removedImages.length}
                  </span>
                  <span>Сами файлы останутся в хранилище</span>
                </div>
                <div className="post-form-image-grid">
                  {removedImages.map((url, index) => (
                    <div className="post-form-image post-form-image--removed" key={url}>
                      <img src={url} alt={`Фотография к удалению ${index + 1}`} loading="lazy" />
                      <span className="post-form-image-badge post-form-image-badge--removed">
                        Будет удалено
                      </span>
                      <button
                        type="button"
                        className="post-form-image-restore"
                        aria-label={`Вернуть фотографию ${index + 1} в заявку`}
                        onClick={() => restoreExistingImage(url)}
                      >
                        <RotateCcw size={16} aria-hidden="true" />
                        Вернуть
                      </button>
                    </div>
                  ))}
                </div>
              </>
            )}

            <p className="post-form-hint post-form-image-hint">
              <ImagePlus size={14} aria-hidden="true" />
              Если фотографий нет — ничего страшного, заявку можно опубликовать и без них.
            </p>
          </section>

          <div className="post-form-actions">
            <span className="post-form-actions-note">
              {mode === 'create'
                ? 'Заявка появится в каталоге только после публикации.'
                : 'Изменения появятся после сохранения.'}
            </span>
            <button
              type="button"
              className="post-form-btn post-form-btn--ghost"
              onClick={() => requestLeave({ type: 'cancel' })}
              disabled={isSubmitting}
            >
              Отмена
            </button>
            <button
              type="submit"
              className="post-form-btn post-form-btn--primary"
              disabled={isSubmitting}
              aria-busy={isSubmitting}
            >
              {isSubmitting ? (
                <Loader2 size={17} className="post-form-spin" aria-hidden="true" />
              ) : mode === 'create' ? (
                <Send size={17} aria-hidden="true" />
              ) : (
                <Save size={17} aria-hidden="true" />
              )}
              {isSubmitting ? submitBusyLabel : submitLabel}
            </button>
          </div>
        </div>

        <aside className="post-form-aside" aria-label="Подсказки и предпросмотр">
          <div className="post-form-side-card post-form-readiness">
            <h2 className="post-form-side-title">
              <Leaf size={14} aria-hidden="true" />
              Готовность заявки
            </h2>
            <div
              className="post-form-progress-track post-form-readiness-track"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={readiness}
              aria-label="Заполнение обязательных полей"
            >
              <div className="post-form-progress-bar" style={{ width: `${readiness}%` }} />
            </div>
            <p className="post-form-readiness-caption">
              {requiredDone === 2
                ? 'Обязательные поля заполнены — можно публиковать.'
                : `Заполнено ${requiredDone} из 2 обязательных полей.`}
            </p>
            <ul className="post-form-checklist">
              <li className={titleValid ? 'is-done' : ''}>
                <span className="post-form-check-icon" aria-hidden="true">
                  {titleValid ? <Check size={12} /> : null}
                </span>
                Заголовок
              </li>
              <li className={category ? 'is-done' : ''}>
                <span className="post-form-check-icon" aria-hidden="true">
                  {category ? <Check size={12} /> : null}
                </span>
                Категория
              </li>
              <li className={description.trim() ? 'is-done' : ''}>
                <span className="post-form-check-icon" aria-hidden="true">
                  {description.trim() ? <Check size={12} /> : null}
                </span>
                Описание
                <span className="post-form-check-optional">необязательно</span>
              </li>
              <li className={imagesCount > 0 ? 'is-done' : ''}>
                <span className="post-form-check-icon" aria-hidden="true">
                  {imagesCount > 0 ? <Check size={12} /> : null}
                </span>
                Фотографии
                <span className="post-form-check-optional">необязательно</span>
              </li>
            </ul>
          </div>

          <div className="post-form-side-card post-form-preview-card">
            <div className="post-form-preview-head">
              <Eye size={14} aria-hidden="true" />
              <span>Предпросмотр в каталоге</span>
            </div>
            <div
              className="post-form-preview"
              role="region"
              aria-label="Предпросмотр карточки заявки"
            >
              <div
                className={`post-form-preview-media${
                  showPreviewImage ? '' : ' post-form-preview-media--placeholder'
                }`}
              >
                {showPreviewImage ? (
                  <img
                    src={previewImage}
                    alt=""
                    onError={() => setPreviewImageFailed(true)}
                  />
                ) : (
                  <div className="post-form-preview-placeholder" aria-hidden="true">
                    <PreviewCategoryIcon size={34} />
                    <span>{category || 'Ваша категория'}</span>
                  </div>
                )}
                <span className={`post-form-preview-status post-form-preview-status--${status.toLowerCase()}`}>
                  {getStatusLabel(status)}
                </span>
                {imagesCount > 1 && (
                  <span className="post-form-preview-count">+{imagesCount - 1}</span>
                )}
              </div>
              <div className="post-form-preview-body">
                <span className="post-form-preview-category">
                  <PreviewCategoryIcon size={11} aria-hidden="true" />
                  {category || 'Категория'}
                </span>
                <h3 className={trimmedTitle ? '' : 'is-placeholder'}>
                  {trimmedTitle || 'Заголовок вашей заявки'}
                </h3>
                <p className={description.trim() ? '' : 'is-placeholder'}>
                  {description.trim() ||
                    'Здесь появится описание — расскажите, какая помощь нужна.'}
                </p>
                <div className="post-form-preview-footer">
                  <div className="post-form-preview-author">
                    <span className="post-form-preview-avatar">
                      {showPreviewAvatar ? (
                        <img
                          src={authorAvatarUrl}
                          alt=""
                          onError={() => setPreviewAvatarFailed(true)}
                        />
                      ) : (
                        <span aria-hidden="true">{getInitials(authorName)}</span>
                      )}
                    </span>
                    <span className="post-form-preview-name">{authorName}</span>
                    {authorRating > 0 && (
                      <span className="post-form-preview-rating">
                        <Star size={10} fill="currentColor" aria-hidden="true" />
                        {authorRating.toFixed(1)}
                      </span>
                    )}
                  </div>
                  <div className="post-form-preview-meta">
                    {previewCity && (
                      <span>
                        <MapPin size={10} aria-hidden="true" />
                        {previewCity}
                      </span>
                    )}
                    <span>Только что</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="post-form-side-card post-form-tips">
            <h2 className="post-form-side-title">
              <Lightbulb size={14} aria-hidden="true" />
              Чтобы откликнулись быстрее
            </h2>
            <ul>
              <li>
                <Check size={13} aria-hidden="true" />
                Опишите конкретно: что случилось, когда нужна помощь и сколько это займёт.
              </li>
              <li>
                <Check size={13} aria-hidden="true" />
                Укажите важные детали: этаж, вес, инструменты, ограничения по времени.
              </li>
              <li>
                <Check size={13} aria-hidden="true" />
                Не публикуйте точный адрес, телефоны и другие личные данные в открытом описании.
              </li>
            </ul>
          </div>
        </aside>
      </div>

      {leaveTarget && (
        <div
          className="post-form-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) cancelLeave();
          }}
        >
          <div
            ref={dialogRef}
            className="post-form-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="post-leave-title"
            aria-describedby="post-leave-desc"
          >
            <span className="post-form-dialog-icon" aria-hidden="true">
              <AlertTriangle size={22} />
            </span>
            <h2 id="post-leave-title">Покинуть страницу?</h2>
            <p id="post-leave-desc">
              В заявке есть несохранённые изменения. Если вы уйдёте сейчас, введённые данные
              будут потеряны.
            </p>
            <div className="post-form-dialog-actions">
              <button
                ref={stayButtonRef}
                type="button"
                className="post-form-btn post-form-btn--ghost"
                onClick={cancelLeave}
              >
                Остаться
              </button>
              <button
                type="button"
                className="post-form-btn post-form-btn--danger"
                onClick={confirmLeave}
              >
                Выйти без сохранения
              </button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
};
