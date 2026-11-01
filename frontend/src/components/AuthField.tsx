import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

interface AuthFieldProps {
  id: string;
  label: string;
  type?: 'text' | 'email' | 'password' | 'tel';
  value: string;
  placeholder: string;
  autoComplete?: string;
  inputMode?: 'email' | 'tel';
  error?: string;
  maxLength?: number;
  onChange: (value: string) => void;
  onBlur?: () => void;
}

export const AuthField = ({
  id, label, type = 'text', value, placeholder, autoComplete, inputMode, error, maxLength, onChange, onBlur,
}: AuthFieldProps) => {
  const [passwordVisible, setPasswordVisible] = useState(false);
  const isPassword = type === 'password';

  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <div className={`auth-input-wrap${error ? ' has-error' : ''}${isPassword ? ' has-password' : ''}`}>
        <input
          id={id}
          name={id}
          className="auth-input"
          type={isPassword && passwordVisible ? 'text' : type}
          value={value}
          placeholder={placeholder}
          autoComplete={autoComplete}
          inputMode={inputMode}
          maxLength={maxLength}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        {isPassword && (
          <button
            type="button"
            className="auth-password-toggle"
            onClick={() => setPasswordVisible((visible) => !visible)}
            aria-label={passwordVisible ? 'Скрыть пароль' : 'Показать пароль'}
            aria-pressed={passwordVisible}
          >
            {passwordVisible ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        )}
      </div>
      {error && <p className="auth-field-error" id={`${id}-error`}>{error}</p>}
    </div>
  );
};
