import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { helpApi } from '../api/helpApi';
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

const DEPTH_COLORS = [
  '#065f46', // Level 0 - root
  '#0e7490', // Level 1
  '#7c3aed', // Level 2
  '#db2777', // Level 3
  '#dc2626', // Level 4+
];

export const HelpGraphPage = () => {
  const [graph, setGraph] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [selectedNode, setSelectedNode] = useState<any>(null);
  const [hoveredNode, setHoveredNode] = useState<any>(null);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [highlightedPath, setHighlightedPath] = useState<{id: number, name: string}[]>([]);
  const [pathTree, setPathTree] = useState<TreeNode | null>(null);
  const nodeRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  useEffect(() => {
    authApi.getCurrentUser()
      .then(user => {
        setCurrentUser(user);
        return helpApi.getHelpGraph(user.id);
      })
      .then(setGraph)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const getSubtreeCount = (node: TreeNode): number => {
    let count = 0;
    const traverse = (n: TreeNode) => {
      count += n.children.length;
      n.children.forEach(traverse);
    };
    traverse(node);
    return count;
  };

  const getPathToNode = (nodeId: number, root: TreeNode): {id: number, name: string}[] => {
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
  };

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

    const findRoot = () => {
      const fromIds = new Set(graph.edges.map((e: any) => e.fromUserId));
      const toIds = new Set(graph.edges.map((e: any) => e.toUserId));
      
      for (const id of Array.from(fromIds)) {
        if (!toIds.has(id)) return id;
      }
      return graph.edges[0]?.fromUserId;
    };

    const rootId = findRoot();
    if (!rootId) return null;

    const buildChildren = (parentId: number, depth: number, visited: Set<number>): TreeNode[] => {
      const edges = edgesByFrom.get(parentId) || [];
      return edges.map(edge => {
        if (visited.has(edge.toUserId)) return null;
        const child = nodeMap.get(edge.toUserId);
        if (!child) return null;
        
        const childVisited = new Set(visited);
        childVisited.add(edge.toUserId);
        
        return {
          id: child.id,
          name: child.name,
          avatarUrl: child.avatarUrl,
          helpedCount: child.helpedCount,
          depth,
          postTitle: edge.postTitle,
          helpDate: edge.confirmedAt,
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

  const tree = buildTree();

  const getLayout = (node: TreeNode, x: number, y: number, level: number, positions: Map<number, {x: number, y: number, node: TreeNode}>) => {
    positions.set(node.id, { x, y, node });
    
    if (node.children.length === 0) return;
    
    const childCount = node.children.length;
    const radius = 180 + level * 40;
    const angleSpread = Math.PI * 0.8;
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
    getLayout(node, 400, 100, 0, positions);
    
    const posArray = Array.from(positions.values());
    const minX = posArray.reduce((min, p) => Math.min(min, p.x), Infinity);
    const maxX = posArray.reduce((max, p) => Math.max(max, p.x), -Infinity);
    const minY = posArray.reduce((min, p) => Math.min(min, p.y), Infinity);
    const maxY = posArray.reduce((max, p) => Math.max(max, p.y), -Infinity);
    const svgWidth = Math.max(800, maxX - minX + 120);
    const svgHeight = Math.max(500, maxY - minY + 120);
    const offsetX = -minX + 60;
    const offsetY = -minY + 60;

    const getPos = (id: number) => {
      const p = positions.get(id);
      return p ? { x: p.x + offsetX, y: p.y + offsetY, node: p.node } : null;
    };

    const getColor = (depth: number) => DEPTH_COLORS[Math.min(depth, DEPTH_COLORS.length - 1)];

    const isHighlighted = (id: number) => highlightedPath.some(p => p.id === id);
    const isParentHighlighted = (id: number) => {
      const idx = highlightedPath.findIndex(p => p.id === id);
      return idx > 0 && highlightedPath[idx - 1].id === selectedNode?.id;
    };

    const renderEdges = () => {
      const edges: React.ReactElement[] = [];
      
      const traverse = (n: TreeNode) => {
        const from = getPos(n.id);
        if (!from) return;
        
        n.children.forEach(child => {
          const to = getPos(child.id);
          if (!to) return;
          
          const isPath = highlightedPath.some(p => p.id === n.id) && highlightedPath.some(p => p.id === child.id);
          const strokeColor = isPath ? '#fbbf24' : 'rgba(34, 211, 238, 0.4)';
          const strokeWidth = isPath ? 3 : 2;
          
          const midY = (from.y + to.y) / 2;
          edges.push(
            <g key={`${n.id}-${child.id}`} style={{ 
              transition: 'all 0.3s ease',
              opacity: highlightedPath.length > 0 && !isPath ? 0.2 : 1 
            }}>
              <path
                d={`M ${from.x} ${from.y + 22} Q ${from.x} ${midY} ${to.x} ${to.y - 22}`}
                stroke={strokeColor}
                strokeWidth={strokeWidth}
                fill="none"
              />
              <polygon
                points={`${to.x},${to.y - 22} ${to.x - 6},${to.y - 32} ${to.x + 6},${to.y - 32}`}
                fill={strokeColor}
              />
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
      
      const traverse = (n: TreeNode) => {
        const pos = getPos(n.id);
        if (!pos) return;
        
        const isSelected = selectedNode?.id === n.id;
        const isHovered = hoveredNode?.id === n.id;
        const subtreeCount = getSubtreeCount(n);
        const color = getColor(n.depth);
        const isDimmed = highlightedPath.length > 0 && !highlightedPath.some(p => p.id === n.id);

        nodes.push(
          <g 
            key={n.id}
            style={{ 
              cursor: 'pointer',
              transition: 'all 0.3s ease',
              opacity: isDimmed ? 0.3 : 1,
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
              r={isSelected ? 32 : isHovered ? 28 : 22}
              fill={isSelected ? '#fbbf24' : color}
              stroke={isSelected ? '#fff' : isHovered ? '#fff' : '#34d399'}
              strokeWidth={isSelected || isHovered ? 4 : 3}
              style={{ 
                transition: 'all 0.3s ease',
                filter: isHovered ? 'brightness(1.2)' : 'none',
              }}
            />
            <text
              x={pos.x}
              y={pos.y + 4}
              textAnchor="middle"
              fill="#fff"
              fontSize="12"
              fontWeight="bold"
            >
              {n.name.charAt(0).toUpperCase()}
            </text>
            {subtreeCount > 0 && !isSelected && (
              <text
                x={pos.x}
                y={pos.y + 40}
                textAnchor="middle"
                fill="rgba(255,255,255,0.6)"
                fontSize="10"
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
          <filter id="glow">
            <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
            <feMerge>
              <feMergeNode in="coloredBlur"/>
              <feMergeNode in="SourceGraphic"/>
            </feMerge>
          </filter>
        </defs>
        {renderEdges()}
        {renderNodes()}
      </svg>
    );
  };

  return (
    <div style={styles.container} onClick={() => { setSelectedNode(null); setHighlightedPath([]); }}>
      <Link to="/" style={styles.backLink}>← На главную</Link>
      <h1 className="page-title" style={styles.title}>🌳 Граф помощи</h1>
      
      <div style={styles.stats}>
        <div style={styles.statBox}>
          <div style={styles.statValue}>{graph.totalUsers}</div>
          <div style={styles.statLabel}>Участников</div>
        </div>
        <div style={styles.statBox}>
          <div style={styles.statValue}>{graph.totalHelps}</div>
          <div style={styles.statLabel}>Актов помощи</div>
        </div>
      </div>

      <div style={styles.graphContainer}>
        {renderTree(tree)}
        
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
              <div style={styles.pathLabel}>Полная цепочка:</div>
              <div style={styles.pathList}>
                {highlightedPath.map((pathItem, idx) => (
                  <React.Fragment key={pathItem.id}>
                    <span style={styles.pathItem}>{pathItem.name}</span>
                    {idx < highlightedPath.length - 1 && <span style={styles.pathArrow}>→</span>}
                  </React.Fragment>
                ))}
              </div>
            </div>
          )}

          <div style={styles.nodeActions}>
            <Link to={`/profile/${selectedNode.id}`} style={styles.profileLink}>
              Перейти в профиль
            </Link>
          </div>
        </div>
      )}

      <div style={styles.legend}>
        <h3 style={styles.legendTitle}>Уровни помощи:</h3>
        <div style={styles.legendItems}>
          {DEPTH_COLORS.map((color, i) => (
            <div key={i} style={styles.legendItem}>
              <div style={{ ...styles.legendDot, background: color }} />
              <span style={styles.legendText}>Уровень {i}</span>
            </div>
          ))}
        </div>
        <p style={styles.legendHint}>
          +N - сколько людей получили помощь через этого человека
        </p>
      </div>
    </div>
  );
};

const positions: any = {};

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 1200,
    margin: '0 auto',
    padding: '24px',
    minHeight: '100vh',
  },
  backLink: {
    color: theme.colors.accentLight,
    textDecoration: 'none',
    fontSize: '14px',
    display: 'inline-block',
    marginBottom: '16px',
  },
  title: {
    color: theme.colors.text,
    fontSize: '28px',
    fontWeight: 700,
    margin: '0 0 20px 0',
  },
  stats: {
    display: 'flex',
    gap: '20px',
    marginBottom: '20px',
  },
  statBox: {
    background: 'linear-gradient(135deg, #065f46 0%, #0e7490 100%)',
    padding: '16px 24px',
    borderRadius: '12px',
    textAlign: 'center',
    border: '1px solid rgba(34, 211, 238, 0.3)',
  },
  statValue: {
    color: '#fff',
    fontSize: '28px',
    fontWeight: 700,
  },
  statLabel: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: '13px',
    marginTop: '4px',
  },
  graphContainer: {
    background: 'linear-gradient(180deg, rgba(2, 44, 34, 0.9) 0%, rgba(6, 78, 59, 0.9) 100%)',
    borderRadius: '16px',
    padding: '20px',
    border: '1px solid rgba(34, 211, 238, 0.2)',
    marginBottom: '20px',
    position: 'relative',
    minHeight: '500px',
  },
  svg: {
    width: '100%',
    minWidth: '800px',
    height: 'auto',
  },
  tooltip: {
    position: 'absolute',
    top: '20px',
    right: '20px',
    background: 'rgba(0,0,0,0.8)',
    padding: '12px 16px',
    borderRadius: '8px',
    border: '1px solid rgba(34, 211, 238, 0.5)',
    zIndex: 100,
  },
  tooltipName: {
    color: '#fff',
    fontSize: '16px',
    fontWeight: 600,
  },
  tooltipStat: {
    color: '#fbbf24',
    fontSize: '13px',
    marginTop: '4px',
  },
  tooltipSubtree: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: '12px',
    marginTop: '2px',
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
    borderRadius: '12px',
    padding: '20px',
    border: '1px solid rgba(34, 211, 238, 0.3)',
    marginBottom: '20px',
  },
  nodeHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
  nodeName: {
    color: '#fff',
    fontSize: '20px',
    margin: '0 0 8px 0',
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
    marginTop: '16px',
    padding: '12px',
    background: 'rgba(0,0,0,0.2)',
    borderRadius: '8px',
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
    marginTop: '16px',
    padding: '12px',
    background: 'rgba(251, 191, 36, 0.1)',
    borderRadius: '8px',
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
    gap: '4px',
  },
  pathItem: {
    color: '#fff',
    fontSize: '13px',
    fontWeight: 500,
  },
  pathArrow: {
    color: '#fbbf24',
    fontSize: '14px',
  },
  nodeActions: {
    marginTop: '16px',
    display: 'flex',
    gap: '12px',
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
    borderRadius: '8px',
  },
  legendTitle: {
    color: theme.colors.text,
    fontSize: '16px',
    margin: '0 0 12px 0',
  },
  legendItems: {
    display: 'flex',
    gap: '16px',
    flexWrap: 'wrap',
  },
  legendItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  legendDot: {
    width: '16px',
    height: '16px',
    borderRadius: '50%',
  },
  legendText: {
    color: theme.colors.textMuted,
    fontSize: '13px',
  },
  legendHint: {
    color: theme.colors.textMuted,
    fontSize: '12px',
    marginTop: '12px',
  },
};