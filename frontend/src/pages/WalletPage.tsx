import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowLeft,
  Award,
  CalendarCheck,
  ChevronLeft,
  ChevronRight,
  Coins,
  Crown,
  Gift,
  HandHeart,
  History,
  Loader2,
  Lock,
  Palette,
  RefreshCw,
  Sparkles,
  Star,
  Unlock,
  Wallet,
  X,
  Zap,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import {
  walletApi,
  notifyWalletUpdated,
  type CoinTransactionDto,
  type PageResponse,
  type WalletDto,
} from '../api/walletApi';
import { postsApi } from '../api/postsApi';
import { useToast } from '../components/Toast';
import { getRelativeTime } from '../utils/dateUtils';
import type { Post } from '../types';
import './WalletPage.css';

const HISTORY_PAGE_SIZE = 10;
// Matches the app.economy.boost-price value used by POST /api/posts/{id}/boost.
const BOOST_PRICE = 5;

type Direction = 'in' | 'out';
type TxIcon = React.ComponentType<{ size?: number | string; className?: string }>;

interface TransactionMeta {
  label: string;
  direction: Direction;
  Icon: TxIcon;
}

const TRANSACTION_META: Record<string, TransactionMeta> = {
  HELP_GIVEN: { label: 'Награда за помощь', direction: 'in', Icon: HandHeart },
  HELP_RECEIVED: { label: 'Помощь получена', direction: 'in', Icon: ArrowDownLeft },
  REVIEW_BONUS: { label: 'Бонус за отзыв', direction: 'in', Icon: Star },
  DAILY_LOGIN: { label: 'Ежедневный вход', direction: 'in', Icon: CalendarCheck },
  GIFT_RECEIVED: { label: 'Подарок получен', direction: 'in', Icon: Gift },
  ADMIN_BONUS: { label: 'Бонус от администратора', direction: 'in', Icon: Sparkles },
  FIRST_HELP: { label: 'Бонус новичка', direction: 'in', Icon: Award },
  POST_BOOST: { label: 'Поднятие заявки', direction: 'out', Icon: Zap },
  BOOST: { label: 'Поднятие заявки', direction: 'out', Icon: Zap },
  VIP_STATUS: { label: 'VIP-статус', direction: 'out', Icon: Crown },
  VIP: { label: 'VIP-статус', direction: 'out', Icon: Crown },
  ACCOUNT_UNBLOCK: { label: 'Разблокировка аккаунта', direction: 'out', Icon: Unlock },
  GIFT_SENT: { label: 'Подарок отправлен', direction: 'out', Icon: Gift },
  NICKNAME_COLOR: { label: 'Смена цвета ника', direction: 'out', Icon: Palette },
};

const numberFormatter = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });

const formatNumber = (value: number): string => numberFormatter.format(value);

const formatCoins = (value: number): string => `${formatNumber(value)} HC`;

const formatSignedCoins = (amount: number): string => {
  const sign = amount > 0 ? '+' : amount < 0 ? '−' : '';
  return `${sign}${formatNumber(Math.abs(amount))} HC`;
};

const formatFullDate = (value: string): string => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const pluralize = (count: number, forms: [string, string, string]): string => {
  const abs = Math.abs(count) % 100;
  const last = abs % 10;
  if (abs > 10 && abs < 20) return forms[2];
  if (last > 1 && last < 5) return forms[1];
  if (last === 1) return forms[0];
  return forms[2];
};

const getTransactionMeta = (transaction: CoinTransactionDto): TransactionMeta =>
  TRANSACTION_META[transaction.type] ?? {
    label: 'Операция',
    direction: transaction.amount < 0 ? 'out' : 'in',
    Icon: Coins,
  };

const isCanceled = (error: unknown): boolean => {
  const candidate = error as { code?: string; name?: string } | null;
  return (
    candidate?.code === 'ERR_CANCELED' ||
    candidate?.name === 'CanceledError' ||
    candidate?.name === 'AbortError'
  );
};

const getErrorMessage = (error: unknown, fallback: string): string => {
  const message = (error as { response?: { data?: { message?: unknown } } } | null)?.response
    ?.data?.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
};

const getErrorStatus = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } } | null)?.response?.status;

