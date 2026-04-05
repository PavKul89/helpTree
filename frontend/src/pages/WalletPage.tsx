import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { walletApi, WalletDto, CoinTransactionDto } from '../api/walletApi';
import { theme } from '../theme';
import { Coins, ArrowUpRight, ArrowDownLeft, Gift, Star, Calendar, TrendingUp, History, Gift as GiftIcon, Sparkles, Crown, Zap, Unlock, CreditCard, Palette, Award } from 'lucide-react';
import { Button } from '../components';
import { useToast } from '../components/Toast';
import { getRelativeTime } from '../utils/dateUtils';

const getTransactionIcon = (type: string) => {
  switch (type) {
    case 'HELP_GIVEN':
      return <Gift size={18} color="#10B981" />;
    case 'HELP_RECEIVED':
      return <ArrowDownLeft size={18} color="#06b6d4" />;
    case 'REVIEW_BONUS':
      return <Star size={18} color="#fbbf24" />;
    case 'DAILY_LOGIN':
      return <Calendar size={18} color="#a78bfa" />;
    case 'ADMIN_BONUS':
      return <TrendingUp size={18} color="#f472b6" />;
    case 'FIRST_HELP':
      return <Award size={18} color="#f97316" />;
    case 'NICKNAME_COLOR':
      return <Palette size={18} color="#ec4899" />;
    default:
      return <Coins size={18} color="#9ca3af" />;
  }
};

const getTransactionLabel = (type: string) => {
  switch (type) {
    case 'HELP_GIVEN':
      return 'Помощь оказана';
    case 'HELP_RECEIVED':
      return 'Помощь получена';
    case 'REVIEW_BONUS':
      return 'Бонус за отзыв';
    case 'DAILY_LOGIN':
      return 'Ежедневный вход';
    case 'ADMIN_BONUS':
      return 'Бонус от админа';
    case 'POST_BOOST':
      return 'Поднятие поста';
    case 'VIP_STATUS':
      return 'VIP статус';
    case 'ACCOUNT_UNBLOCK':
      return 'Разблокировка';
    case 'GIFT_SENT':
      return 'Подарок отправлен';
    case 'GIFT_RECEIVED':
      return 'Подарок получен';
    case 'FIRST_HELP':
      return 'Бонус новичка';
    case 'NICKNAME_COLOR':
      return 'Смена цвета ника';
    default:
      return 'Транзакция';
  }
};

const getTransactionColor = (type: string) => {
  switch (type) {
    case 'HELP_GIVEN':
    case 'HELP_RECEIVED':
    case 'REVIEW_BONUS':
    case 'DAILY_LOGIN':
    case 'ADMIN_BONUS':
    case 'GIFT_RECEIVED':
    case 'FIRST_HELP':
      return '#10B981';
    case 'POST_BOOST':
    case 'VIP_STATUS':
    case 'ACCOUNT_UNBLOCK':
    case 'GIFT_SENT':
    case 'NICKNAME_COLOR':
      return '#f87171';
    default:
      return '#9ca3af';
  }
};

