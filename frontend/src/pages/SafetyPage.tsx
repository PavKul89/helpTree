import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Coins,
  Heart,
  Info,
  Lock,
  MapPin,
  MessageCircle,
  Settings2,
  ShieldCheck,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import './SafetyPage.css';

interface GuidanceCard {
  icon: ReactNode;
  title: string;
  text: string;
}

const GUIDANCE_CARDS: GuidanceCard[] = [
  {
    icon: <Heart size={20} aria-hidden="true" />,
    title: 'Уважительное общение',
    text: 'Общайтесь без оскорблений, угроз и давления. Отказ от помощи — нормальный ответ, и он не должен становиться поводом для конфликта.',
  },
  {
    icon: <ShieldCheck size={20} aria-hidden="true" />,
    title: 'Проверяйте информацию',
    text: 'Сверяйте детали заявки, профиль и историю помощи. Срочность или настойчивость собеседника сами по себе не подтверждают его слова.',
  },
  {
    icon: <Lock size={20} aria-hidden="true" />,
    title: 'Берегите личные данные',
    text: 'Не отправляйте паспортные данные, коды из SMS, пароли и банковские реквизиты. Делитесь только тем, что действительно нужно для помощи.',
  },
  {
    icon: <MapPin size={20} aria-hidden="true" />,
    title: 'Безопасные встречи',
    text: 'Для первой встречи выбирайте людное место. Сообщите близким, куда и с кем идёте, и оставайтесь на связи.',
  },
  {
    icon: <Coins size={20} aria-hidden="true" />,
    title: 'Деньги и вещи',
    text: 'Обсуждайте условия заранее и не переводите предоплату незнакомым людям. Передавайте ценные вещи только там, где вы чувствуете себя безопасно.',
  },
  {
    icon: <MessageCircle size={20} aria-hidden="true" />,
    title: 'Тревожные сигналы',
    text: 'Если собеседник давит, угрожает или просит секретные данные, остановите общение. Не продолжайте разговор только из вежливости.',
  },
];

