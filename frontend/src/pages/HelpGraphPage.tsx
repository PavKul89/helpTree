import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BarChart3,
  CalendarDays,
  ChevronRight,
  CircleHelp,
  Minus,
  Plus,
  RotateCcw,
  Search,
  TreeDeciduous,
  Users,
  X,
  ZoomIn,
} from 'lucide-react';
import { helpApi, type HelpStats } from '../api/helpApi';
import { authApi } from '../api/authApi';
import type { GraphEdge, GraphNode, HelpGraph, User } from '../types';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import './HelpGraphPage.css';

interface PositionedNode extends GraphNode {
  x: number;
  y: number;
  depth: number;
}

const NODE_GAP_X = 190;
const NODE_GAP_Y = 150;
const VIEW_WIDTH = 1100;

const getNodeLayout = (nodes: GraphNode[], edges: GraphEdge[], rootId?: number): {
  nodes: PositionedNode[];
  width: number;
  height: number;
} => {
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const outgoing = new Map<number, number[]>();
  edges.forEach((edge) => {
    const children = outgoing.get(edge.fromUserId) ?? [];
    children.push(edge.toUserId);
    outgoing.set(edge.fromUserId, children);
  });

  const depthById = new Map<number, number>();
  const queue: number[] = [];
  if (rootId && nodeById.has(rootId)) {
    depthById.set(rootId, 0);
    queue.push(rootId);
  }
  for (let cursor = 0; cursor < queue.length; cursor += 1) {
    const currentId = queue[cursor];
    const nextDepth = (depthById.get(currentId) ?? 0) + 1;
    (outgoing.get(currentId) ?? []).forEach((nextId) => {
      if (nodeById.has(nextId) && !depthById.has(nextId)) {
        depthById.set(nextId, nextDepth);
        queue.push(nextId);
      }
    });
  }

  const maxDepth = Math.max(0, ...Array.from(depthById.values()));
  nodes.forEach((node) => {
    if (!depthById.has(node.id)) depthById.set(node.id, maxDepth + 1);
  });
  const layers = new Map<number, GraphNode[]>();
  nodes.forEach((node) => {
    const depth = depthById.get(node.id) ?? 0;
    const layer = layers.get(depth) ?? [];
    layer.push(node);
    layers.set(depth, layer);
  });

  const maxNodesInLayer = Math.max(1, ...Array.from(layers.values(), (layer) => layer.length));
  const width = Math.max(VIEW_WIDTH, maxNodesInLayer * NODE_GAP_X + 140);
  const height = Math.max(620, (Math.max(0, ...Array.from(layers.keys())) + 1) * NODE_GAP_Y + 120);
  const positioned: PositionedNode[] = [];

  layers.forEach((layer, depth) => {
    const spacing = width / (layer.length + 1);
    layer.forEach((node, index) => {
      positioned.push({
        ...node,
        x: spacing * (index + 1),
        y: 80 + depth * NODE_GAP_Y,
        depth,
      });
    });
  });
  return { nodes: positioned, width, height };
};

