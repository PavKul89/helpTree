import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { LoaderCircle } from 'lucide-react';
import { authApi } from '../api/authApi';
import { AuthField } from '../components/AuthField';
import { AuthLayout } from '../components/AuthLayout';
import { useAuth } from '../context/AuthContext';
import { getApiErrorInfo } from '../utils/apiErrors';

type RegisterField = 'name' | 'email' | 'password' | 'phone' | 'city';
type FieldErrors = Partial<Record<RegisterField, string>>;

const validateEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

const validatePassword = (value: string) => {
  if (value.length < 8) return 'Пароль должен содержать не менее 8 символов';
  if (value.length > 100) return 'Пароль должен содержать не более 100 символов';
  if (!/[A-Z]/.test(value)) return 'Добавьте хотя бы одну заглавную латинскую букву';
  if (!/[a-z]/.test(value)) return 'Добавьте хотя бы одну строчную латинскую букву';
  if (!/\d/.test(value)) return 'Добавьте хотя бы одну цифру';
  return '';
};

const validateForm = (values: Record<RegisterField, string>): FieldErrors => {
  const errors: FieldErrors = {};
  const name = values.name.trim();
  const email = values.email.trim();
  const phone = values.phone.trim();

  if (!name) errors.name = 'Введите имя';
  else if (name.length < 2 || name.length > 100) errors.name = 'Имя должно содержать от 2 до 100 символов';

  if (!email) errors.email = 'Введите email';
  else if (!validateEmail(email)) errors.email = 'Проверьте формат email';

  if (!values.password) errors.password = 'Введите пароль';
  else errors.password = validatePassword(values.password) || undefined;

  if (!phone) errors.phone = 'Введите телефон';
  else if (phone.length < 10 || phone.length > 20) errors.phone = 'Телефон должен содержать от 10 до 20 символов';

  if (!values.city.trim()) errors.city = 'Введите город';
  return errors;
};

export const RegisterPage = () => {
  const [values, setValues] = useState<Record<RegisterField, string>>({
    name: '', email: '', password: '', phone: '', city: '',
  });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const updateField = (field: RegisterField, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setError('');
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSubmitting) return;
    setError('');
    const errors = validateForm(values);
    setFieldErrors(errors);
    if (Object.values(errors).some(Boolean)) return;

    setIsSubmitting(true);
    try {
      const response = await authApi.register({
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
        phone: values.phone.trim(),
        city: values.city.trim(),
      });

      login(response.accessToken, {
        id: response.userId,
        email: response.email,
        name: values.name.trim(),
        rating: 0,
        role: response.role,
        createdAt: new Date().toISOString(),
      });
      setValues((current) => ({ ...current, password: '' }));
      navigate('/');
    } catch (requestError: unknown) {
      if (axios.isAxiosError(requestError)) {
        const info = getApiErrorInfo(requestError);
        const serverErrors: FieldErrors = {};
        (Object.keys(info.fields) as RegisterField[]).forEach((field) => {
          if (field === 'name' || field === 'email' || field === 'password'
              || field === 'phone' || field === 'city') {
            serverErrors[field] = info.fields[field];
          }
        });

        if (Object.keys(serverErrors).length > 0) {
          setFieldErrors((current) => ({ ...current, ...serverErrors }));
        } else if (info.status === 409) {
          setError('Аккаунт с таким email уже существует. Войдите или укажите другой email.');
        } else if (!info.status) {
          setError('Не удалось связаться с сервером. Проверьте подключение и попробуйте ещё раз.');
        } else {
          setError(
            info.traceId
              ? `Не удалось создать аккаунт. Попробуйте ещё раз. Код обращения: ${info.traceId}`
              : 'Не удалось создать аккаунт. Проверьте данные и попробуйте ещё раз.'
          );
        }
      } else {
        setError('Не удалось создать аккаунт. Попробуйте ещё раз.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout>
      <header className="auth-heading">
        <h2>Создать аккаунт</h2>
        <p>Присоединяйтесь к сообществу добрых дел</p>
      </header>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <AuthField
          id="register-name" label="Ваше имя" value={values.name} placeholder="Например, Анна Иванова"
          autoComplete="name" maxLength={100} error={fieldErrors.name}
          onChange={(value) => updateField('name', value)}
        />
        <AuthField
          id="register-email" label="Email" type="email" value={values.email} placeholder="name@example.com"
          autoComplete="email" maxLength={254} error={fieldErrors.email}
          onChange={(value) => updateField('email', value)}
        />
        <AuthField
          id="register-password" label="Пароль" type="password" value={values.password} placeholder="Не менее 8 символов"
          autoComplete="new-password" maxLength={100} error={fieldErrors.password}
          onChange={(value) => updateField('password', value)}
        />
        <AuthField
          id="register-phone" label="Телефон" type="tel" inputMode="tel" value={values.phone}
          placeholder="+7 (999) 123-45-67" autoComplete="tel" maxLength={20} error={fieldErrors.phone}
          onChange={(value) => updateField('phone', value)}
        />
        <AuthField
          id="register-city" label="Город" value={values.city} placeholder="Например, Казань"
          autoComplete="address-level2" error={fieldErrors.city}
          onChange={(value) => updateField('city', value)}
        />

        {error && <p className="auth-form-error" role="alert">{error}</p>}

        <button className="auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="auth-spinner" size={18} aria-hidden="true" />}
          {isSubmitting ? 'Создаём аккаунт…' : 'Создать аккаунт'}
        </button>
      </form>

      <p className="auth-switch">
        Уже есть аккаунт? <Link className="auth-link" to="/login">Войти</Link>
      </p>
    </AuthLayout>
  );
};
