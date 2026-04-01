import React, { useEffect, useState, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { helpApi, HelpStats } from '../api/helpApi';
import { authApi } from '../api/authApi';
import { Spinner, Avatar } from '../components';
import { theme } from '../theme';
import { getRelativeTime } from '../utils/dateUtils';

interface TreeNode {
  id: number;
  name: string;
  avatarUrl?: string;
  helpedCount: number;
  children: TreeNode[];
  depth: number;
  postTitle?: string;
  helpDate?: string;
}

interface EdgeData {
  postTitle?: string;
  helpDate?: string;
}

const DEPTH_COLORS = [
  '#065f46',
  '#0e7490',
  '#7c3aed',
  '#db2777',
  '#dc2626',
];

const DEPTH_BORDER = [
  '#34d399',
  '#22d3ee',
  '#a78bfa',
  '#f472b6',
  '#f87171',
];

export const HelpGraphPage = () => {
  const [graph, setGraph] = useState<any>(null);
  const [stats, setStats] = useState<HelpStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [showStats, setShowStats] = useState(false);
  const [selectedNode, setSelectedNode] = useState<any>(null);
  const [hoveredNode, setHoveredNode] = useState<any>(null);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [highlightedPath, setHighlightedPath] = useState<{id: number, name: string}[]>([]);
  const [animatedNodes, setAnimatedNodes] = useState<Set<number>>(new Set());
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    authApi.getCurrentUser()
      .then(user => {
        setCurrentUser(user);
        return helpApi.getHelpGraph(user.id);
      })
      .then(setGraph)
      .catch(console.error)
      .finally(() => setLoading(false));
    
    helpApi.getHelpStats()
      .then(setStats)
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (graph && currentUser?.id) {
      const treeLocal = buildTree();
      if (treeLocal) {
        const ids = new Set<number>();
        const collectIds = (node: TreeNode) => {
          ids.add(node.id);
          node.children.forEach(collectIds);
        };
        collectIds(treeLocal);
        
        setAnimatedNodes(new Set());
        
        let delay = 0;
        ids.forEach(id => {
          setTimeout(() => {
            setAnimatedNodes(prev => {
              const newSet = new Set(prev);
              newSet.add(id);
              return newSet;
            });
          }, delay);
          delay += 60;
        });
      }
    }
  }, [graph, currentUser?.id]);

  const getSubtreeCount = useCallback((node: TreeNode): number => {
    let count = 0;
    const traverse = (n: TreeNode) => {
      count += n.children.length;
      n.children.forEach(traverse);
    };
    traverse(node);
    return count;
  }, []);

  const getPathToNode = useCallback((nodeId: number, root: TreeNode): {id: number, name: string}[] => {
    const path: {id: number, name: string}[] = [];
    const find = (n: TreeNode): boolean => {
      path.push({ id: n.id, name: n.name });
      if (n.id === nodeId) return true;
      for (const child of n.children) {
        if (find(child)) return true;
      }
      path.pop();
      return false;
    };
    find(root);
    return path;
  }, []);

  if (loading) return <Spinner message="Загрузка графа помощи..." />;

  if (!graph || graph.nodes.length === 0) {
    return (
      <div style={styles.container}>
        <Link to="/" style={styles.backLink}>← На главную</Link>
        <h1 className="page-title" style={styles.title}>Граф помощи</h1>
        <div style={styles.emptyState}>
          <div style={styles.emptyIcon}>🌳</div>
          <h2>Пока нет связей помощи</h2>
          <p>Когда пользователи будут помогать друг другу, цепочки появятся здесь.</p>
          <Link to="/">
            <button style={styles.btn}>К постам</button>
          </Link>
        </div>
      </div>
    );
  }

  const buildTree = (): TreeNode | null => {
    if (!graph.nodes.length || !graph.edges.length) return null;

    const nodeMap = new Map<number, any>();
    graph.nodes.forEach((n: any) => nodeMap.set(n.id, { ...n, children: [] }));

    const edgesByFrom = new Map<number, any[]>();
    graph.edges.forEach((e: any) => {
      const list = edgesByFrom.get(e.fromUserId) || [];
      list.push(e);
      edgesByFrom.set(e.fromUserId, list);
    });

    const rootId = currentUser?.id;
    if (!rootId || !nodeMap.has(rootId)) return null;

    const edgeDataMap = new Map<string, EdgeData>();
    graph.edges.forEach((e: any) => {
      edgeDataMap.set(`${e.fromUserId}-${e.toUserId}`, {
        postTitle: e.postTitle,
        helpDate: e.confirmedAt,
      });
    });

    const buildChildren = (parentId: number, depth: number, visited: Set<number>): TreeNode[] => {
      const edges = edgesByFrom.get(parentId) || [];
      return edges.map(edge => {
        if (visited.has(edge.toUserId)) return null;
        const child = nodeMap.get(edge.toUserId);
        if (!child) return null;
        
        const childVisited = new Set(visited);
        childVisited.add(edge.toUserId);
        
        const edgeData = edgeDataMap.get(`${parentId}-${edge.toUserId}`);
        
        return {
          id: child.id,
          name: child.name,
          avatarUrl: child.avatarUrl,
          helpedCount: child.helpedCount,
          depth,
          postTitle: edgeData?.postTitle,
          helpDate: edgeData?.helpDate,
          children: buildChildren(child.id, depth + 1, childVisited),
        };
      }).filter(Boolean) as TreeNode[];
    };

    const root = nodeMap.get(rootId);
    if (!root) return null;

    return {
      id: root.id,
      name: root.name,
      avatarUrl: root.avatarUrl,
      helpedCount: root.helpedCount,
      depth: 0,
      children: buildChildren(rootId, 1, new Set([rootId])),
    };
  };

  const getLayout = (node: TreeNode, x: number, y: number, level: number, positions: Map<number, {x: number, y: number, node: TreeNode}>) => {
    positions.set(node.id, { x, y, node });
    
    if (node.children.length === 0) return;
    
    const childCount = node.children.length;
    const radius = 200 + level * 50;
    const angleSpread = Math.min(Math.PI * 0.9, Math.PI * 0.5 + childCount * 0.15);
    const angleStep = childCount > 1 ? angleSpread / (childCount - 1) : 0;
    const startAngle = Math.PI / 2 - angleSpread / 2;
    
    node.children.forEach((child, i) => {
      const angle = startAngle + i * angleStep;
      const childX = x + radius * Math.cos(angle);
      const childY = y + radius * Math.sin(angle);
      getLayout(child, childX, childY, level + 1, positions);
    });
  };

  const renderTree = (node: TreeNode | null) => {
    if (!node) return null;

    const positions = new Map<number, {x: number, y: number, node: TreeNode}>();
    getLayout(node, 400, 120, 0, positions);
    
    const posArray = Array.from(positions.values());
    const minX = posArray.reduce((min, p) => Math.min(min, p.x), Infinity);
    const maxX = posArray.reduce((max, p) => Math.max(max, p.x), -Infinity);
    const minY = posArray.reduce((min, p) => Math.min(min, p.y), Infinity);
    const maxY = posArray.reduce((max, p) => Math.max(max, p.y), -Infinity);
    const svgWidth = Math.max(800, maxX - minX + 200);
    const svgHeight = Math.max(500, maxY - minY + 150);
    const offsetX = -minX + 100;
    const offsetY = -minY + 80;

    const getPos = (id: number) => {
      const p = positions.get(id);
      return p ? { x: p.x + offsetX, y: p.y + offsetY, node: p.node } : null;
    };

    const getColor = (depth: number) => DEPTH_COLORS[Math.min(depth, DEPTH_COLORS.length - 1)];
    const getBorder = (depth: number) => DEPTH_BORDER[Math.min(depth, DEPTH_BORDER.length - 1)];

    const renderEdges = () => {
      const edges: React.ReactElement[] = [];
      const renderedEdgeKeys = new Set<string>();
      
      const traverse = (n: TreeNode) => {
        const from = getPos(n.id);
        if (!from) return;
        
        n.children.forEach(child => {
          const edgeKey = `${n.id}-${child.id}`;
          if (renderedEdgeKeys.has(edgeKey)) return;
          renderedEdgeKeys.add(edgeKey);
          
          const to = getPos(child.id);
          if (!to) return;
          
          const isPath = highlightedPath.some(p => p.id === n.id) && highlightedPath.some(p => p.id === child.id);
          const isDimmed = highlightedPath.length > 0 && !isPath;
          const isHovered = hoveredNode?.id === child.id;
          const nodeColor = DEPTH_COLORS[Math.min(child.depth, DEPTH_COLORS.length - 1)];
          const strokeColor = isPath ? '#fbbf24' : isHovered ? '#fff' : nodeColor;
          const strokeWidth = isPath ? 3 : isHovered ? 2.5 : 2;
          
          const midX = (from.x + to.x) / 2;
          const midY = (from.y + to.y) / 2;
          
          edges.push(
            <g 
              key={`edge-${n.id}-${child.id}`} 
              style={{ 
                transition: 'opacity 0.3s ease',
                opacity: isDimmed ? 0.15 : 1 
              }}
            >
              <path
                d={`M ${from.x} ${from.y + 24} Q ${from.x} ${midY} ${to.x} ${to.y - 24}`}
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                fill="none"
                strokeLinecap="round"
                className={isPath ? 'graph-edge-highlighted' : 'graph-edge'}
              />
              {!isPath && (
                <path
                  d={`M ${from.x} ${from.y + 24} Q ${from.x} ${midY} ${to.x} ${to.y - 24}`}
                  stroke={nodeColor}
                  strokeWidth={2}
                  fill="none"
                  strokeLinecap="round"
                  strokeDasharray="6 94"
                  style={{
                    animation: 'flowRight 1.5s linear infinite',
                    opacity: 0.7,
                  }}
                />
              )}
              <polygon
                points={`${to.x},${to.y - 24} ${to.x - 5},${to.y - 34} ${to.x + 5},${to.y - 34}`}
                fill={strokeColor}
                className={isPath ? 'graph-edge-highlighted' : ''}
                style={!isPath ? { filter: `drop-shadow(0 0 6px ${nodeColor})`, animation: 'pulseGlow 2s ease-in-out infinite' } : {}}
              />
              {(isHovered || isPath) && child.postTitle && (
                <g>
                  <rect
                    x={midX - 60}
                    y={midY - 8}
                    width={120}
                    height={36}
                    rx={6}
                    fill="rgba(0,0,0,0.85)"
                    stroke="rgba(34, 211, 238, 0.5)"
                    strokeWidth={1}
                  />
                  <text
                    x={midX}
                    y={midY + 4}
                    textAnchor="middle"
                    fill="#fff"
                    fontSize={10}
                    fontWeight={500}
                  >
                    {child.postTitle.length > 20 ? child.postTitle.substring(0, 20) + '...' : child.postTitle}
                  </text>
                  {child.helpDate && (
                    <text
                      x={midX}
                      y={midY + 16}
                      textAnchor="middle"
                      fill="rgba(255,255,255,0.6)"
                      fontSize={9}
                    >
                      {new Date(child.helpDate).toLocaleDateString('ru-RU')}
                    </text>
                  )}
                </g>
              )}
            </g>
          );
          traverse(child);
        });
      };
      
      traverse(node);
      return edges;
    };

    const renderNodes = () => {
      const nodes: React.ReactElement[] = [];
      const renderedIds = new Set<number>();
      
      const traverse = (n: TreeNode) => {
        if (renderedIds.has(n.id)) return;
        renderedIds.add(n.id);
        
        const pos = getPos(n.id);
        if (!pos) return;
        
        const isSelected = selectedNode?.id === n.id;
        const isHovered = hoveredNode?.id === n.id;
        const isAnimated = animatedNodes.has(n.id);
        const subtreeCount = getSubtreeCount(n);
        const color = getColor(n.depth);
        const border = getBorder(n.depth);
        const isDimmed = highlightedPath.length > 0 && !highlightedPath.some(p => p.id === n.id);

        nodes.push(
          <g 
            key={`node-${n.id}`}
            className="graph-node"
            style={{ 
              cursor: 'pointer',
              transition: 'opacity 0.3s ease',
              opacity: isDimmed ? 0.2 : (isAnimated ? 1 : 0),
            }}
            onClick={(e) => {
              e.stopPropagation();
              const newPath = selectedNode?.id === n.id ? [] : getPathToNode(n.id, node);
              setHighlightedPath(newPath);
              setSelectedNode(isSelected ? null : n);
            }}
            onMouseEnter={() => setHoveredNode(n)}
            onMouseLeave={() => setHoveredNode(null)}
          >
            <circle
              cx={pos.x}
              cy={pos.y}
              r={isSelected ? 36 : isHovered ? 30 : 24}
              fill={isSelected ? '#fbbf24' : color}
              stroke={isSelected ? '#fff' : isHovered ? '#fff' : border}
              strokeWidth={isSelected || isHovered ? 4 : 3}
              style={{ 
                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                filter: isHovered ? 'drop-shadow(0 0 8px rgba(34, 211, 238, 0.6))' : 
                       isAnimated && !isSelected ? 'drop-shadow(0 0 4px rgba(34, 211, 238, 0.4))' : 'none',
              }}
            />
            <text
              x={pos.x}
              y={pos.y + 5}
              textAnchor="middle"
              fill="#fff"
              fontSize="13"
              fontWeight="bold"
              style={{ pointerEvents: 'none' }}
            >
              {n.name.charAt(0).toUpperCase()}
            </text>
            {n.depth === 0 && (
              <text
                x={pos.x}
                y={pos.y - 32}
                textAnchor="middle"
                fill="#fbbf24"
                fontSize="11"
                fontWeight="600"
                style={{ pointerEvents: 'none' }}
              >
                Вы
              </text>
            )}
            {subtreeCount > 0 && !isSelected && (
              <text
                x={pos.x}
                y={pos.y + 44}
                textAnchor="middle"
                fill="rgba(255,255,255,0.6)"
                fontSize="10"
                style={{ pointerEvents: 'none' }}
              >
                +{subtreeCount}
              </text>
            )}
          </g>
        );
        
        n.children.forEach(child => traverse(child));
      };
      
      traverse(node);
      return nodes;
    };

    return (
      <svg viewBox={`0 0 ${svgWidth} ${svgHeight}`} style={styles.svg}>
        <defs>
          <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="coloredBlur"/>
            <feMerge>
              <feMergeNode in="coloredBlur"/>
              <feMergeNode in="SourceGraphic"/>
            </feMerge>
          </filter>
          <linearGradient id="edgeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="rgba(34, 211, 238, 0.5)" />
            <stop offset="100%" stopColor="rgba(34, 211, 238, 0.2)" />
          </linearGradient>
        </defs>
        {renderEdges()}
        {renderNodes()}
      </svg>
    );
  };

  return (
    <div style={styles.container} onClick={() => { setSelectedNode(null); setHighlightedPath([]); }}>
      <div style={styles.header}>
        <Link to="/" style={styles.backLink}>← На главную</Link>
        <h1 className="page-title" style={styles.title}>🌳 Граф помощи</h1>
      </div>
      
      <div style={styles.stats}>
        <div style={styles.statBox}>
          <div style={styles.statValue}>{graph.totalUsers}</div>
          <div style={styles.statLabel}>В цепочке</div>
        </div>
        <div style={styles.statBox}>
          <div style={styles.statValue}>{graph.totalHelps}</div>
          <div style={styles.statLabel}>Актов помощи</div>
        </div>
        <button 
          style={showStats ? {...styles.statsToggle, ...styles.statsToggleActive} : styles.statsToggle}
          onClick={() => setShowStats(!showStats)}
        >
          📊 Статистика
        </button>
      </div>

      {showStats && stats && (
        <div style={styles.statsPanel}>
          <div style={styles.statsSection}>
            <h3 style={styles.statsTitle}>По месяцам</h3>
            <div style={styles.chartContainer}>
              {Object.entries(stats.byMonth).map(([month, count]) => (
                <div key={month} style={styles.chartBar}>
                  <div style={{...styles.bar, height: `${Math.max(5, (count / (stats.totalHelps || 1)) * 100)}%`}} />
                  <span style={styles.barLabel}>{month}</span>
                  <span style={styles.barValue}>{count}</span>
                </div>
              ))}
            </div>
          </div>
          
          <div style={styles.statsSection}>
            <h3 style={styles.statsTitle}>По категориям</h3>
            <div style={styles.categoryList}>
              {Object.entries(stats.byCategory).sort((a, b) => b[1] - a[1]).map(([category, count]) => (
                <div key={category} style={styles.categoryItem}>
                  <span style={styles.categoryName}>{category}</span>
                  <span style={styles.categoryCount}>{count}</span>
                </div>
              ))}
            </div>
          </div>

          <div style={styles.statsSection}>
            <h3 style={styles.statsTitle}>Топ помогающих</h3>
            <div style={styles.topList}>
              {stats.topHelpers.slice(0, 5).map((helper, idx) => (
                <div key={helper.userId} style={styles.topItem}>
                  <span style={styles.topRank}>#{idx + 1}</span>
                  <span style={styles.topName}>{helper.name}</span>
                  <span style={styles.topCount}>★ {helper.helpCount}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <div style={styles.graphContainer} ref={containerRef}>
        {renderTree(graph ? buildTree() : null)}
        
        {hoveredNode && !selectedNode && (
          <div style={styles.tooltip}>
            <div style={styles.tooltipName}>{hoveredNode.name}</div>
            <div style={styles.tooltipStat}>★ Помог: {hoveredNode.helpedCount}</div>
            {getSubtreeCount(hoveredNode) > 0 && (
              <div style={styles.tooltipSubtree}>Через него: {getSubtreeCount(hoveredNode)} чел.</div>
            )}
          </div>
        )}
      </div>

      {selectedNode && (
        <div style={styles.nodeInfo} onClick={(e) => e.stopPropagation()}>
          <div style={styles.nodeHeader}>
            <Avatar name={selectedNode.name} avatarUrl={selectedNode.avatarUrl} size="large" />
            <div>
              <h3 style={styles.nodeName}>{selectedNode.name}</h3>
              <div style={styles.nodeStats}>
                <span style={styles.nodeStat}>★ Помог: {selectedNode.helpedCount}</span>
                {getSubtreeCount(selectedNode) > 0 && (
                  <span style={styles.nodeStatGreen}>→ Через него: {getSubtreeCount(selectedNode)}</span>
                )}
              </div>
            </div>
            <button 
              style={styles.closeBtn}
              onClick={() => { setSelectedNode(null); setHighlightedPath([]); }}
            >
              ✕
            </button>
          </div>
          
          {selectedNode.depth > 0 && selectedNode.postTitle && (
            <div style={styles.helpInfo}>
              <div style={styles.helpInfoLabel}>Помог с:</div>
              <div style={styles.helpInfoValue}>{selectedNode.postTitle}</div>
              {selectedNode.helpDate && (
                <div style={styles.helpInfoDate}>
                  {getRelativeTime(selectedNode.helpDate)}
                </div>
              )}
            </div>
          )}

          {highlightedPath.length > 1 && (
            <div style={styles.pathInfo}>
              <div style={styles.pathLabel}>Цепочка:</div>
              <div style={styles.pathList}>
                {highlightedPath.map((pathItem, idx) => (
                  <React.Fragment key={`path-${pathItem.id}-${idx}`}>
                    <span style={idx === highlightedPath.length - 1 ? styles.pathItemActive : styles.pathItem}>
                      {pathItem.name}
                    </span>
                    {idx < highlightedPath.length - 1 && <span style={styles.pathArrow}>→</span>}
                  </React.Fragment>
                ))}
              </div>
            </div>
          )}

          <div style={styles.nodeActions}>
            <Link to={`/profile/${selectedNode.id}`} style={styles.profileLink}>
              Перейти в профиль →
            </Link>
          </div>
        </div>
      )}

      <div style={styles.legend}>
        <h3 style={styles.legendTitle}>Уровни:</h3>
        <div style={styles.legendItems}>
          {DEPTH_COLORS.map((color, i) => (
            <div key={`legend-${i}`} style={styles.legendItem}>
              <div style={{ ...styles.legendDot, background: color, border: `2px solid ${DEPTH_BORDER[i]}` }} />
              <span style={styles.legendText}>{i === 0 ? 'Вы' : `Уровень ${i}`}</span>
            </div>
          ))}
        </div>
        <p style={styles.legendHint}>+N — сколько получили помощь через этого человека</p>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 1000,
    margin: '0 auto',
    padding: '20px',
    minHeight: '100vh',
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.3) 0%, rgba(6, 78, 59, 0.2) 100%)',
  },
  header: {
    marginBottom: '16px',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
    display: 'inline-block',
    marginBottom: '8px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
    margin: 0,
  },
  stats: {
    display: 'flex',
    gap: '16px',
    marginBottom: '16px',
  },
  statBox: {
    background: 'linear-gradient(135deg, #065f46 0%, #0e7490 100%)',
    padding: '12px 20px',
    borderRadius: '12px',
    textAlign: 'center',
    border: '1px solid rgba(34, 211, 238, 0.3)',
  },
  statValue: {
    color: '#fff',
    fontSize: '24px',
    fontWeight: 700,
  },
  statLabel: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: '12px',
    marginTop: '2px',
  },
  graphContainer: {
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.5) 0%, rgba(6, 78, 59, 0.4) 100%)',
    borderRadius: '20px',
    padding: '20px',
    border: '1px solid rgba(34, 211, 238, 0.2)',
    marginBottom: '16px',
    position: 'relative',
    minHeight: '450px',
    overflow: 'auto',
  },
  svg: {
    display: 'block',
    width: '100%',
    minWidth: '700px',
    height: 'auto',
  },
  tooltip: {
    position: 'absolute',
    top: '16px',
    right: '16px',
    background: 'rgba(0,0,0,0.85)',
    padding: '14px 18px',
    borderRadius: '12px',
    border: '1px solid rgba(34, 211, 238, 0.5)',
    zIndex: 100,
    backdropFilter: 'blur(8px)',
  },
  tooltipName: {
    color: '#fff',
    fontSize: '16px',
    fontWeight: 600,
    marginBottom: '6px',
  },
  tooltipStat: {
    color: '#fbbf24',
    fontSize: '13px',
  },
  tooltipSubtree: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: '12px',
    marginTop: '4px',
  },
  emptyState: {
    textAlign: 'center',
    padding: '60px 20px',
    color: theme.colors.text,
  },
  emptyIcon: {
    fontSize: '64px',
    marginBottom: '16px',
  },
  btn: {
    marginTop: '20px',
    padding: '12px 24px',
    background: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
    border: 'none',
    borderRadius: '8px',
    color: '#fff',
    fontSize: '16px',
    cursor: 'pointer',
  },
  nodeInfo: {
    background: 'linear-gradient(135deg, #065f46 0%, #0e7490 100%)',
    borderRadius: '16px',
    padding: '20px',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    marginBottom: '16px',
    position: 'relative',
  },
  nodeHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  closeBtn: {
    position: 'absolute',
    top: '12px',
    right: '12px',
    background: 'rgba(255,255,255,0.1)',
    border: 'none',
    borderRadius: '50%',
    width: '28px',
    height: '28px',
    color: 'rgba(255,255,255,0.7)',
    cursor: 'pointer',
    fontSize: '14px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  nodeName: {
    color: '#fff',
    fontSize: '20px',
    margin: '0 0 6px 0',
    fontWeight: 600,
  },
  nodeStats: {
    display: 'flex',
    gap: '16px',
    flexWrap: 'wrap',
  },
  nodeStat: {
    color: '#fbbf24',
    fontSize: '14px',
  },
  nodeStatGreen: {
    color: '#34d399',
    fontSize: '14px',
  },
  helpInfo: {
    marginTop: '14px',
    padding: '12px',
    background: 'rgba(0,0,0,0.2)',
    borderRadius: '10px',
  },
  helpInfoLabel: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: '12px',
    marginBottom: '4px',
  },
  helpInfoValue: {
    color: '#fff',
    fontSize: '14px',
    fontWeight: 500,
  },
  helpInfoDate: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: '12px',
    marginTop: '4px',
  },
  pathInfo: {
    marginTop: '14px',
    padding: '12px',
    background: 'rgba(251, 191, 36, 0.1)',
    borderRadius: '10px',
    border: '1px solid rgba(251, 191, 36, 0.3)',
  },
  pathLabel: {
    color: '#fbbf24',
    fontSize: '12px',
    marginBottom: '8px',
  },
  pathList: {
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '6px',
  },
  pathItem: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: '13px',
    padding: '4px 8px',
    background: 'rgba(0,0,0,0.2)',
    borderRadius: '4px',
  },
  pathItemActive: {
    color: '#fff',
    fontSize: '13px',
    fontWeight: 600,
    padding: '4px 8px',
    background: 'rgba(251, 191, 36, 0.3)',
    borderRadius: '4px',
  },
  pathArrow: {
    color: '#fbbf24',
    fontSize: '14px',
  },
  nodeActions: {
    marginTop: '14px',
  },
  profileLink: {
    display: 'inline-block',
    padding: '10px 20px',
    background: 'linear-gradient(135deg, #06b6d4 0%, #0891b2 100%)',
    borderRadius: '8px',
    color: '#fff',
    textDecoration: 'none',
    fontSize: '14px',
    fontWeight: 500,
  },
  legend: {
    background: 'rgba(0,0,0,0.2)',
    padding: '16px',
    borderRadius: '12px',
  },
  legendTitle: {
    color: theme.colors.text,
    fontSize: '14px',
    margin: '0 0 12px 0',
  },
  legendItems: {
    display: 'flex',
    gap: '14px',
    flexWrap: 'wrap',
  },
  legendItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  legendDot: {
    width: '14px',
    height: '14px',
    borderRadius: '50%',
  },
  legendText: {
    color: theme.colors.textMuted,
    fontSize: '12px',
  },
  legendHint: {
    color: theme.colors.textMuted,
    fontSize: '11px',
    marginTop: '10px',
  },
  statsToggle: {
    padding: '10px 16px',
    background: 'rgba(255,255,255,0.1)',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    borderRadius: '8px',
    color: theme.colors.text,
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 500,
  },
  statsToggleActive: {
    background: 'rgba(34, 211, 238, 0.2)',
    borderColor: '#22d3ee',
  },
  statsPanel: {
    background: 'rgba(0,0,0,0.3)',
    borderRadius: '16px',
    padding: '20px',
    marginBottom: '16px',
    border: '1px solid rgba(34, 211, 238, 0.2)',
  },
  statsSection: {
    marginBottom: '24px',
  },
  statsTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    margin: '0 0 12px 0',
    fontWeight: 600,
  },
  chartContainer: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: '4px',
    height: '120px',
    padding: '10px 0',
  },
  chartBar: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    height: '100%',
    position: 'relative',
  },
  bar: {
    width: '100%',
    background: 'linear-gradient(180deg, #22d3ee 0%, #0891b2 100%)',
    borderRadius: '4px 4px 0 0',
    marginTop: 'auto',
    minHeight: '4px',
    transition: 'height 0.3s ease',
  },
  barLabel: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: '9px',
    marginTop: '4px',
  },
  barValue: {
    color: '#22d3ee',
    fontSize: '10px',
    fontWeight: 600,
    position: 'absolute',
    top: 0,
  },
  categoryList: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '8px',
  },
  categoryItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    background: 'rgba(255,255,255,0.05)',
    padding: '8px 12px',
    borderRadius: '8px',
    border: '1px solid rgba(34, 211, 238, 0.2)',
  },
  categoryName: {
    color: theme.colors.text,
    fontSize: '13px',
  },
  categoryCount: {
    color: '#22d3ee',
    fontSize: '13px',
    fontWeight: 600,
  },
  topList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  topItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '10px 14px',
    background: 'rgba(255,255,255,0.05)',
    borderRadius: '10px',
    border: '1px solid rgba(251, 191, 36, 0.3)',
  },
  topRank: {
    color: '#fbbf24',
    fontWeight: 700,
    fontSize: '14px',
    width: '24px',
  },
  topName: {
    color: theme.colors.text,
    fontSize: '14px',
    flex: 1,
  },
  topCount: {
    color: '#fbbf24',
    fontSize: '14px',
    fontWeight: 600,
  },
};