const formatDate = (date: string): string => {
  const parsed = new Date(date);
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toLocaleDateString('ru-RU', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
};

export const HelpGraphPage = () => {
  const [graph, setGraph] = useState<HelpGraph | null>(null);
  const [stats, setStats] = useState<HelpStats | null>(null);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [statsError, setStatsError] = useState(false);
  const [showStats, setShowStats] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null);
  const [selectedEdgeIndex, setSelectedEdgeIndex] = useState<number | null>(null);
  const [zoom, setZoom] = useState(1);
  const [translation, setTranslation] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const svgRef = useRef<SVGSVGElement>(null);
  const dragStart = useRef<{ x: number; y: number; originX: number; originY: number } | null>(null);
  const pointerMoved = useRef(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const user = await authApi.getCurrentUser();
      setCurrentUser(user);
      const graphData = await helpApi.getHelpGraph(user.id);
      setGraph(graphData);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }

    try {
      setStats(await helpApi.getHelpStats());
      setStatsError(false);
    } catch {
      setStatsError(true);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const layout = useMemo(
    () => getNodeLayout(graph?.nodes ?? [], graph?.edges ?? [], currentUser?.id),
    [graph, currentUser?.id]
  );
  const positionedNodesById = useMemo(
    () => new Map(layout.nodes.map((node) => [node.id, node])),
    [layout.nodes]
  );
  const selectedNode = layout.nodes.find((node) => node.id === selectedNodeId) ?? null;
  const selectedEdge = selectedEdgeIndex === null ? null : graph?.edges[selectedEdgeIndex] ?? null;
  const searchResults = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('ru-RU');
    if (!normalized) return [];
    return layout.nodes
      .filter((node) => node.name.toLocaleLowerCase('ru-RU').includes(normalized))
      .slice(0, 8);
  }, [layout.nodes, query]);
  const neighborIds = useMemo(() => {
    const related = new Set<number>();
    if (selectedNodeId === null) return related;
    related.add(selectedNodeId);
    (graph?.edges ?? []).forEach((edge) => {
      if (edge.fromUserId === selectedNodeId) related.add(edge.toUserId);
      if (edge.toUserId === selectedNodeId) related.add(edge.fromUserId);
    });
    return related;
  }, [graph?.edges, selectedNodeId]);

  const clearSelection = () => {
    setSelectedNodeId(null);
    setSelectedEdgeIndex(null);
  };

  const focusNode = (node: PositionedNode) => {
    setSelectedNodeId(node.id);
    setSelectedEdgeIndex(null);
    setQuery('');
    setZoom((current) => Math.max(current, 1));
    setTranslation({
      x: layout.width / 2 - node.x * Math.max(1, zoom),
      y: layout.height / 2 - node.y * Math.max(1, zoom),
    });
  };

  const resetView = () => {
    setZoom(1);
    setTranslation({ x: 0, y: 0 });
    clearSelection();
  };

  const handlePointerDown = (event: React.PointerEvent<SVGSVGElement>) => {
    pointerMoved.current = false;
    if ((event.target as Element).closest('.help-graph-node, .help-graph-edge')) return;
    dragStart.current = {
      x: event.clientX,
      y: event.clientY,
      originX: translation.x,
      originY: translation.y,
    };
    setDragging(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    if (!dragStart.current || !svgRef.current) return;
    const bounds = svgRef.current.getBoundingClientRect();
    const scaleX = layout.width / Math.max(1, bounds.width);
    const scaleY = layout.height / Math.max(1, bounds.height);
    const dx = event.clientX - dragStart.current.x;
    const dy = event.clientY - dragStart.current.y;
    if (Math.abs(dx) + Math.abs(dy) > 3) pointerMoved.current = true;
    setTranslation({
      x: dragStart.current.originX + dx * scaleX,
      y: dragStart.current.originY + dy * scaleY,
    });
  };

  const handlePointerUp = () => {
    dragStart.current = null;
    setDragging(false);
  };

  const zoomBy = (amount: number) => {
    setZoom((current) => Math.min(2.4, Math.max(0.45, current + amount)));
  };

  if (loading) {
    return (
      <main className="help-graph-page">
        <div className="help-graph-loading" role="status" aria-label="Загружается граф взаимопомощи">
          <div className="help-graph-loading-heading"><span /><i /><i /></div>
          <div className="help-graph-loading-canvas"><span /><span /><span /><span /><span /></div>
        </div>
      </main>
    );
  }

  if (error || !graph) {
    return (
      <main className="help-graph-page">
        <header className="help-graph-header">
          <div><span className="help-graph-eyebrow">Сообщество HelpTree</span><h1>Граф взаимопомощи</h1></div>
        </header>
        <section className="help-graph-state" role="alert">
          <span><TreeDeciduous size={24} aria-hidden="true" /></span>
          <h2>Не удалось загрузить граф</h2>
          <p>Проверьте соединение и попробуйте ещё раз.</p>
          <Button onClick={() => void loadData()}>Повторить загрузку</Button>
        </section>
      </main>
    );
  }

  return (
    <main className="help-graph-page">
      <header className="help-graph-header">
        <div className="help-graph-heading">
          <span className="help-graph-eyebrow">Сообщество HelpTree</span>
          <h1>Граф взаимопомощи</h1>
          <p>Исследуйте реальные связи, созданные подтверждёнными актами помощи.</p>
        </div>
        <div className="help-graph-header-actions">
          <button
            type="button"
            className={`help-graph-stats-toggle${showStats ? ' is-active' : ''}`}
            onClick={() => setShowStats((visible) => !visible)}
            aria-expanded={showStats}
          >
            <BarChart3 size={16} aria-hidden="true" /> Статистика
          </button>
        </div>
      </header>

      <section className="help-graph-metrics" aria-label="Показатели графа">
        <div><span className="help-graph-metric-icon"><Users size={17} /></span><strong>{graph.totalUsers.toLocaleString('ru-RU')}</strong><span>участников</span></div>
        <div><span className="help-graph-metric-icon"><TreeDeciduous size={17} /></span><strong>{graph.totalHelps.toLocaleString('ru-RU')}</strong><span>подтверждённых актов помощи</span></div>
        <span className="help-graph-scope-note">Граф строится от вашего участия</span>
      </section>

      {showStats && (
        <section className="help-graph-stats-panel" aria-label="Общая статистика помощи">
          {statsError ? (
            <div className="help-graph-stats-error">
              <span>Не удалось загрузить общую статистику.</span>
              <button type="button" onClick={() => void helpApi.getHelpStats().then(setStats).then(() => setStatsError(false)).catch(() => setStatsError(true))}>Повторить</button>
            </div>
          ) : stats ? (
            <>
              <div className="help-graph-stats-column">
                <h2>Помощь по месяцам</h2>
                <div className="help-graph-month-bars">
                  {Object.entries(stats.byMonth).map(([month, count]) => (
                    <div className="help-graph-month-bar" key={month} title={`${month}: ${count}`}>
                      <span style={{ height: `${Math.max(3, (count / Math.max(1, ...Object.values(stats.byMonth))) * 100)}%` }} />
                      <small>{month.slice(0, 2)}</small>
                    </div>
                  ))}
                </div>
              </div>
              <div className="help-graph-stats-column">
                <h2>Категории</h2>
                {Object.entries(stats.byCategory).sort((a, b) => b[1] - a[1]).slice(0, 5).map(([category, count]) => (
                  <div className="help-graph-category-row" key={category}><span>{category}</span><strong>{count}</strong></div>
                ))}
              </div>
              <div className="help-graph-stats-column">
                <h2>Активные помощники</h2>
                {stats.topHelpers.slice(0, 5).map((helper, index) => (
                  <Link className="help-graph-helper-row" to={`/profile/${helper.userId}`} key={helper.userId}>
                    <span>{index + 1}</span><strong>{helper.name}</strong><small>{helper.helpCount} помощи</small>
                  </Link>
                ))}
              </div>
            </>
          ) : <p>Загружаем статистику…</p>}
        </section>
      )}

      {graph.nodes.length === 0 || graph.edges.length === 0 ? (
        <section className="help-graph-state">
          <span><TreeDeciduous size={24} aria-hidden="true" /></span>
          <h2>{graph.nodes.length === 0 ? 'В вашей цепочке пока нет участников' : 'Пока нет связей помощи'}</h2>
          <p>Граф отражает помощь, подтверждённую участниками. Здесь появятся участники и связи, когда будут доступны подтверждённые акты помощи.</p>
          <Link className="help-graph-link-button" to="/posts">Перейти к заявкам <ChevronRight size={16} /></Link>
        </section>
      ) : (
        <section className={`help-graph-workspace${selectedNode || selectedEdge ? ' has-selection' : ''}`}>
          <div className="help-graph-main">
            <div className="help-graph-toolbar">
              <div className="help-graph-search">
                <Search size={16} aria-hidden="true" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Найти участника"
                  aria-label="Поиск участника в графе"
                />
                {query && <button type="button" onClick={() => setQuery('')} aria-label="Очистить поиск"><X size={15} /></button>}
                {query && (
                  <div className="help-graph-search-results">
                    {searchResults.length ? searchResults.map((node) => (
                      <button type="button" key={node.id} onClick={() => focusNode(node)}>
                        <Avatar name={node.name} avatarUrl={node.avatarUrl} size="small" />
                        <span>{node.name}</span>
                        <ChevronRight size={14} />
                      </button>
                    )) : <span className="help-graph-no-results">Участники не найдены</span>}
                  </div>
                )}
              </div>
              <div className="help-graph-zoom-controls" role="group" aria-label="Управление масштабом графа">
                <button type="button" onClick={() => zoomBy(0.15)} aria-label="Увеличить граф" title="Увеличить"><Plus size={16} /></button>
                <span>{Math.round(zoom * 100)}%</span>
                <button type="button" onClick={() => zoomBy(-0.15)} aria-label="Уменьшить граф" title="Уменьшить"><Minus size={16} /></button>
                <button type="button" onClick={resetView} aria-label="Сбросить вид графа" title="Сбросить вид"><RotateCcw size={15} /></button>
              </div>
            </div>

            <div className="help-graph-canvas">
              <svg
                ref={svgRef}
                viewBox={`0 0 ${layout.width} ${layout.height}`}
                role="img"
                aria-label={`Граф взаимопомощи: ${graph.totalUsers} участников и ${graph.totalHelps} подтверждённых актов`}
                className={dragging ? 'is-dragging' : ''}
                onPointerDown={handlePointerDown}
                onPointerMove={handlePointerMove}
                onPointerUp={handlePointerUp}
                onPointerCancel={handlePointerUp}
                onClick={() => {
                  if (!pointerMoved.current) clearSelection();
                  pointerMoved.current = false;
                }}
              >
                <defs>
                  <pattern id="help-graph-dots" width="32" height="32" patternUnits="userSpaceOnUse">
                    <circle cx="1" cy="1" r="1" fill="rgba(200,245,106,0.12)" />
                  </pattern>
                  <filter id="help-graph-selected-glow" x="-80%" y="-80%" width="260%" height="260%">
                    <feGaussianBlur stdDeviation="7" result="blur" />
                    <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
                  </filter>
                </defs>
                <rect width={layout.width} height={layout.height} fill="url(#help-graph-dots)" />
                <g transform={`translate(${translation.x} ${translation.y}) scale(${zoom})`}>
                  {graph.edges.map((edge, edgeIndex) => {
                    const from = positionedNodesById.get(edge.fromUserId);
                    const to = positionedNodesById.get(edge.toUserId);
                    if (!from || !to) return null;
                    const highlighted = selectedNodeId === from.id || selectedNodeId === to.id || selectedEdgeIndex === edgeIndex;
                    const dimmed = selectedNodeId !== null && !highlighted;
                    const midY = (from.y + to.y) / 2;
                    return (
                      <g
                        key={`${edge.fromUserId}-${edge.toUserId}-${edge.postTitle}-${edge.confirmedAt}-${edgeIndex}`}
                        className="help-graph-edge"
                        role="button"
                        tabIndex={0}
                        aria-label={`${edge.fromUserName} помог участнику ${edge.toUserName}. ${edge.postTitle}`}
                        onClick={(event) => {
                          event.stopPropagation();
                          setSelectedNodeId(null);
                          setSelectedEdgeIndex(edgeIndex);
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            setSelectedNodeId(null);
                            setSelectedEdgeIndex(edgeIndex);
                          }
                        }}
                      >
                        <path
                          d={`M ${from.x} ${from.y + 32} C ${from.x} ${midY}, ${to.x} ${midY}, ${to.x} ${to.y - 32}`}
                          className={highlighted ? 'is-highlighted' : ''}
                          style={{ opacity: dimmed ? 0.12 : highlighted ? 1 : 0.52 }}
                        />
                        <path
                          d={`M ${from.x} ${from.y + 32} C ${from.x} ${midY}, ${to.x} ${midY}, ${to.x} ${to.y - 32}`}
                          className="help-graph-edge-hit"
                        />
                        <title>{edge.postTitle || 'Подтверждённая помощь'}{edge.confirmedAt ? ` · ${formatDate(edge.confirmedAt)}` : ''}</title>
                      </g>
                    );
                  })}
                  {layout.nodes.map((node) => {
                    const selected = node.id === selectedNodeId;
                    const related = neighborIds.has(node.id);
                    const dimmed = selectedNodeId !== null && !related;
                    const isRoot = node.id === currentUser?.id;
                    const radius = isRoot ? 30 : 24;
                    return (
                      <g
                        key={node.id}
                        className={`help-graph-node${selected ? ' is-selected' : ''}`}
                        transform={`translate(${node.x} ${node.y})`}
                        role="button"
                        tabIndex={0}
                        aria-label={`${node.name}, помог ${node.helpedCount ?? 0} раз`}
                        aria-pressed={selected}
                        style={{ opacity: dimmed ? 0.2 : 1 }}
                        onClick={(event) => {
                          event.stopPropagation();
                          if (pointerMoved.current) return;
                          setSelectedNodeId(selected ? null : node.id);
                          setSelectedEdgeIndex(null);
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault();
                            setSelectedNodeId(selected ? null : node.id);
                            setSelectedEdgeIndex(null);
                          }
                        }}
                      >
                        <circle className="help-graph-node-halo" r={radius + 8} />
                        <circle className="help-graph-node-circle" r={radius} filter={selected ? 'url(#help-graph-selected-glow)' : undefined} />
                        {node.avatarUrl ? (
                          <>
                            <clipPath id={`help-graph-avatar-${node.id}`}><circle r={radius - 2} /></clipPath>
                            <image
                              href={node.avatarUrl}
                              x={-(radius - 2)}
                              y={-(radius - 2)}
                              width={(radius - 2) * 2}
                              height={(radius - 2) * 2}
                              preserveAspectRatio="xMidYMid slice"
                              clipPath={`url(#help-graph-avatar-${node.id})`}
                            />
                          </>
                        ) : (
                          <text className="help-graph-node-initial" textAnchor="middle" dy="5">
                            {node.name.charAt(0).toLocaleUpperCase('ru-RU')}
                          </text>
                        )}
                        {isRoot && <text className="help-graph-node-you" textAnchor="middle" y={-radius - 13}>ВЫ</text>}
                        <text className="help-graph-node-label" textAnchor="middle" y={radius + 22}>
                          {node.name.length > 19 ? `${node.name.slice(0, 18)}…` : node.name}
                        </text>
                        <title>{node.name} · рейтинг {Number.isFinite(node.rating) ? node.rating.toFixed(1) : '—'} · помог {node.helpedCount ?? 0}</title>
                      </g>
                    );
                  })}
                </g>
              </svg>
              <div className="help-graph-canvas-hint"><ZoomIn size={14} aria-hidden="true" /> Перетаскивайте для перемещения · используйте кнопки масштаба</div>
            </div>
          </div>

          {(selectedNode || selectedEdge) && (
            <aside className="help-graph-details" aria-label="Информация о выбранном элементе">
              <div className="help-graph-details-heading">
                <span>{selectedNode ? 'Участник' : 'Связь помощи'}</span>
                <button type="button" onClick={clearSelection} aria-label="Закрыть панель"><X size={17} /></button>
              </div>
              {selectedNode && (
                <div className="help-graph-person">
                  <Avatar name={selectedNode.name} avatarUrl={selectedNode.avatarUrl} size="large" />
                  <h2>{selectedNode.name}</h2>
                  {selectedNode.id === currentUser?.id && <span className="help-graph-you-badge">Это вы</span>}
                  <div className="help-graph-person-metrics">
                    <div><strong>{selectedNode.helpedCount ?? 0}</strong><span>помощей</span></div>
                    <div><strong>{selectedNode.rating ? selectedNode.rating.toFixed(1) : '—'}</strong><span>рейтинг</span></div>
                  </div>
                  <Link to={`/profile/${selectedNode.id}`} className="help-graph-profile-link">Открыть профиль <ChevronRight size={15} /></Link>
                </div>
              )}
              {selectedEdge && (
                <div className="help-graph-connection">
                  <div className="help-graph-connection-person"><Avatar name={selectedEdge.fromUserName} size="small" /><strong>{selectedEdge.fromUserName}</strong></div>
                  <div className="help-graph-connection-line"><span />Передал помощь<span /></div>
                  <div className="help-graph-connection-person"><Avatar name={selectedEdge.toUserName} size="small" /><strong>{selectedEdge.toUserName}</strong></div>
                  <div className="help-graph-edge-info">
                    <span className="help-graph-confirmed-tag">Подтверждённая помощь</span>
                    {selectedEdge.postTitle && <p>{selectedEdge.postTitle}</p>}
                    {selectedEdge.confirmedAt && <span className="help-graph-date"><CalendarDays size={14} />{formatDate(selectedEdge.confirmedAt)}</span>}
                    {selectedEdge.postId && <Link className="help-graph-post-link" to={`/posts/${selectedEdge.postId}`}>Открыть заявку <ChevronRight size={14} /></Link>}
                  </div>
                </div>
              )}
              <div className="help-graph-related">
                <h3>Связи</h3>
                {graph.edges.map((edge, edgeIndex) => ({ edge, edgeIndex })).filter(({ edge, edgeIndex }) => selectedNode
                  ? edge.fromUserId === selectedNode.id || edge.toUserId === selectedNode.id
                  : edgeIndex === selectedEdgeIndex
                ).map(({ edge, edgeIndex }) => (
                  <button type="button" key={`${edge.fromUserId}-${edge.toUserId}-${edge.postTitle}-${edge.confirmedAt}-${edgeIndex}`} onClick={() => { setSelectedNodeId(null); setSelectedEdgeIndex(edgeIndex); }}>
                    <span>{edge.fromUserName} <ChevronRight size={12} /> {edge.toUserName}</span>
                    <small>{edge.postTitle || 'Помощь'}</small>
                  </button>
                ))}
                {selectedNode && !graph.edges.some((edge) => edge.fromUserId === selectedNode.id || edge.toUserId === selectedNode.id) && (
                  <p className="help-graph-no-links"><CircleHelp size={15} /> Связи для выбранного участника не найдены.</p>
                )}
              </div>
            </aside>
          )}
        </section>
      )}

      <footer className="help-graph-legend">
        <span><i className="help-graph-legend-current" /> Вы</span>
        <span><i className="help-graph-legend-person" /> Участники</span>
        <span><i className="help-graph-legend-edge" /> Подтверждённые акты помощи</span>
      </footer>
    </main>
  );
};
