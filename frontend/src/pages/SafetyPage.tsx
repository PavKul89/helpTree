import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Coins,
  Flag,
  Heart,
  Info,
  Loader2,
  Lock,
  MapPin,
  MessageCircle,
  RefreshCw,
  Settings2,
  ShieldCheck,
  ShieldOff,
} from 'lucide-react';
import { reportsApi } from '../api/reportsApi';
import { useAuth } from '../context/AuthContext';
import { getRelativeTime } from '../utils/dateUtils';
import type { Report, ReportReason, ReportStatus, ReportTargetType } from '../types';
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

const STATUS_META: Record<ReportStatus, { label: string; tone: string }> = {
  OPEN: { label: 'Открыта', tone: 'open' },
  IN_REVIEW: { label: 'На рассмотрении', tone: 'review' },
  RESOLVED: { label: 'Рассмотрена', tone: 'resolved' },
  REJECTED: { label: 'Отклонена', tone: 'rejected' },
  DUPLICATE: { label: 'Дубликат', tone: 'duplicate' },
};

const TARGET_META: Record<ReportTargetType, string> = {
  POST: 'Заявка',
  USER: 'Пользователь',
  COMMENT: 'Комментарий',
  REVIEW: 'Отзыв',
  MESSAGE: 'Сообщение',
  HELP: 'Помощь',
  CHAT: 'Чат',
  REPORT: 'Обращение',
};

const REASON_META: Record<ReportReason, string> = {
  SPAM: 'Спам',
  ABUSE: 'Оскорбление',
  HARASSMENT: 'Преследование',
  INAPPROPRIATE: 'Неуместный контент',
  FRAUD: 'Мошенничество',
  IMPERSONATION: 'Выдача себя за другого',
  OTHER: 'Другое',
};

const REPORT_PAGE_SIZE = 6;

