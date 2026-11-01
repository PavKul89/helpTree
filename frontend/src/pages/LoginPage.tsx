import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { LoaderCircle } from 'lucide-react';
import { authApi } from '../api/authApi';
import { AuthField } from '../components/AuthField';
import { AuthLayout } from '../components/AuthLayout';
import { useAuth } from '../context/AuthContext';

type LoginField = 'email' | 'password';
type FieldErrors = Partial<Record<LoginField, string>>;

const validateEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

export const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSubmitting) return;
    setError('');

    const errors: FieldErrors = {};
    if (!email.trim()) errors.email = 'Введите email';
    else if (!validateEmail(email.trim())) errors.email = 'Проверьте формат email';
    if (!password) errors.password = 'Введите пароль';
    else if (password.length < 6) errors.password = 'Пароль должен содержать не менее 6 символов';
    setFieldErrors(errors);
    if (Object.values(errors).some(Boolean)) return;

    setIsSubmitting(true);
    try {
      const response = await authApi.login({ email: email.trim(), password });
      login(response.accessToken, {
        id: response.userId,
        email: response.email,
        name: response.email.split('@')[0],
        rating: 0,
        role: response.role,
        createdAt: new Date().toISOString(),
      });
      setPassword('');
      navigate('/');
    } catch (requestError: unknown) {
      if (axios.isAxiosError(requestError)) {
        const status = requestError.response?.status;
        if (status === 401 || status === 404) {
          setError('Проверьте правильность введённых данных.');
        } else if (status === 400 || status === 429) {
          setError('Слишком много попыток. Попробуйте войти немного позже.');
        } else if (!requestError.response) {
          setError('Не удалось связаться с сервером. Проверьте подключение и попробуйте ещё раз.');
        } else {
          setError('Не удалось выполнить вход. Попробуйте ещё раз.');
        }
      } else {
        setError('Не удалось выполнить вход. Попробуйте ещё раз.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout>
      <header className="auth-heading">
        <h2>С возвращением!</h2>
        <p>Рады видеть вас снова в HelpTree</p>
      </header>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <AuthField
          id="login-email" label="Email" type="email" value={email} placeholder="name@example.com"
          autoComplete="email" maxLength={254} error={fieldErrors.email}
          onChange={(value) => {
            setEmail(value);
            setFieldErrors((current) => ({ ...current, email: undefined }));
            setError('');
          }}
        />
        <AuthField
          id="login-password" label="Пароль" type="password" value={password} placeholder="Введите пароль"
          autoComplete="current-password" maxLength={100} error={fieldErrors.password}
          onChange={(value) => {
            setPassword(value);
            setFieldErrors((current) => ({ ...current, password: undefined }));
            setError('');
          }}
        />

        {error && <p className="auth-form-error" role="alert">{error}</p>}

        <button className="auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="auth-spinner" size={18} aria-hidden="true" />}
          {isSubmitting ? 'Входим…' : 'Войти в аккаунт'}
        </button>
      </form>

      <p className="auth-switch">
        Нет аккаунта? <Link className="auth-link" to="/register">Создать аккаунт</Link>
      </p>
    </AuthLayout>
  );
};
