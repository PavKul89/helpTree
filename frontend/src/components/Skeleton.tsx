import React from 'react';
import { Card } from './Card';

interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  borderRadius?: string | number;
}

export const Skeleton: React.FC<SkeletonProps> = ({ 
  width = '100%', 
  height = 20, 
  borderRadius = 4 
}) => {
  return (
    <div 
      className="skeleton"
      style={{
        width,
        height,
        borderRadius,
      }}
    />
  );
};

export const PostCardSkeleton: React.FC = () => {
  return (
    <div style={styles.card}>
      <Skeleton width="100%" height={200} borderRadius={16} />
      <div style={styles.content}>
        <div style={styles.header}>
          <Skeleton width={80} height={24} borderRadius={12} />
          <Skeleton width={24} height={24} borderRadius={4} />
        </div>
        <Skeleton width="40%" height={24} />
        <Skeleton width="100%" height={16} />
        <Skeleton width="85%" height={16} />
        <Skeleton width="60%" height={16} />
        <div style={styles.footer}>
          <div style={styles.avatarRow}>
            <Skeleton width={32} height={32} borderRadius="50%" />
            <Skeleton width={100} height={14} />
          </div>
        </div>
      </div>
    </div>
  );
};

export const PostDetailSkeleton: React.FC = () => {
  return (
    <Card style={styles.detailCard}>
      <div style={styles.detailHeader}>
        <Skeleton width={100} height={32} borderRadius={16} />
        <Skeleton width={100} height={32} borderRadius={16} />
      </div>
      <Skeleton width="70%" height={36} />
      <Skeleton width="100%" height={20} />
      <Skeleton width="90%" height={20} />
      <Skeleton width="80%" height={20} />
      <Skeleton width="100%" height={300} borderRadius={16} />
      <div style={styles.detailActions}>
        <Skeleton width={160} height={44} borderRadius={12} />
        <Skeleton width={120} height={44} borderRadius={12} />
        <Skeleton width={120} height={44} borderRadius={12} />
      </div>
    </Card>
  );
};

const styles: Record<string, React.CSSProperties> = {
  card: {
    background: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 16,
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
  },
  content: {
    padding: 16,
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  footer: {
    marginTop: 8,
    paddingTop: 12,
    borderTop: '1px solid rgba(255,255,255,0.1)',
  },
  avatarRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  detailCard: {
    padding: 24,
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  },
  detailHeader: {
    display: 'flex',
    gap: 12,
  },
  detailActions: {
    display: 'flex',
    gap: 12,
    paddingTop: 16,
    borderTop: '1px solid rgba(255,255,255,0.1)',
  },
};
