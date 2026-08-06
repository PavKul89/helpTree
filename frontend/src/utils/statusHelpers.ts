export const getStatusColor = (status: string, type: 'post' | 'help' = 'post'): string => {
  const postColors: Record<string, string> = {
    OPEN: '#10B981',
    IN_PROGRESS: '#38bdf8',
    COMPLETED: '#F59E0B',
    CANCELLED: '#EF4444',
  };

  const helpColors: Record<string, string> = {
    PENDING: '#F59E0B',
    ACCEPTED: '#38bdf8',
    COMPLETED: '#10B981',
    CONFIRMED: '#059669',
    CANCELLED: '#EF4444',
  };

  const colors = type === 'help' ? helpColors : postColors;
  return colors[status] || '#6B7280';
};

export const getStatusLabel = (status: string, type: 'post' | 'help' = 'post'): string => {
  const postLabels: Record<string, string> = {
    OPEN: 'Открыт',
    IN_PROGRESS: 'В работе',
    COMPLETED: 'Завершён',
    CANCELLED: 'Отменён',
  };

  const helpLabels: Record<string, string> = {
    PENDING: 'Ожидает',
    ACCEPTED: 'Принят',
    COMPLETED: 'Завершён',
    CONFIRMED: 'Подтверждён',
    CANCELLED: 'Отменён',
  };

  const labels = type === 'help' ? helpLabels : postLabels;
  return labels[status] || status;
};