export const WalletPage: React.FC = () => {
  const { user } = useAuth();
  const [wallet, setWallet] = useState<WalletDto | null>(null);
  const [transactions, setTransactions] = useState<CoinTransactionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [spending, setSpending] = useState<string | null>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const { showToast } = useToast();

  const COLORS = [
    { name: 'Красный', value: '#ef4444' },
    { name: 'Оранжевый', value: '#f97316' },
    { name: 'Жёлтый', value: '#eab308' },
    { name: 'Зелёный', value: '#22c55e' },
    { name: 'Голубой', value: '#06b6d4' },
    { name: 'Синий', value: '#3b82f6' },
    { name: 'Фиолетовый', value: '#a855f7' },
    { name: 'Розовый', value: '#ec4899' },
    { name: 'Белый', value: '#ffffff' },
  ];

  const loadWallet = async () => {
    if (!user) return;
    try {
      const data = await walletApi.getWallet(user.id);
      setWallet(data);
    } catch (err) {
      console.error('Error loading wallet:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadTransactions = async () => {
    if (!user) return;
    try {
      const data = await walletApi.getTransactions(user.id, 0, 50);
      setTransactions(data.content);
      
      const lastTx = data.content[0];
      if (lastTx && lastTx.type === 'DAILY_LOGIN') {
        const txTime = new Date(lastTx.createdAt).getTime();
        const now = Date.now();
        if (now - txTime < 60000) {
          showToast('+1 HC за ежедневный вход!', 'success');
        }
      }
    } catch (err) {
      console.error('Error loading transactions:', err);
    }
  };

  const handleChangeColor = async (color: string) => {
    if (!user || !wallet) return;
    if (wallet.balance < 20) {
      showToast('Недостаточно HC. Нужно 20, у вас ' + wallet.balance, 'error');
      return;
    }
    setSpending('COLOR');
    try {
      await walletApi.changeNicknameColor(user.id, color);
      showToast('Цвет ника изменён!', 'success');
      loadWallet();
      loadTransactions();
      setShowColorPicker(false);
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Ошибка при смене цвета', 'error');
    } finally {
      setSpending(null);
    }
  };

  const styles: Record<string, React.CSSProperties> = {
    container: {
      padding: '24px',
      maxWidth: '800px',
      margin: '0 auto',
    },
    loading: {
      textAlign: 'center',
      color: theme.colors.textSecondary,
      padding: '40px',
    },
    header: {
      marginBottom: '24px',
    },
    title: {
      color: theme.colors.text,
      fontSize: '28px',
      fontWeight: 700,
      margin: 0,
    },
    balanceCard: {
      background: `linear-gradient(135deg, ${theme.colors.primaryDark} 0%, ${theme.colors.primary} 50%, ${theme.colors.accentDark} 100%)`,
      borderRadius: theme.borderRadius.xl,
      padding: '32px',
      marginBottom: '24px',
      boxShadow: '0 10px 40px rgba(6, 182, 212, 0.3)',
      textAlign: 'center' as const,
    },
    balanceLabel: {
      color: 'rgba(255,255,255,0.8)',
      fontSize: '14px',
      textTransform: 'uppercase' as const,
      letterSpacing: '2px',
      marginBottom: '8px',
    },
    balanceAmount: {
      color: '#fff',
      fontSize: '56px',
      fontWeight: 700,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '12px',
    },
    balanceCoins: {
      color: '#fbbf24',
    },
    statsRow: {
      display: 'flex',
      gap: '16px',
      marginTop: '24px',
    },
    statItem: {
      flex: 1,
      background: 'rgba(255,255,255,0.1)',
      borderRadius: theme.borderRadius.lg,
      padding: '16px',
      textAlign: 'center' as const,
    },
    statValue: {
      color: '#fff',
      fontSize: '20px',
      fontWeight: 700,
    },
    statLabel: {
      color: 'rgba(255,255,255,0.7)',
      fontSize: '12px',
      marginTop: '4px',
    },
    section: {
      background: 'rgba(255,255,255,0.05)',
      borderRadius: theme.borderRadius.lg,
      padding: '24px',
      marginBottom: '24px',
    },
    sectionTitle: {
      color: theme.colors.text,
      fontSize: '18px',
      fontWeight: 600,
      marginBottom: '16px',
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
    },
    dailyBonus: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '20px',
      background: 'linear-gradient(135deg, rgba(251, 191, 36, 0.15) 0%, rgba(251, 191, 36, 0.05) 100%)',
      borderRadius: theme.borderRadius.lg,
      border: '1px solid rgba(251, 191, 36, 0.3)',
    },
    bonusInfo: {
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
    },
    bonusText: {
      color: theme.colors.text,
      fontSize: '14px',
    },
    bonusSubtext: {
      color: theme.colors.textSecondary,
      fontSize: '12px',
      marginTop: '2px',
    },
    transactionItem: {
      display: 'flex',
      alignItems: 'center',
      padding: '16px 0',
      borderBottom: `1px solid ${theme.colors.border}`,
    },
    transactionIcon: {
      width: '40px',
      height: '40px',
      borderRadius: '50%',
      background: 'rgba(255,255,255,0.1)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      marginRight: '12px',
    },
    transactionInfo: {
      flex: 1,
    },
    transactionTitle: {
      color: theme.colors.text,
      fontSize: '14px',
      fontWeight: 500,
    },
    transactionDesc: {
      color: theme.colors.textSecondary,
      fontSize: '12px',
      marginTop: '2px',
    },
    transactionAmount: {
      fontSize: '16px',
      fontWeight: 700,
      display: 'flex',
      alignItems: 'center',
      gap: '4px',
    },
    transactionTime: {
      color: theme.colors.textMuted,
      fontSize: '11px',
      marginTop: '2px',
      textAlign: 'right' as const,
    },
    emptyState: {
      textAlign: 'center',
      padding: '40px',
      color: theme.colors.textSecondary,
    },
    howToEarn: {
      display: 'grid',
      gridTemplateColumns: 'repeat(2, 1fr)',
      gap: '12px',
    },
    earnItem: {
      display: 'flex',
      alignItems: 'center',
      gap: '10px',
      padding: '12px',
      background: 'rgba(255,255,255,0.05)',
      borderRadius: theme.borderRadius.md,
    },
    earnIcon: {
      width: '32px',
      height: '32px',
      borderRadius: '8px',
      background: 'rgba(16, 185, 129, 0.2)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    earnText: {
      color: theme.colors.text,
      fontSize: '13px',
    },
    earnAmount: {
      color: '#10B981',
      fontWeight: 700,
      marginLeft: 'auto',
    },
    spendSection: {
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
      gap: '12px',
    },
    spendItem: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: '20px 16px',
      background: 'rgba(255,255,255,0.05)',
      borderRadius: theme.borderRadius.lg,
      border: '1px solid rgba(255,255,255,0.1)',
      cursor: 'pointer',
      transition: 'all 0.2s',
    },
    spendItemDisabled: {
      opacity: 0.5,
      cursor: 'not-allowed',
    },
    spendIcon: {
      width: '48px',
      height: '48px',
      borderRadius: '12px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: '12px',
    },
    spendTitle: {
      color: theme.colors.text,
      fontSize: '14px',
      fontWeight: 600,
      textAlign: 'center',
      marginBottom: '4px',
    },
    spendDesc: {
      color: theme.colors.textSecondary,
      fontSize: '11px',
      textAlign: 'center',
      marginBottom: '12px',
    },
    spendPrice: {
      color: '#fbbf24',
      fontWeight: 700,
      fontSize: '16px',
    },
  };

  useEffect(() => {
    if (user) {
      loadWallet();
      loadTransactions();
    }
  }, [user]);

  const handleSpend = async (type: 'VIP' | 'BOOST' | 'UNBLOCK') => {
    if (!user || !wallet) return;
    
    const prices = { VIP: 50, BOOST: 5, UNBLOCK: 100 };
    const price = prices[type];
    const names = { VIP: 'VIP статус', BOOST: 'поднятие поста', UNBLOCK: 'разблокировку' };
    
    if (wallet.balance < price) {
      showToast(`Недостаточно HC. Нужно ${price}, у вас ${wallet.balance}`, 'error');
      return;
    }
    
    setSpending(type);
    try {
      await walletApi.spend(user.id, type.toUpperCase(), names[type]);
      showToast(`Вы потратили ${price} HC на ${names[type]}`, 'success');
      loadWallet();
      loadTransactions();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Ошибка при покупке', 'error');
    } finally {
      setSpending(null);
    }
  };

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.loading}>Загрузка...</div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>Кошелёк</h1>
      </div>

      <div style={styles.balanceCard}>
        <div style={styles.balanceLabel}>Ваш баланс</div>
        <div style={styles.balanceAmount}>
          <Coins size={48} color="#fbbf24" />
          <span>{wallet?.balance || 0}</span>
          <span style={styles.balanceCoins}>HC</span>
        </div>
        <div style={styles.statsRow}>
          <div style={styles.statItem}>
            <div style={styles.statValue}>+{wallet?.totalEarned || 0}</div>
            <div style={styles.statLabel}>Заработано</div>
          </div>
          <div style={styles.statItem}>
            <div style={styles.statValue}>-{wallet?.totalSpent || 0}</div>
            <div style={styles.statLabel}>Потрачено</div>
          </div>
        </div>
      </div>

      <div style={styles.section}>
        <div style={styles.sectionTitle}>
          <TrendingUp size={20} color={theme.colors.accent} />
          Как заработать
        </div>
        <div style={styles.howToEarn}>
          <div style={styles.earnItem}>
            <div style={styles.earnIcon}>
              <Gift size={16} color="#10B981" />
            </div>
            <div style={styles.earnText}>Помочь другому</div>
            <div style={styles.earnAmount}>+10</div>
          </div>
          <div style={styles.earnItem}>
            <div style={styles.earnIcon}>
              <Award size={16} color="#f97316" />
            </div>
            <div style={styles.earnText}>Первая помощь новичка</div>
            <div style={styles.earnAmount}>+3</div>
          </div>
          <div style={styles.earnItem}>
            <div style={styles.earnIcon}>
              <ArrowDownLeft size={16} color="#06b6d4" />
            </div>
            <div style={styles.earnText}>Получить помощь</div>
            <div style={styles.earnAmount}>+2</div>
          </div>
          <div style={styles.earnItem}>
            <div style={styles.earnIcon}>
              <Star size={16} color="#fbbf24" />
            </div>
            <div style={styles.earnText}>Оставить отзыв</div>
            <div style={styles.earnAmount}>+2</div>
          </div>
          <div style={styles.earnItem}>
            <div style={styles.earnIcon}>
              <Calendar size={16} color="#a78bfa" />
            </div>
            <div style={styles.earnText}>Ежедневный вход</div>
            <div style={styles.earnAmount}>+1</div>
          </div>
        </div>
      </div>

      <div style={styles.section}>
        <div style={styles.sectionTitle}>
          <CreditCard size={20} color={theme.colors.accent} />
          Потратить HC
        </div>
        <div style={styles.spendSection}>
          <div style={styles.spendItem} onClick={() => handleSpend('VIP')}>
            <div style={{...styles.spendIcon, background: 'linear-gradient(135deg, rgba(251, 191, 36, 0.2) 0%, rgba(251, 191, 36, 0.1) 100%)'}}>
              <Crown size={24} color="#fbbf24" />
            </div>
            <div style={styles.spendTitle}>VIP статус</div>
            <div style={styles.spendDesc}>Золотая рамка профиля на месяц</div>
            <div style={styles.spendPrice}>50 HC</div>
          </div>
          
          <div style={{...styles.spendItem}} onClick={() => setShowColorPicker(!showColorPicker)}>
            <div style={{...styles.spendIcon, background: 'linear-gradient(135deg, rgba(236, 72, 153, 0.2) 0%, rgba(236, 72, 153, 0.1) 100%)'}}>
              <Palette size={24} color="#ec4899" />
            </div>
            <div style={styles.spendTitle}>Цвет ника</div>
            <div style={styles.spendDesc}>Выбрать цвет для ника</div>
            <div style={styles.spendPrice}>20 HC</div>
          </div>
          
          <div style={styles.spendItem} onClick={() => handleSpend('BOOST')}>
            <div style={{...styles.spendIcon, background: 'linear-gradient(135deg, rgba(6, 182, 212, 0.2) 0%, rgba(6, 182, 212, 0.1) 100%)'}}>
              <Zap size={24} color="#06b6d4" />
            </div>
            <div style={styles.spendTitle}>Поднять пост</div>
            <div style={styles.spendDesc}>Пост в топе на 24 часа</div>
            <div style={styles.spendPrice}>5 HC</div>
          </div>
          
          <div style={styles.spendItem} onClick={() => handleSpend('UNBLOCK')}>
            <div style={{...styles.spendIcon, background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.2) 0%, rgba(239, 68, 68, 0.1) 100%)'}}>
              <Unlock size={24} color="#ef4444" />
            </div>
            <div style={styles.spendTitle}>Разблокировка</div>
            <div style={styles.spendDesc}>Снять блокировку за долг</div>
            <div style={styles.spendPrice}>100 HC</div>
          </div>
        </div>
      </div>

      {showColorPicker && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>
            <Palette size={20} color={theme.colors.accent} />
            Выберите цвет ника
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', justifyContent: 'center' }}>
            {COLORS.map((color) => (
              <button
                key={color.value}
                onClick={() => handleChangeColor(color.value)}
                disabled={spending === 'COLOR'}
                style={{
                  width: '60px',
                  height: '60px',
                  borderRadius: '12px',
                  background: color.value,
                  border: user?.nicknameColor === color.value ? '3px solid #fff' : '3px solid transparent',
                  cursor: spending === 'COLOR' ? 'not-allowed' : 'pointer',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                  opacity: spending === 'COLOR' ? 0.5 : 1,
                }}
                title={color.name}
              />
            ))}
          </div>
          <div style={{ textAlign: 'center', marginTop: '16px', color: theme.colors.textSecondary, fontSize: '14px' }}>
            {spending === 'COLOR' ? 'Смена цвета...' : 'Нажмите на цвет для покупки'}
          </div>
        </div>
      )}

      <div style={styles.section}>
        <div style={styles.sectionTitle}>
          <History size={20} color={theme.colors.accent} />
          История транзакций
        </div>
        {transactions.length === 0 ? (
          <div style={styles.emptyState}>
            Пока нет транзакций
          </div>
        ) : (
          transactions.map((tx) => (
            <div key={tx.id} style={styles.transactionItem}>
              <div style={styles.transactionIcon}>
                {getTransactionIcon(tx.type)}
              </div>
              <div style={styles.transactionInfo}>
                <div style={styles.transactionTitle}>{getTransactionLabel(tx.type)}</div>
                {tx.description && (
                  <div style={styles.transactionDesc}>{tx.description}</div>
                )}
              </div>
              <div>
                <div style={{
                  ...styles.transactionAmount,
                  color: getTransactionColor(tx.type),
                }}>
                  {tx.amount > 0 ? '+' : ''}{tx.amount}
                </div>
                <div style={styles.transactionTime}>
                  {getRelativeTime(tx.createdAt)}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