export const SafetyPage = () => {
  const { user } = useAuth();
  const [reports, setReports] = useState<Report[]>([]);
  const [reportsTotal, setReportsTotal] = useState(0);
  const [reportsLoading, setReportsLoading] = useState(false);
  const [reportsError, setReportsError] = useState<string | null>(null);

  const loadReports = useCallback(async () => {
    if (!user) {
      setReports([]);
      setReportsTotal(0);
      return;
    }
    setReportsLoading(true);
    setReportsError(null);
    try {
      const response = await reportsApi.getMine({ page: 0, size: REPORT_PAGE_SIZE });
      setReports(response.content);
      setReportsTotal(response.totalElements);
    } catch {
      setReportsError('Не удалось загрузить ваши жалобы. Попробуйте ещё раз.');
    } finally {
      setReportsLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void loadReports();
  }, [loadReports]);

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
                <span>
                  Своё сообщение можно отредактировать в течение 15 минут после
                  отправки и удалить в любой момент.
                </span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>
                  Удаление чата скрывает диалог в вашем списке. У собеседника
                  переписка сохраняется, поэтому не рассчитывайте, что удаление чата
                  стирает сообщения для другого участника.
                </span>
              </li>
              <li>
                <Check size={15} aria-hidden="true" />
                <span>
                  Если общение стало нежелательным, откройте профиль собеседника и
                  заблокируйте его — переписка и взаимодействие будут закрыты.
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

      <section className="safety-section" aria-labelledby="safety-reports-title">
        <div className="safety-section-head">
          <h2 id="safety-reports-title">Жалобы, блокировки и модерация</h2>
          <p>
            Эти механизмы работают на сервере: обращение попадает в очередь модерации,
            а его статус можно отслеживать ниже.
          </p>
        </div>
        <div className="safety-split safety-split--three">
          <article className="safety-panel">
            <span className="safety-panel-icon" aria-hidden="true">
              <Flag size={18} />
            </span>
            <h3>На что можно пожаловаться</h3>
            <p>
              На заявку, комментарий, отзыв и пользователя. На странице заявки можно
              пожаловаться на саму заявку, её комментарии, отзывы и автора. Жалоба
              содержит причину и необязательное описание, а повторная открытая жалоба
              на тот же объект отклоняется сервером.
            </p>
          </article>
          <article className="safety-panel">
            <span className="safety-panel-icon" aria-hidden="true">
              <ShieldOff size={18} />
            </span>
            <h3>Личная блокировка</h3>
            <p>
              В профиле пользователя доступна блокировка с областью действия: всё
              взаимодействие, только сообщения, публичный профиль с отзывами или
              отклики на помощь. Блокировку можно снять там же.
            </p>
          </article>
          <article className="safety-panel">
            <span className="safety-panel-icon" aria-hidden="true">
              <AlertTriangle size={18} />
            </span>
            <h3>Что происходит дальше</h3>
            <p>
              Обращение получает статус «Открыта», затем «На рассмотрении» и решение —
              «Рассмотрена», «Отклонена» или «Дубликат». О решении и действиях модерации
              приходят уведомления.
            </p>
          </article>
        </div>

        {user && (
          <div className="safety-my-reports">
            <div className="safety-my-reports-head">
              <div>
                <h3>Мои жалобы</h3>
                <p>
                  {reportsTotal > 0
                    ? `Всего обращений: ${reportsTotal}. Показаны последние ${reports.length}.`
                    : 'Здесь появятся отправленные вами обращения и их статусы.'}
                </p>
              </div>
              <button
                type="button"
                className="safety-reports-refresh"
                onClick={() => void loadReports()}
                disabled={reportsLoading}
              >
                {reportsLoading ? (
                  <Loader2 size={14} className="safety-spin" aria-hidden="true" />
                ) : (
                  <RefreshCw size={14} aria-hidden="true" />
                )}
                Обновить
              </button>
            </div>

            {reportsError && (
              <p className="safety-reports-error" role="alert">
                {reportsError}
              </p>
            )}

            {!reportsError && reportsLoading && reports.length === 0 && (
              <p className="safety-reports-empty">
                <Loader2 size={14} className="safety-spin" aria-hidden="true" />
                Загружаем обращения…
              </p>
            )}

            {!reportsError && !reportsLoading && reports.length === 0 && (
              <p className="safety-reports-empty">
                Вы ещё не отправляли жалоб. Кнопки «Жалоба на заявку» и «Жалоба на
                автора» есть на странице заявки, а в комментариях и отзывах — отдельная
                кнопка жалобы.
              </p>
            )}

            {reports.length > 0 && (
              <ul className="safety-reports-list">
                {reports.map((report) => {
                  const status = STATUS_META[report.status];
                  return (
                    <li key={report.id} className="safety-report-item">
                      <div className="safety-report-main">
                        <span className={`safety-report-status is-${status.tone}`}>
                          {status.label}
                        </span>
                        <strong>
                          {TARGET_META[report.targetType]} #{report.targetId}
                        </strong>
                        <span className="safety-report-reason">
                          {REASON_META[report.reason]}
                        </span>
                      </div>
                      {report.description && (
                        <p className="safety-report-description">{report.description}</p>
                      )}
                      {report.resolution && (
                        <p className="safety-report-resolution">
                          Решение: {report.resolution}
                        </p>
                      )}
                      <div className="safety-report-meta">
                        <span>№{report.id}</span>
                        <span>Создана {getRelativeTime(report.createdAt)}</span>
                        {report.updatedAt && report.updatedAt !== report.createdAt && (
                          <span>Обновлена {getRelativeTime(report.updatedAt)}</span>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </section>

      <section className="safety-section" aria-labelledby="safety-incident-title">
        <div className="safety-section-head">
          <h2 id="safety-incident-title">Если вы столкнулись с нарушением</h2>
          <p>Что можно сделать прямо сейчас.</p>
        </div>
        <div className="safety-panel safety-panel--steps">
          <ol className="safety-steps">
            <li>
              <span>
                <strong>Остановите взаимодействие.</strong> Прекратите переписку или
                встречу, если чувствуете угрозу или сильное давление. При необходимости
                заблокируйте пользователя.
              </span>
            </li>
            <li>
              <span>
                <strong>Сохраните факты.</strong> Сделайте скриншоты и сохраните ссылки
                на заявку или профиль — это поможет восстановить обстоятельства и
                дополнить жалобу.
              </span>
            </li>
            <li>
              <span>
                <strong>Отправьте жалобу.</strong> Используйте кнопки жалобы на
                странице заявки — для самой заявки, её комментариев, отзывов или автора.
                Статус обращения отображается в разделе «Мои жалобы».
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
            HelpTree — платформа взаимопомощи, а не служба экстренной помощи. Жалоба
            попадает в очередь модерации, но не заменяет обращение в экстренные службы и
            не гарантирует немедленную реакцию.
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
