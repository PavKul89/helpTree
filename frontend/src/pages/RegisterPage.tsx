import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';

export const RegisterPage = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [focused, setFocused] = useState<string | null>(null);
  const [buttonHover, setButtonHover] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{name?: string; email?: string; password?: string}>({});

  // 🔧 МАКСИМАЛЬНО ПРОСТАЯ ВАЛИДАЦИЯ EMAIL
  const isValidEmail = (email: string): boolean => {
    const trimmed = email.trim();
    if (!trimmed) return false;
    const hasAt = trimmed.includes('@');
    const hasDot = trimmed.includes('.');
    const result = hasAt && hasDot;

    // Отладка в консоль
    console.log('Email validation:', { email: trimmed, hasAt, hasDot, result });

    return result;
  };

  const handleBlur = (field: string, value: string) => {
    const errors: {name?: string; email?: string; password?: string} = {};

    if (field === 'name') {
      if (!value.trim()) {
        errors.name = 'Введите имя';
      } else if (value.trim().length < 2) {
        errors.name = 'Имя должно содержать не менее 2 символов';
      }
    }

    if (field === 'email') {
      const cleaned = value.trim();
      if (!cleaned) {
        errors.email = 'Введите email';
      } else if (!isValidEmail(cleaned)) {
        errors.email = 'Введите корректный email (например, name@example.com)';
      }
    }

    if (field === 'password') {
      if (!value) {
        errors.password = 'Введите пароль';
      } else if (value.length < 6) {
        errors.password = 'Пароль должен содержать не менее 6 символов';
      }
    }

    setFieldErrors(prev => ({...prev, ...errors}));
  };

  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const errors: {name?: string; email?: string; password?: string} = {};

    if (!name.trim()) {
      errors.name = 'Введите имя';
    } else if (name.trim().length < 2) {
      errors.name = 'Имя должно содержать не менее 2 символов';
    }

    if (!email.trim()) {
      errors.email = 'Введите email';
    } else if (!isValidEmail(email)) {
      errors.email = 'Введите корректный email (например, name@example.com)';
    }

    if (!password) {
      errors.password = 'Введите пароль';
    } else if (password.length < 6) {
      errors.password = 'Пароль должен содержать не менее 6 символов';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    try {
      const response = await authApi.register({ name, email, password });
      localStorage.setItem('accessToken', response.accessToken);
      login(response.accessToken, {
        id: response.userId,
        email: response.email,
        name: name,
        rating: 0,
        role: response.role,
        createdAt: new Date().toISOString()
      });
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка регистрации');
    }
  };

  return (
      <div style={styles.container}>
        <div style={styles.card}>
          <div style={styles.leftPanel}>
            <h1 style={styles.leftTitle}>Давайте начнём</h1>
            <p style={styles.leftText}>
              Помогать — легко. Станьте частью сообщества добрых дел.
            </p>
            <p style={styles.leftSubtext}>
              Зарегистрируйтесь и начните помогать прямо сейчас.<br/>
              Ваша помощь нужна тем, кто рядом.
            </p>
          </div>

          <div style={styles.rightPanel}>
            <h2 style={styles.formTitle}>Регистрация</h2>

            <form onSubmit={handleSubmit} style={styles.form}>
              <div style={styles.formGroup}>
                <label style={styles.label}>Ваше имя</label>
                <input
                    type="text"
                    autoComplete="name"
                    value={name}
                    onChange={(e) => { setName(e.target.value); setFieldErrors(prev => ({...prev, name: ''})); }}
                    onFocus={() => setFocused('name')}
                    onBlur={() => handleBlur('name', name)}
                    placeholder="Иван Петров"
                    style={{
                      ...styles.input,
                      borderColor: fieldErrors.name ? '#EF4444' : (focused === 'name' ? '#6EE7B7' : 'rgba(255,255,255,0.2)'),
                      boxShadow: fieldErrors.name ? '0 0 0 3px rgba(239, 68, 68, 0.25)' : (focused === 'name' ? '0 0 0 3px rgba(110, 231, 183, 0.2)' : 'none'),
                    }}
                />
                {fieldErrors.name && <div style={styles.fieldError}>{fieldErrors.name}</div>}
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Email</label>
                <input
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
                      setFieldErrors(prev => ({...prev, email: ''}));
                    }}
                    onFocus={() => setFocused('email')}
                    onBlur={() => handleBlur('email', email)}
                    placeholder="ivan@example.com"
                    style={{
                      ...styles.input,
                      borderColor: fieldErrors.email ? '#EF4444' : (focused === 'email' ? '#6EE7B7' : 'rgba(255,255,255,0.2)'),
                      boxShadow: fieldErrors.email ? '0 0 0 3px rgba(239, 68, 68, 0.25)' : (focused === 'email' ? '0 0 0 3px rgba(110, 231, 183, 0.2)' : 'none'),
                    }}
                />
                {fieldErrors.email && <div style={styles.fieldError}>{fieldErrors.email}</div>}
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Пароль</label>
                <input
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => { setPassword(e.target.value); setFieldErrors(prev => ({...prev, password: ''})); }}
                    onFocus={() => setFocused('password')}
                    onBlur={() => handleBlur('password', password)}
                    placeholder="••••••••"
                    style={{
                      ...styles.input,
                      borderColor: fieldErrors.password ? '#EF4444' : (focused === 'password' ? '#6EE7B7' : 'rgba(255,255,255,0.2)'),
                      boxShadow: fieldErrors.password ? '0 0 0 3px rgba(239, 68, 68, 0.25)' : (focused === 'password' ? '0 0 0 3px rgba(110, 231, 183, 0.2)' : 'none'),
                    }}
                />
                {fieldErrors.password && <div style={styles.fieldError}>{fieldErrors.password}</div>}
              </div>

              {error && (
                  <div style={styles.error}>
                    <span style={styles.errorIcon}>⚠</span>
                    <span>{error}</span>
                  </div>
              )}

              <button
                  type="submit"
                  style={buttonHover ? {...styles.button, ...styles.buttonHover} : styles.button}
                  onMouseEnter={() => setButtonHover(true)}
                  onMouseLeave={() => setButtonHover(false)}
              >
                Зарегистрироваться
              </button>
            </form>

            <p style={styles.loginLink}>
              Уже есть аккаунт? <Link to="/login" style={styles.link}>Войти</Link>
            </p>
          </div>
        </div>
      </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '40px 24px',
    background: 'linear-gradient(135deg, #022c22 0%, #065F46 100%)',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
  card: {
    width: '75%',
    maxWidth: '1000px',
    display: 'flex',
    gap: '24px',
  },
  leftPanel: {
    flex: '0 0 40%',
    background: 'linear-gradient(160deg, #047857 0%, #065F46 50%, #064E3B 100%)',
    padding: '56px 44px',
    borderRadius: '20px',
    boxShadow: '0 20px 40px -8px rgba(6, 78, 59, 0.35), 0 8px 16px -4px rgba(6, 78, 59, 0.2)',
    display: 'flex',
    flexDirection: 'column' as const,
    justifyContent: 'center',
  },
  leftTitle: {
    fontSize: '34px',
    fontWeight: 700,
    color: '#fff',
    margin: '0 0 20px 0',
    letterSpacing: '-0.5px',
  },
  leftText: {
    fontSize: '17px',
    color: 'rgba(255,255,255,0.9)',
    margin: '0 0 28px 0',
    lineHeight: 1.6,
  },
  leftSubtext: {
    fontSize: '14px',
    color: 'rgba(255,255,255,0.7)',
    lineHeight: 1.6,
    margin: 0,
  },
  rightPanel: {
    flex: '0 0 60%',
    padding: '52px 44px',
    background: 'linear-gradient(160deg, #047857 0%, #065F46 50%, #064E3B 100%)',
    borderRadius: '20px',
    boxShadow: '0 20px 40px -8px rgba(6, 78, 59, 0.35), 0 8px 16px -4px rgba(6, 78, 59, 0.2)',
  },
  formTitle: {
    fontSize: '26px',
    fontWeight: 700,
    color: '#fff',
    margin: '0 0 28px 0',
  },
  form: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '20px',
  },
  formGroup: {
    marginBottom: '4px',
  },
  label: {
    display: 'block',
    fontSize: '14px',
    fontWeight: 600,
    color: 'rgba(255,255,255,0.85)',
    marginBottom: '8px',
  },
  input: {
    width: '100%',
    padding: '14px 18px',
    fontSize: '15px',
    backgroundColor: 'rgba(255,255,255,0.1)',
    border: '1.5px solid rgba(255,255,255,0.2)',
    borderRadius: '12px',
    outline: 'none',
    transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
    boxSizing: 'border-box' as const,
    color: '#fff',
  },
  error: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.15) 0%, rgba(220, 38, 38, 0.1) 100%)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: '12px',
    padding: '14px 18px',
    color: '#FCA5A5',
    fontSize: '14px',
    boxShadow: '0 4px 12px rgba(239, 68, 68, 0.15)',
  },
  errorIcon: {
    fontSize: '18px',
  },
  fieldError: {
    color: '#FCA5A5',
    fontSize: '13px',
    marginTop: '6px',
    marginLeft: '2px',
    fontWeight: 500,
  },
  button: {
    width: '100%',
    padding: '16px',
    background: 'linear-gradient(135deg, #10B981 0%, #059669 100%)',
    border: 'none',
    borderRadius: '12px',
    color: '#fff',
    fontSize: '16px',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    marginTop: '8px',
    boxShadow: '0 4px 14px -3px rgba(16, 185, 129, 0.5), 0 0 20px rgba(16, 185, 129, 0.2)',
    transform: 'perspective(1000px) translateZ(0)',
  },
  buttonHover: {
    background: 'linear-gradient(135deg, #34D399 0%, #10B981 100%)',
    boxShadow: '0 6px 20px -3px rgba(52, 211, 153, 0.6), 0 0 30px rgba(52, 211, 153, 0.3)',
    transform: 'perspective(1000px) translateZ(5px)',
  },
  loginLink: {
    textAlign: 'center' as const,
    color: 'rgba(255,255,255,0.7)',
    fontSize: '14px',
    marginTop: '24px',
  },
  link: {
    color: '#6EE7B7',
    fontWeight: 600,
    textDecoration: 'none',
    transition: 'color 0.2s',
  },
};