export const SafetyPage = () => {
  const { user } = useAuth();

  return (
    <div className="safety-page">
      <header className="safety-hero">
        <span className="safety-eyebrow">HelpTree · безопасное сообщество</span>
        <div className="safety-hero-title">
          <span className="safety-hero-mark" aria-hidden="true">
            <ShieldCheck size={26} />
          </span>
          <h1>Безопасность и защита сообщества</h1>
        </div>
        <p className="safety-hero-text">
          Как общаться спокойнее, беречь личные данные и что делать, если ситуация
          вызывает тревогу.
        </p>
        <p className="safety-hero-note">
          <Info size={16} aria-hidden="true" />
          <span>
            Это рекомендации, а не юридические гарантии. HelpTree не проверяет каждое
            сообщение и не может гарантировать безопасность личных встреч, сделок или
            передачи вещей.
          </span>
        </p>
      </header>

      <section className="safety-section" aria-labelledby="safety-guidance-title">
        <div className="safety-section-head">
          <h2 id="safety-guidance-title">Как общаться безопасно</h2>
          <p>Простые правила, которые снижают риск неприятных ситуаций.</p>
        </div>
        <div className="safety-grid">
          {GUIDANCE_CARDS.map((card) => (
            <article className="safety-card" key={card.title}>
              <span className="safety-card-icon" aria-hidden="true">
                {card.icon}
              </span>
              <h3>{card.title}</h3>
              <p>{card.text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="safety-section" aria-labelledby="safety-privacy-title">
        <div className="safety-section-head">
          <h2 id="safety-privacy-title">Личные данные и переписка</h2>
          <p>Что стоит держать при себе и как работает чат HelpTree.</p>
        </div>
        <div className="safety-split">
          <article className="safety-panel">
            <h3>Что не стоит публиковать</h3>
            <ul className="safety-list">
              <li>
                <Check size={15} aria-hidden="true" />
                <span>Точный домашний адрес и график отсутствия дома.</span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>Номера документов, банковские данные и коды подтверждения.</span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>Пароли и данные для входа в аккаунты.</span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>Сведения о других людях без их согласия.</span>
              </li>
            </ul>
          </article>

          <article className="safety-panel">
            <h3>Как устроен чат</h3>
            <ul className="safety-list">
              <li>
                <Check size={15} aria-hidden="true" />
                <span>
                  Переписка доступна участникам чата. Не отправляйте туда данные,
                  которые не готовы раскрыть.
                </span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>Вы можете удалить своё сообщение.</span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>
                  Удаление чата доступно его участнику и удаляет переписку для обоих
                  собеседников.
                </span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>
                  Встроенной жалобы на сообщение или собеседника в текущей версии нет.
                </span>
              </li>
            </ul>
          </article>
        </div>
      </section>

      <section className="safety-section" aria-labelledby="safety-works-title">
        <div className="safety-section-head">
          <h2 id="safety-works-title">Что уже действует</h2>
          <p>Реальные механизмы платформы, на которые можно рассчитывать.</p>
        </div>
        <div className="safety-split safety-split--three">
          <article className="safety-panel">
            <h3>Ограничение аккаунта за долг</h3>
            <p>
              При задолженности сервер может ограничить создание заявок и откликов до
              разблокировки аккаунта. Это ограничение связано с долгом, а не с жалобой
              другого пользователя.
            </p>
          </article>
          <article className="safety-panel">
            <h3>Права администратора</h3>
            <p>
              Управлять чужими заявками и отзывами может только пользователь с ролью
              ADMIN. Обычный участник управляет своим контентом, а проверка прав
              выполняется на сервере.
            </p>
          </article>
          <article className="safety-panel">
            <h3>Свои материалы</h3>
            <p>
              Вы можете удалить свою заявку, комментарий, отзыв или сообщение в чате.
              Комментарии других авторов обычный участник удалить не может.
            </p>
          </article>
        </div>
      </section>

      <section className="safety-section" aria-labelledby="safety-limits-title">
        <div className="safety-limitations">
          <div className="safety-limitations-head">
            <span className="safety-limitations-icon" aria-hidden="true">
              <AlertTriangle size={22} />
            </span>
            <div>
              <h2 id="safety-limits-title">Встроенные жалобы пока не поддерживаются</h2>
              <p>
                В текущей версии HelpTree нет серверного API для жалоб и модерации
                обращений. Поэтому в интерфейсе нет кнопок «Пожаловаться», списка «Мои
                жалобы», статусов рассмотрения и раздела модерации: такие элементы были
                бы декоративными и вводили бы в заблуждение.
              </p>
            </div>
          </div>

          <ul className="safety-limits-list">
            <li>
              Жалобы на пользователей, заявки, публикации, комментарии, отзывы и
              сообщения чата.
            </li>
            <li>Личная блокировка нежелательного пользователя и список блокировок.</li>
            <li>Ограничения отправки сообщений в чате на основании жалоб.</li>
            <li>Уведомления о статусе и решении по обращению.</li>
          </ul>

          <p className="safety-limitations-note">
            Это ограничение backend, а не скрытая настройка интерфейса. Пока сервер не
            принимает обращения, HelpTree не показывает ложные подтверждения и не
            обещает сроки рассмотрения.
          </p>
        </div>
      </section>

      <section className="safety-section" aria-labelledby="safety-incident-title">
        <div className="safety-section-head">
          <h2 id="safety-incident-title">Если вы столкнулись с нарушением</h2>
          <p>Что можно сделать прямо сейчас, не дожидаясь встроенных инструментов.</p>
        </div>
        <div className="safety-panel safety-panel--steps">
          <ol className="safety-steps">
            <li>
              <span>
                <strong>Остановите взаимодействие.</strong> Прекратите переписку или
                встречу, если чувствуете угрозу или сильное давление.
              </span>
            </li>
            <li>
              <span>
                <strong>Сохраните факты.</strong> Сделайте скриншоты и сохраните ссылки
                на заявку или профиль — это поможет восстановить обстоятельства.
              </span>
            </li>
            <li>
              <span>
                <strong>Не передавайте новые данные.</strong> Не сообщайте коды, пароли
                и реквизиты, даже если собеседник торопит или угрожает.
              </span>
            </li>
            <li>
              <span>
                <strong>Обратитесь в профильные службы.</strong> При угрозе жизни,
                здоровью или имуществу используйте экстренные службы и официальные
                каналы, а не публичные заявки HelpTree.
              </span>
            </li>
          </ol>
          <p className="safety-steps-note">
            HelpTree — платформа взаимопомощи, а не служба экстренной помощи. В текущей
            версии у неё нет встроенного приёма жалоб, поэтому она не может
            гарантировать рассмотрение обращения внутри приложения.
          </p>
        </div>
      </section>

      <div className="safety-actions">
        <Link to="/posts" className="safety-action safety-action--primary">
          <ArrowLeft size={16} aria-hidden="true" />
          Вернуться к заявкам
        </Link>
        {user && (
          <Link to="/settings" className="safety-action safety-action--ghost">
            <Settings2 size={16} aria-hidden="true" />
            Настройки профиля
          </Link>
        )}
      </div>
    </div>
  );
};

export default SafetyPage;