const dedupeTransactions = (transactions: CoinTransactionDto[]): CoinTransactionDto[] => {
  const seen = new Set<number>();
  return transactions.filter((transaction) => {
    if (seen.has(transaction.id)) return false;
    seen.add(transaction.id);
    return true;
  });
};

export const WalletPage: React.FC = () => {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [wallet, setWallet] = useState<WalletDto | null>(null);
  const [walletLoading, setWalletLoading] = useState(true);
  const [walletError, setWalletError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  const [page, setPage] = useState(0);
  const [history, setHistory] = useState<PageResponse<CoinTransactionDto> | null>(null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedTransaction, setSelectedTransaction] = useState<CoinTransactionDto | null>(null);

  const [boostOpen, setBoostOpen] = useState(false);
  const [posts, setPosts] = useState<Post[] | null>(null);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postsError, setPostsError] = useState<string | null>(null);
  const [selectedPostId, setSelectedPostId] = useState<number | null>(null);
  const [boostError, setBoostError] = useState<string | null>(null);
  const [boosting, setBoosting] = useState(false);
  const boostingRef = useRef(false);

  useEffect(() => {
    if (!user) {
      setWallet(null);
      setWalletLoading(false);
      setHistory(null);
      setHistoryLoading(false);
      return;
    }

    const controller = new AbortController();
    setForbidden(false);
    setWalletLoading(true);
    setWalletError(null);

    walletApi
      .getWallet(user.id, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setWallet(data);
        notifyWalletUpdated(data.balance);
      })
      .catch((error) => {
        if (controller.signal.aborted || isCanceled(error)) return;
        if (getErrorStatus(error) === 403) {
          setForbidden(true);
          setWallet(null);
          return;
        }
        setWalletError(getErrorMessage(error, 'Не удалось загрузить баланс'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setWalletLoading(false);
      });

    return () => controller.abort();
  }, [user, refreshKey]);

  useEffect(() => {
    if (!user) return;

    const controller = new AbortController();
    setHistoryLoading(true);
    setHistoryError(null);

    walletApi
      .getTransactions(user.id, page, HISTORY_PAGE_SIZE, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setHistory({ ...data, content: dedupeTransactions(data.content) });
        if (data.totalPages > 0 && page > data.totalPages - 1) {
          setPage(data.totalPages - 1);
        }
      })
      .catch((error) => {
        if (controller.signal.aborted || isCanceled(error)) return;
        if (getErrorStatus(error) === 403) {
          setForbidden(true);
          return;
        }
        setHistoryError(getErrorMessage(error, 'Не удалось загрузить историю операций'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false);
      });

    return () => controller.abort();
  }, [user, page, refreshKey]);

  useEffect(() => {
    const modalOpen = Boolean(selectedTransaction) || boostOpen;
    if (!modalOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (selectedTransaction) {
        setSelectedTransaction(null);
      } else if (!boostingRef.current) {
        setBoostOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [selectedTransaction, boostOpen]);

  const refreshWalletData = () => {
    setPage(0);
    setRefreshKey((key) => key + 1);
  };

  const openBoostModal = async () => {
    if (!user || boostingRef.current) return;

    setBoostOpen(true);
    setBoostError(null);
    setSelectedPostId(null);
    setPosts(null);
    setPostsError(null);
    setPostsLoading(true);

    try {
      const userPosts = await postsApi.getByUser(user.id);
      setPosts(userPosts.filter((post) => post.status === 'OPEN'));
    } catch (error) {
      setPostsError(getErrorMessage(error, 'Не удалось загрузить ваши заявки'));
    } finally {
      setPostsLoading(false);
    }
  };

  const closeBoostModal = () => {
    if (boostingRef.current) return;
    setBoostOpen(false);
  };

  const handleBoost = async () => {
    if (!user || !selectedPostId || boostingRef.current) return;

    boostingRef.current = true;
    setBoosting(true);
    setBoostError(null);

    try {
      const result = await postsApi.boost(selectedPostId);
      showToast(result.message || 'Заявка поднята в топ', 'success');
      setBoostOpen(false);
      setSelectedPostId(null);
      refreshWalletData();
    } catch (error) {
      const message = getErrorMessage(error, 'Не удалось поднять заявку');
      setBoostError(message);
      showToast(message, 'error');
      // Never leave an old balance on screen after a financial operation.
      refreshWalletData();
    } finally {
      boostingRef.current = false;
      setBoosting(false);
    }
  };

  const renderWalletBody = () => {
    if (walletLoading) {
      return (
        <div className="wallet-balance-loading" aria-label="Загрузка баланса">
          <span className="wallet-skeleton wallet-skeleton--label" />
          <span className="wallet-skeleton wallet-skeleton--amount" />
          <div className="wallet-balance-stats">
            <span className="wallet-skeleton wallet-skeleton--stat" />
            <span className="wallet-skeleton wallet-skeleton--stat" />
          </div>
        </div>
      );
    }

    if (walletError) {
      return (
        <div className="wallet-balance-error" role="alert">
          <span className="wallet-balance-error-icon" aria-hidden="true">
            <AlertCircle size={22} />
          </span>
          <div>
            <strong>Не удалось загрузить баланс</strong>
            <p>{walletError}</p>
          </div>
          <button type="button" className="wallet-ghost-button" onClick={refreshWalletData}>
            <RefreshCw size={15} /> Повторить
          </button>
        </div>
      );
    }

    if (!wallet) return null;

    return (
      <>
        <span className="wallet-balance-label">
          <Coins size={15} aria-hidden="true" /> Текущий баланс
        </span>
        <div className="wallet-balance-value">
          <span className="wallet-balance-number">{formatNumber(wallet.balance)}</span>
          <span className="wallet-balance-unit">HC</span>
        </div>
        {wallet.balance === 0 && (
          <p className="wallet-zero-note">
            Баланс пока пуст. Первые HC можно получить за помощь сообществу.
          </p>
        )}
        <div className="wallet-balance-stats">
          <div className="wallet-balance-stat">
            <span>Получено</span>
            <strong className="wallet-balance-stat-in">+{formatCoins(wallet.totalEarned)}</strong>
          </div>
          <div className="wallet-balance-stat">
            <span>Потрачено</span>
            <strong className="wallet-balance-stat-out">−{formatCoins(wallet.totalSpent)}</strong>
          </div>
        </div>
      </>
    );
  };

  const renderHistoryBody = () => {
    if (historyLoading) {
      return (
        <div className="wallet-history-skeleton" aria-label="Загрузка истории операций">
          {[0, 1, 2, 3, 4].map((row) => (
            <div className="wallet-history-skeleton-row" key={row}>
              <span className="wallet-skeleton wallet-skeleton--icon" />
              <span className="wallet-history-skeleton-text">
                <span className="wallet-skeleton wallet-skeleton--line" />
                <span className="wallet-skeleton wallet-skeleton--line wallet-skeleton--short" />
              </span>
              <span className="wallet-skeleton wallet-skeleton--amount-small" />
            </div>
          ))}
        </div>
      );
    }

    if (historyError) {
      return (
        <div className="wallet-state wallet-state--inside" role="alert">
          <span className="wallet-state-icon" aria-hidden="true">
            <AlertCircle size={25} />
          </span>
          <h3>История не загрузилась</h3>
          <p>{historyError}</p>
          <button type="button" className="wallet-ghost-button" onClick={refreshWalletData}>
            <RefreshCw size={15} /> Повторить
          </button>
        </div>
      );
    }

    if (!history || history.content.length === 0) {
      return (
        <div className="wallet-state wallet-state--inside">
          <span className="wallet-state-icon" aria-hidden="true">
            <History size={25} />
          </span>
          <h3>Пока нет операций</h3>
          <p>Здесь появятся начисления и списания HC, когда вы начнёте помогать по заявкам.</p>
          <Link className="wallet-ghost-button" to="/posts">
            Перейти к заявкам
          </Link>
        </div>
      );
    }

    return (
      <ul className="wallet-transactions">
        {history.content.map((transaction) => {
          const meta = getTransactionMeta(transaction);
          const Icon = meta.Icon;
          const direction = meta.direction;

          return (
            <li key={transaction.id}>
              <button
                type="button"
                className={`wallet-transaction wallet-transaction--${direction}`}
                onClick={() => setSelectedTransaction(transaction)}
                aria-label={`${meta.label}: ${formatSignedCoins(transaction.amount)}. Подробности операции`}
              >
                <span className="wallet-transaction-icon" aria-hidden="true">
                  <Icon size={19} />
                </span>
                <span className="wallet-transaction-main">
                  <span className="wallet-transaction-title">{meta.label}</span>
                  {transaction.description && transaction.description.trim() !== meta.label && (
                    <span className="wallet-transaction-description">{transaction.description}</span>
                  )}
                </span>
                <span className="wallet-transaction-side">
                  <span className={`wallet-transaction-amount wallet-transaction-amount--${direction}`}>
                    {formatSignedCoins(transaction.amount)}
                  </span>
                  <time className="wallet-transaction-date" dateTime={transaction.createdAt}>
                    {getRelativeTime(transaction.createdAt)}
                  </time>
                </span>
                <ChevronRight className="wallet-transaction-chevron" size={17} aria-hidden="true" />
              </button>
            </li>
          );
        })}
      </ul>
    );
  };

  const renderPagination = () => {
    if (!history || history.totalPages <= 1) return null;

    const currentPage = history.number;
    const isFirst = currentPage <= 0;
    const isLast = currentPage >= history.totalPages - 1;

    return (
      <nav className="wallet-pagination" aria-label="Страницы истории операций">
        <button
          type="button"
          className="wallet-pagination-button"
          onClick={() => setPage((value) => Math.max(0, value - 1))}
          disabled={isFirst || historyLoading}
        >
          <ChevronLeft size={16} aria-hidden="true" /> Назад
        </button>
        <span className="wallet-pagination-status">
          Страница <strong>{currentPage + 1}</strong> из {history.totalPages}
        </span>
        <button
          type="button"
          className="wallet-pagination-button"
          onClick={() => setPage((value) => value + 1)}
          disabled={isLast || historyLoading}
        >
          Вперёд <ChevronRight size={16} aria-hidden="true" />
        </button>
      </nav>
    );
  };

  const selectedMeta = selectedTransaction ? getTransactionMeta(selectedTransaction) : null;
  const SelectedIcon = selectedMeta?.Icon;
  const totalLabel = history
    ? `${formatNumber(history.totalElements)} ${pluralize(history.totalElements, [
        'операция',
        'операции',
        'операций',
      ])}`
    : null;

  return (
    <main className="wallet-page">
      <header className="wallet-heading">
        <span className="wallet-heading-mark" aria-hidden="true">
          <Wallet size={23} />
        </span>
        <div>
          <span className="wallet-eyebrow">HelpTree · баланс и история</span>
          <h1>Мой кошелёк</h1>
          <p>Управляйте средствами и следите за историей операций.</p>
        </div>
      </header>

      {forbidden ? (
        <section className="wallet-state wallet-state--page" role="alert">
          <span className="wallet-state-icon" aria-hidden="true">
            <Lock size={26} />
          </span>
          <h2>Нет доступа к кошельку</h2>
          <p>Сервер разрешает просматривать только собственный баланс и историю операций.</p>
          <Link className="wallet-ghost-button" to="/">
            <ArrowLeft size={15} /> На главную
          </Link>
        </section>
      ) : (
        <>
          <section className="wallet-overview" aria-label="Баланс и финансовые действия">
            <div className="wallet-balance-card">
              <span className="wallet-balance-orbit wallet-balance-orbit--one" aria-hidden="true" />
              <span className="wallet-balance-orbit wallet-balance-orbit--two" aria-hidden="true" />
              <div className="wallet-balance-content">{renderWalletBody()}</div>
            </div>

            <aside className="wallet-action-card">
              <span className="wallet-action-icon" aria-hidden="true">
                <Zap size={21} />
              </span>
              <span className="wallet-action-eyebrow">Финансовое действие</span>
              <h2>Поднять заявку</h2>
              <p>Выберите свою открытую заявку — она окажется выше в ленте на 24 часа.</p>
              <button type="button" className="wallet-primary-button" onClick={openBoostModal}>
                <Zap size={16} aria-hidden="true" /> Выбрать заявку
              </button>
              <span className="wallet-action-note">
                Стоимость {BOOST_PRICE} HC спишет сервер после подтверждения.
              </span>
            </aside>
          </section>

          <div className="wallet-content">
            <section className="wallet-history-card" aria-labelledby="wallet-history-title">
              <header className="wallet-history-header">
                <div>
                  <span className="wallet-section-eyebrow">Движение средств</span>
                  <h2 id="wallet-history-title">История операций</h2>
                </div>
                {totalLabel && !historyLoading && !historyError && (
                  <span className="wallet-history-count">{totalLabel}</span>
                )}
              </header>

              {renderHistoryBody()}
              {renderPagination()}
            </section>

            <aside className="wallet-facts-card" aria-labelledby="wallet-facts-title">
              <header>
                <span className="wallet-facts-icon" aria-hidden="true">
                  <Sparkles size={18} />
                </span>
                <div>
                  <span className="wallet-section-eyebrow">Правила сервера</span>
                  <h2 id="wallet-facts-title">Как получить HC</h2>
                </div>
              </header>
              <ul className="wallet-facts-list">
                <li>
                  <HandHeart size={16} aria-hidden="true" />
                  <span>Помощь по заявке</span>
                  <strong>+10</strong>
                </li>
                <li>
                  <ArrowDownLeft size={16} aria-hidden="true" />
                  <span>Полученная помощь</span>
                  <strong>+2</strong>
                </li>
                <li>
                  <Star size={16} aria-hidden="true" />
                  <span>Отзыв</span>
                  <strong>+2</strong>
                </li>
                <li>
                  <CalendarCheck size={16} aria-hidden="true" />
                  <span>Ежедневный вход</span>
                  <strong>+1</strong>
                </li>
                <li>
                  <Award size={16} aria-hidden="true" />
                  <span>Бонус новичка</span>
                  <strong>+3</strong>
                </li>
              </ul>
              <p className="wallet-facts-note">
                Ежедневный бонус начисляется автоматически при входе. Первые три помощи дополнительно
                приносят бонус новичка.
              </p>
            </aside>
          </div>
        </>
      )}

      {selectedTransaction && selectedMeta && SelectedIcon && (
        <div
          className="wallet-modal-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setSelectedTransaction(null);
          }}
        >
          <section
            className="wallet-modal wallet-modal--details"
            role="dialog"
            aria-modal="true"
            aria-labelledby="wallet-detail-title"
          >
            <header className="wallet-modal-header">
              <span
                className={`wallet-modal-mark wallet-modal-mark--${selectedMeta.direction}`}
                aria-hidden="true"
              >
                <SelectedIcon size={21} />
              </span>
              <div>
                <span className="wallet-section-eyebrow">Операция #{selectedTransaction.id}</span>
                <h2 id="wallet-detail-title">{selectedMeta.label}</h2>
              </div>
              <button
                type="button"
                className="wallet-modal-close"
                onClick={() => setSelectedTransaction(null)}
                aria-label="Закрыть подробности операции"
              >
                <X size={18} />
              </button>
            </header>

            <div className="wallet-modal-body">
              <div
                className={`wallet-detail-amount wallet-detail-amount--${selectedMeta.direction}`}
              >
                {formatSignedCoins(selectedTransaction.amount)}
              </div>

              <dl className="wallet-detail-list">
                <div>
                  <dt>Тип операции</dt>
                  <dd>{selectedMeta.label}</dd>
                </div>
                {selectedTransaction.description &&
                  selectedTransaction.description.trim() !== selectedMeta.label && (
                    <div>
                      <dt>Описание</dt>
                      <dd>{selectedTransaction.description}</dd>
                    </div>
                  )}
                <div>
                  <dt>Дата и время</dt>
                  <dd>{formatFullDate(selectedTransaction.createdAt)}</dd>
                </div>
                <div>
                  <dt>Идентификатор</dt>
                  <dd>#{selectedTransaction.id}</dd>
                </div>
                {selectedTransaction.relatedPostId && (
                  <div>
                    <dt>Связанная заявка</dt>
                    <dd>
                      <Link
                        to={`/posts/${selectedTransaction.relatedPostId}`}
                        onClick={() => setSelectedTransaction(null)}
                      >
                        Открыть заявку #{selectedTransaction.relatedPostId}
                      </Link>
                    </dd>
                  </div>
                )}
                {selectedTransaction.relatedUserId && (
                  <div>
                    <dt>Связанный участник</dt>
                    <dd>
                      <Link
                        to={`/profile/${selectedTransaction.relatedUserId}`}
                        onClick={() => setSelectedTransaction(null)}
                      >
                        Профиль участника #{selectedTransaction.relatedUserId}
                      </Link>
                    </dd>
                  </div>
                )}
              </dl>
            </div>
          </section>
        </div>
      )}

      {boostOpen && (
        <div
          className="wallet-modal-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeBoostModal();
          }}
        >
          <section
            className="wallet-modal wallet-modal--boost"
            role="dialog"
            aria-modal="true"
            aria-labelledby="wallet-boost-title"
          >
            <header className="wallet-modal-header">
              <span className="wallet-modal-mark wallet-modal-mark--lime" aria-hidden="true">
                <Zap size={21} />
              </span>
              <div>
                <span className="wallet-section-eyebrow">Поднятие заявки</span>
                <h2 id="wallet-boost-title">Выберите заявку</h2>
              </div>
              <button
                type="button"
                className="wallet-modal-close"
                onClick={closeBoostModal}
                aria-label="Закрыть выбор заявки"
                disabled={boosting}
              >
                <X size={18} />
              </button>
            </header>

            <div className="wallet-modal-body">
              {postsLoading ? (
                <div className="wallet-boost-skeleton" aria-label="Загрузка заявок">
                  {[0, 1, 2].map((row) => (
                    <span className="wallet-skeleton wallet-skeleton--post" key={row} />
                  ))}
                </div>
              ) : postsError ? (
                <div className="wallet-state wallet-state--inside" role="alert">
                  <span className="wallet-state-icon" aria-hidden="true">
                    <AlertCircle size={24} />
                  </span>
                  <h3>Заявки не загрузились</h3>
                  <p>{postsError}</p>
                  <button type="button" className="wallet-ghost-button" onClick={openBoostModal}>
                    <RefreshCw size={15} /> Повторить
                  </button>
                </div>
              ) : !posts || posts.length === 0 ? (
                <div className="wallet-state wallet-state--inside">
                  <span className="wallet-state-icon" aria-hidden="true">
                    <History size={24} />
                  </span>
                  <h3>Нет открытых заявок</h3>
                  <p>Поднять можно только свою заявку в статусе «Открыта».</p>
                  <Link className="wallet-ghost-button" to="/posts/new" onClick={closeBoostModal}>
                    Создать заявку
                  </Link>
                </div>
              ) : (
                <ul className="wallet-boost-list">
                  {posts.map((post) => {
                    const selected = post.id === selectedPostId;
                    const boosted =
                      Boolean(post.boostedUntil) && new Date(post.boostedUntil as string) > new Date();

                    return (
                      <li key={post.id}>
                        <button
                          type="button"
                          className={`wallet-boost-option${selected ? ' is-selected' : ''}`}
                          onClick={() => setSelectedPostId(post.id)}
                          aria-pressed={selected}
                          disabled={boosting}
                        >
                          <span className="wallet-boost-option-mark" aria-hidden="true">
                            {selected ? <Zap size={17} /> : <span />}
                          </span>
                          <span className="wallet-boost-option-main">
                            <span className="wallet-boost-option-title">{post.title}</span>
                            <span className="wallet-boost-option-meta">
                              {boosted
                                ? `Уже в топе до ${formatFullDate(post.boostedUntil as string)}`
                                : 'Открытая заявка'}
                            </span>
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}

              {boostError && (
                <p className="wallet-inline-error" role="alert">
                  <AlertCircle size={15} aria-hidden="true" /> {boostError}
                </p>
              )}
            </div>

            <footer className="wallet-modal-footer">
              <div className="wallet-modal-actions">
                <button
                  type="button"
                  className="wallet-ghost-button"
                  onClick={closeBoostModal}
                  disabled={boosting}
                >
                  Отмена
                </button>
                <button
                  type="button"
                  className="wallet-primary-button"
                  onClick={handleBoost}
                  disabled={!selectedPostId || boosting}
                >
                  {boosting ? (
                    <>
                      <Loader2 className="wallet-spinner" size={16} aria-hidden="true" /> Поднимаем…
                    </>
                  ) : (
                    <>
                      <Zap size={16} aria-hidden="true" /> Поднять за {BOOST_PRICE} HC
                    </>
                  )}
                </button>
              </div>
              <p className="wallet-modal-note">
                Стоимость спишет сервер. Баланс и история обновятся после подтверждения операции.
              </p>
            </footer>
          </section>
        </div>
      )}
    </main>
  );
};
