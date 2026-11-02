import { BellRing, CircleHelp } from 'lucide-react';
import './NotificationCenterPage.css';

export const NotificationCenterPage = () => (
  <main className="notification-center">
    <header className="notification-center-heading">
      <span className="notification-center-mark" aria-hidden="true">
        <BellRing size={22} />
      </span>
      <div>
        <span className="notification-center-eyebrow">HelpTree · ваш центр событий</span>
        <h1>Уведомления</h1>
        <p>Здесь собраны важные события и обновления вашего сообщества.</p>
      </div>
    </header>

    <section className="notification-center-state" aria-labelledby="notification-state-title">
      <span className="notification-center-state-icon" aria-hidden="true">
        <CircleHelp size={25} />
      </span>
      <h2 id="notification-state-title">Центр уведомлений пока недоступен</h2>
      <p>
        Сервер пока не предоставляет API для списка уведомлений, счётчика новых
        событий или отметки прочтения. Поэтому здесь не показываются
        вымышленные уведомления и неподтверждённые счётчики.
      </p>
    </section>
  </main>
);

export default NotificationCenterPage;
