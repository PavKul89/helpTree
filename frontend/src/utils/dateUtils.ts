export const getRelativeTime = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);
  const diffWeek = Math.floor(diffDay / 7);
  const diffMonth = Math.floor(diffDay / 30);

  if (diffSec < 60) {
    return 'только что';
  } else if (diffMin < 60) {
    return `${diffMin} мин. назад`;
  } else if (diffHour < 24) {
    return `${diffHour} ч. назад`;
  } else if (diffDay < 7) {
    return `${diffDay} дн. назад`;
  } else if (diffWeek < 4) {
    return `${diffWeek} нед. назад`;
  } else if (diffMonth < 12) {
    return `${diffMonth} мес. назад`;
  } else {
    return date.toLocaleDateString('ru-RU');
  }
};