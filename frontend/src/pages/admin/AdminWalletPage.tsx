import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Ban,
  ChevronRight,
  Coins,
  FilterX,
  RefreshCw,
  Search,
  Sparkles,
  Wallet,
} from 'lucide-react';
import {
  adminApi,
  type AdminWalletAccount,
  type AdminWalletAccountsQuery,
} from '../../api/adminApi';
import type { PageResponse } from '../../api/walletApi';
import {
  AdminEmptyState,
  AdminErrorState,
  AdminLoadingBlock,
} from '../../components/admin/AdminFeedback';
import { Pagination } from '../../components/Pagination';
import { formatDateTime, formatNumber, getApiErrorMessage } from './adminUtils';

type BooleanFilter = 'ALL' | 'true' | 'false';
type SortOption = 'helpCoins-desc' | 'helpCoins-asc' | 'debtCount-desc' | 'createdAt-desc' | 'name-asc';

const PAGE_SIZE = 20;

const SORT_PARAMS: Record<
  SortOption,
  { sort: AdminWalletAccountsQuery['sort']; direction: 'asc' | 'desc' }
> = {
  'helpCoins-desc': { sort: 'helpCoins', direction: 'desc' },
  'helpCoins-asc': { sort: 'helpCoins', direction: 'asc' },
  'debtCount-desc': { sort: 'debtCount', direction: 'desc' },
  'createdAt-desc': { sort: 'createdAt', direction: 'desc' },
  'name-asc': { sort: 'name', direction: 'asc' },
};

