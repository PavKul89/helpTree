import { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import './AuthPage.css';

export const AuthLayout = ({ children }: { children: ReactNode }) => (
  <main className="auth-page">
    <section className="auth-story" aria-label="О HelpTree">
      <Link to="/" className="auth-brand" aria-label="HelpTree — на главную">
        <img className="auth-brand-icon" src="/helptree-logo.png" alt="" />
        <span>HelpTree</span>
      </Link>

      <div className="auth-story-copy">
        <span className="auth-eyebrow"><span aria-hidden="true" /> Вместе — мы можем больше</span>
        <h1>Добрые дела <span>начинаются здесь</span></h1>
        <p>Помогайте, получайте поддержку и находите людей рядом. Вместе мы создаём сильное и заботливое сообщество.</p>
      </div>

      <div className="auth-community">
        <img className="auth-community-mark" src="/helptree-logo.png" alt="" />
        <span>Присоединяйтесь к сообществу HelpTree</span>
      </div>
    </section>

    <section className="auth-form-panel">
      <div className="auth-form-content">{children}</div>
      <p className="auth-footer">HelpTree <span aria-hidden="true">·</span> Сообщество, в котором забота растёт</p>
    </section>
  </main>
);
