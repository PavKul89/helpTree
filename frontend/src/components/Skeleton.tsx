import React from 'react';

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
      <div style={styles.header}>
        <Skeleton width={80} height={24} borderRadius={12} />
        <Skeleton width={60} height={24} borderRadius={12} />
      </div>
      <Skeleton width="80%" height={28} />
      <Skeleton width="100%" height={16} />
      <Skeleton width="60%" height={16} />
      <div style={styles.footer}>
        <div style={styles.avatar}>
          <Skeleton width={32} height={32} borderRadius="50%" />
        </div>
        <Skeleton width={100} height={14} />
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  card: {
    background: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 12,
    padding: 16,
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
  },
  footer: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    marginTop: 8,
  },
  avatar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
};