export const AdminWalletPage: React.FC = () => {
  const [q, setQ] = useState('');
  const [minBalance, setMinBalance] = useState('');
  const [maxBalance, setMaxBalance] = useState('');
  const [vip, setVip] = useState<BooleanFilter>('ALL');
  const [blocked, setBlocked] = useState<BooleanFilter>('ALL');
  const [debtor, setDebtor] = useState<BooleanFilter>('ALL');
  const [sort, setSort] = useState<SortOption>('helpCoins-desc');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);

  const [data, setData] = useState<PageResponse<AdminWalletAccount> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const query = useMemo<AdminWalletAccountsQuery>(() => {
    const min = Number(minBalance);
    const max = Number(maxBalance);
    const { sort: sortField, direction } = SORT_PARAMS[sort];
    return {
      page,
      size: PAGE_SIZE,
      q: q.trim() || undefined,
      minBalance: Number.isFinite(min) && minBalance !== '' ? min : undefined,
      maxBalance: Number.isFinite(max) && maxBalance !== '' ? max : undefined,
      vip: vip === 'ALL' ? undefined : vip === 'true',
      blocked: blocked === 'ALL' ? undefined : blocked === 'true',
      debtor: debtor === 'ALL' ? undefined : debtor === 'true',
      sort: sortField,
      direction,
    };
  }, [page, q, minBalance, maxBalance, vip, blocked, debtor, sort]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    setLoading(true);
    setError(null);

    adminApi
      .getWalletAccounts(query, controller.signal)
      .then((response) => {
        if (!cancelled) setData(response);
      })
      .catch((loadError) => {
        if (cancelled || controller.signal.aborted) return;
        setError(getApiErrorMessage(loadError, 'Не удалось загрузить кошельки.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [query, reloadKey]);

  const resetFilters = useCallback(() => {
    setQ('');
    setMinBalance('');
    setMaxBalance('');
    setVip('ALL');
    setBlocked('ALL');
    setDebtor('ALL');
    setSort('helpCoins-desc');
    setPage(0);
    setReloadKey((value) => value + 1);
  }, []);

  const filtersActive =
    q.trim() !== '' ||
    minBalance !== '' ||
    maxBalance !== '' ||
    vip !== 'ALL' ||
    blocked !== 'ALL' ||
    debtor !== 'ALL' ||
    sort !== 'helpCoins-desc';

  const accounts = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="admin-stack">
      <div className="admin-notice">
        <Wallet size={17} aria-hidden="true" />
        <p>
          <code>GET /api/admin/wallet/accounts</code> — серверный обзор балансов с фильтрами по
          имени/email, диапазону HelpCoins, VIP, блокировке и долгу. Начисления и списания
          выполняются в карточке пользователя и пишутся в аудит как <code>COINS_ADJUSTED</code>.
        </p>
      </div>

      <form
        className="admin-toolbar-card"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setReloadKey((value) => value + 1);
        }}
      >
        <div className="admin-filter-grid">
          <label className="admin-field">
            <span>Поиск</span>
            <input
              type="search"
              value={q}
              onChange={(event) => {
                setQ(event.target.value);
                setPage(0);
              }}
              placeholder="Имя или email"
            />
          </label>
          <label className="admin-field">
            <span>Баланс от</span>
            <input
              type="number"
              inputMode="numeric"
              value={minBalance}
              onChange={(event) => {
                setMinBalance(event.target.value);
                setPage(0);
              }}
              placeholder="0"
            />
          </label>
          <label className="admin-field">
            <span>Баланс до</span>
            <input
              type="number"
              inputMode="numeric"
              value={maxBalance}
              onChange={(event) => {
                setMaxBalance(event.target.value);
                setPage(0);
              }}
              placeholder="Например, 500"
            />
          </label>
        </div>

        <div className="admin-toolbar">
          <div className="admin-toolbar-filters">
            <label className="admin-filter">
              <span>VIP</span>
              <select
                value={vip}
                onChange={(event) => {
                  setVip(event.target.value as BooleanFilter);
                  setPage(0);
                }}
              >
                <option value="ALL">Все</option>
                <option value="true">Активен</option>
                <option value="false">Нет</option>
              </select>
            </label>
            <label className="admin-filter">
              <span>Блокировка</span>
              <select
                value={blocked}
                onChange={(event) => {
                  setBlocked(event.target.value as BooleanFilter);
                  setPage(0);
                }}
              >
                <option value="ALL">Все</option>
                <option value="true">Заблокирован</option>
                <option value="false">Не заблокирован</option>
              </select>
            </label>
            <label className="admin-filter">
              <span>Долг</span>
              <select
                value={debtor}
                onChange={(event) => {
                  setDebtor(event.target.value as BooleanFilter);
                  setPage(0);
                }}
              >
                <option value="ALL">Все</option>
                <option value="true">Есть долг</option>
                <option value="false">Без долга</option>
              </select>
            </label>
            <label className="admin-filter">
              <span>Сортировка</span>
              <select
                value={sort}
                onChange={(event) => {
                  setSort(event.target.value as SortOption);
                  setPage(0);
                }}
              >
                <option value="helpCoins-desc">Баланс ↓</option>
                <option value="helpCoins-asc">Баланс ↑</option>
                <option value="debtCount-desc">Долг ↓</option>
                <option value="createdAt-desc">Сначала новые</option>
                <option value="name-asc">Имя А–Я</option>
              </select>
            </label>
          </div>

          <div className="admin-toolbar-actions">
            <button
              type="button"
              className="admin-button admin-button--ghost admin-button--compact"
              onClick={resetFilters}
              disabled={!filtersActive}
            >
              <FilterX size={15} aria-hidden="true" />
              Сбросить
            </button>
            <button
              type="button"
              className="admin-button admin-button--secondary admin-button--compact"
              onClick={() => setReloadKey((value) => value + 1)}
              disabled={loading}
            >
              <RefreshCw size={15} aria-hidden="true" />
              Обновить
            </button>
            <button type="submit" className="admin-button admin-button--primary admin-button--compact">
              <Search size={15} aria-hidden="true" />
              Применить
            </button>
          </div>
        </div>

        <div className="admin-toolbar-meta">
          <span>
            Найдено <strong>{formatNumber(data?.totalElements ?? 0)}</strong> кошельков
          </span>
          {data && (
            <span>
              Страница {totalPages === 0 ? 0 : data.number + 1} из {totalPages}
            </span>
          )}
        </div>
      </form>

      {loading ? (
        <div className="admin-section">
          <AdminLoadingBlock rows={6} />
        </div>
      ) : error ? (
        <AdminErrorState
          title="Кошельки недоступны"
          message={error}
          onRetry={() => setReloadKey((value) => value + 1)}
        />
      ) : accounts.length === 0 ? (
        <AdminEmptyState
          title="Кошельков не найдено"
          description={
            filtersActive
              ? 'По выбранным фильтрам сервер вернул пустой список.'
              : 'В системе нет активных пользователей.'
          }
          action={
            filtersActive ? (
              <button type="button" className="admin-button admin-button--secondary" onClick={resetFilters}>
                <FilterX size={16} aria-hidden="true" />
                Сбросить фильтры
              </button>
            ) : undefined
          }
        />
      ) : (
        <>
          <section
            className="admin-section admin-section--flush"
            aria-labelledby="admin-wallet-table-title"
          >
            <div className="admin-table-heading">
              <h2 id="admin-wallet-table-title" className="admin-visually-hidden">
                Кошельки
              </h2>
              <span className="admin-table-hint">
                Страница {data ? data.number + 1 : 1} из {Math.max(1, totalPages)}
              </span>
            </div>

            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th scope="col">Пользователь</th>
                    <th scope="col">Баланс</th>
                    <th scope="col" className="admin-col-optional">Долг</th>
                    <th scope="col" className="admin-col-optional">VIP</th>
                    <th scope="col" className="admin-col-optional">Блокировка</th>
                    <th scope="col" className="admin-col-wide">Создан</th>
                    <th scope="col">
                      <span className="admin-visually-hidden">Действия</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <tr key={account.userId}>
                      <td>
                        <div className="admin-user-cell">
                          <div>
                            <Link to={`/admin/users/${account.userId}`} className="admin-user-link">
                              {account.name}
                            </Link>
                            <span>{account.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="admin-wallet-balance">
                          <Coins size={14} aria-hidden="true" />
                          {formatNumber(account.balance)}
                        </span>
                      </td>
                      <td className="admin-col-optional">{formatNumber(account.debtCount)}</td>
                      <td className="admin-col-optional">
                        {account.vipActive ? (
                          <span className="admin-badge admin-badge--lime">
                            <Sparkles size={11} aria-hidden="true" />
                            до {formatDateTime(account.vipUntil)}
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="admin-col-optional">
                        {account.blocked ? (
                          <span className="admin-badge admin-badge--danger">
                            <Ban size={11} aria-hidden="true" />
                            до {formatDateTime(account.blockedUntil)}
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="admin-col-wide">{formatDateTime(account.createdAt)}</td>
                      <td>
                        <Link
                          to={`/admin/users/${account.userId}`}
                          className="admin-icon-btn"
                          aria-label={`Открыть кошелёк пользователя ${account.name}`}
                          title="Открыть пользователя"
                        >
                          <ChevronRight size={17} aria-hidden="true" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <Pagination
            page={(data?.number ?? 0) + 1}
            totalPages={Math.max(1, totalPages)}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            style={{ marginTop: '18px' }}
          />
        </>
      )}
    </div>
  );
};
