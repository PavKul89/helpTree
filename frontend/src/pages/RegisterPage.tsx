import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import { theme } from '../theme';

export const RegisterPage = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [city, setCity] = useState('');
  const [error, setError] = useState('');
  const [focused, setFocused] = useState<string | null>(null);
  const [buttonHover, setButtonHover] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{name?: string; email?: string; password?: string; phone?: string; city?: string}>({});

  // Валидация email
  const validateEmail = (email: string) => {
    const re = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return re.test(email);
  };

  const handleBlur = (field: string, value: string) => {
    const errors: {name?: string; email?: string; password?: string; phone?: string; city?: string} = {};

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
      } else if (!validateEmail(cleaned)) {
        errors.email = 'Некорректный email';
      }
    }

    if (field === 'password') {
      if (!value) {
        errors.password = 'Введите пароль';
      } else if (value.length < 8) {
        errors.password = 'Пароль должен содержать не менее 8 символов';
      } else if (!/[A-Z]/.test(value)) {
        errors.password = 'Пароль должен содержать хотя бы одну заглавную букву';
      } else if (!/[a-z]/.test(value)) {
        errors.password = 'Пароль должен содержать хотя бы одну строчную букву';
      } else if (!/\d/.test(value)) {
        errors.password = 'Пароль должен содержать хотя бы одну цифру';
      }
    }

    if (field === 'phone') {
      if (!value.trim()) {
        errors.phone = 'Введите телефон';
      }
    }

    if (field === 'city') {
      if (!value.trim()) {
        errors.city = 'Введите город';
      }
    }

    setFieldErrors(prev => ({...prev, ...errors}));
  };

  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const errors: {name?: string; email?: string; password?: string; phone?: string; city?: string} = {};

    if (!name.trim()) {
      errors.name = 'Введите имя';
    } else if (name.trim().length < 2) {
      errors.name = 'Имя должно содержать не менее 2 символов';
    }

    if (!email.trim()) {
      errors.email = 'Введите email';
    } else if (!validateEmail(email)) {
      errors.email = 'Некорректный email';
    }

    if (!password) {
      errors.password = 'Введите пароль';
    } else if (password.length < 8) {
      errors.password = 'Пароль должен содержать не менее 8 символов';
    } else if (!/[A-Z]/.test(password)) {
      errors.password = 'Пароль должен содержать хотя бы одну заглавную букву';
    } else if (!/[a-z]/.test(password)) {
      errors.password = 'Пароль должен содержать хотя бы одну строчную букву';
    } else if (!/\d/.test(password)) {
      errors.password = 'Пароль должен содержать хотя бы одну цифру';
    }

    if (!phone.trim()) {
      errors.phone = 'Введите телефон';
    }

    if (!city.trim()) {
      errors.city = 'Введите город';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    try {
      const response = await authApi.register({ name, email, password, phone, city });
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

              <div style={styles.formGroup}>
                <label style={styles.label}>Телефон</label>
                <input
                    type="text"
                    inputMode="tel"
                    autoComplete="tel"
                    value={phone}
                    onChange={(e) => { setPhone(e.target.value); setFieldErrors(prev => ({...prev, phone: ''})); }}
                    onFocus={() => setFocused('phone')}
                    onBlur={() => handleBlur('phone', phone)}
                    placeholder="+7 (999) 123-45-67"
                    style={{
                      ...styles.input,
                      borderColor: fieldErrors.phone ? '#EF4444' : (focused === 'phone' ? '#6EE7B7' : 'rgba(255,255,255,0.2)'),
                      boxShadow: fieldErrors.phone ? '0 0 0 3px rgba(239, 68, 68, 0.25)' : (focused === 'phone' ? '0 0 0 3px rgba(110, 231, 183, 0.2)' : 'none'),
                    }}
                />
                {fieldErrors.phone && <div style={styles.fieldError}>{fieldErrors.phone}</div>}
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Город</label>
                <input
                    type="text"
                    value={city}
                    onChange={(e) => { setCity(e.target.value); setFieldErrors(prev => ({...prev, city: ''})); }}
                    onFocus={() => setFocused('city')}
                    onBlur={() => handleBlur('city', city)}
                    placeholder="Москва"
                    style={{
                      ...styles.input,
                      borderColor: fieldErrors.city ? '#EF4444' : (focused === 'city' ? '#6EE7B7' : 'rgba(255,255,255,0.2)'),
                      boxShadow: fieldErrors.city ? '0 0 0 3px rgba(239, 68, 68, 0.25)' : (focused === 'city' ? '0 0 0 3px rgba(110, 231, 183, 0.2)' : 'none'),
                    }}
                />
                {fieldErrors.city && <div style={styles.fieldError}>{fieldErrors.city}</div>}
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
    background: 'linear-gradient(135deg, #0c4a6e 0%, #022c22 50%, #065F46 100%)',
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
    background: 'linear-gradient(160deg, #0e7490 0%, #065F46 50%, #0c4a6e 100%)',
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
    textShadow: '0 2px 10px rgba(0,0,0,0.2)',
  },
  leftText: {
    fontSize: '17px',
    color: 'rgba(255,255,255,0.95)',
    margin: '0 0 28px 0',
    lineHeight: 1.6,
    fontWeight: 500,
  },
  leftSubtext: {
    fontSize: '14px',
    color: 'rgba(255,255,255,0.8)',
    lineHeight: 1.6,
    margin: 0,
  },
  rightPanel: {
    flex: '0 0 60%',
    padding: '52px 44px',
    background: 'linear-gradient(160deg, #0e7490 0%, #0891b2 50%, #164e63 100%)',
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
    background: 'linear-gradient(135deg, #22d3ee 0%, #06b6d4 100%)',
    border: 'none',
    borderRadius: '12px',
    color: '#022c22',
    fontSize: '16px',
    fontWeight: 700,
    cursor: 'pointer',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    marginTop: '8px',
    boxShadow: '0 4px 14px -3px rgba(34, 211, 238, 0.5), 0 0 20px rgba(34, 211, 238, 0.2)',
    transform: 'perspective(1000px) translateZ(0)',
  },
  buttonHover: {
    background: 'linear-gradient(135deg, #67e8f9 0%, #22d3ee 100%)',
    boxShadow: '0 6px 20px -3px rgba(34, 211, 238, 0.6), 0 0 30px rgba(34, 211, 238, 0.4)',
    transform: 'perspective(1000px) translateZ(5px)',
  },
  loginLink: {
    textAlign: 'center' as const,
    color: 'rgba(255,255,255,0.7)',
    fontSize: '14px',
    marginTop: '24px',
  },
  link: {
    color: '#22d3ee',
    fontWeight: 700,
    textDecoration: 'none',
    transition: 'all 0.2s',
  },
};