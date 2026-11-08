import React, { useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MarkerType,
} from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from 'dagre';
import { EvidenceNode } from './EvidenceNode';
import type { EvidenceNodeData } from './EvidenceNode';
import type { EvidenceGraphDto, EvidenceNodeDto } from '../lib/api';
import { useTheme } from '../lib/theme';

interface EvidenceGraphViewProps {
  graph?: EvidenceGraphDto | null;
  className?: string;
}

const nodeTypes = {
  evidence: EvidenceNode,
};

const NODE_WIDTH = 150;
const NODE_HEIGHT = 56;

function layoutDagreGraph(rawNodes: Node[], rawEdges: Edge[]): { nodes: Node[]; edges: Edge[] } {
  if (rawNodes.length === 0) return { nodes: rawNodes, edges: rawEdges };

  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  dagreGraph.setGraph({
    rankdir: 'LR',
    align: 'DL',
    nodesep: 90,
    ranksep: 120,
    marginx: 30,
    marginy: 30,
  });

  rawNodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });

  rawEdges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  // Position MISSING nodes into an aligned right column if they lack incoming edges
  let maxConfirmedX = 0;
  rawNodes.forEach((node) => {
    const isMissing = Boolean((node.data as unknown as EvidenceNodeData)?.isMissing);
    const pos = dagreGraph.node(node.id);
    if (!isMissing && pos && pos.x > maxConfirmedX) {
      maxConfirmedX = pos.x;
    }
  });

  const layoutedNodes = rawNodes.map((node) => {
    const pos = dagreGraph.node(node.id) || { x: 100, y: 100 };
    const isMissing = Boolean((node.data as unknown as EvidenceNodeData)?.isMissing);

    let finalX = pos.x - NODE_WIDTH / 2;
    // Push missing nodes cleanly to their own right column
    if (isMissing && maxConfirmedX > 0 && finalX <= maxConfirmedX) {
      finalX = maxConfirmedX + 110;
    }

    return {
      ...node,
      position: {
        x: finalX,
        y: pos.y - NODE_HEIGHT / 2,
      },
    };
  });

  return { nodes: layoutedNodes, edges: rawEdges };
}

function formatMinor(minor?: number): string | undefined {
  if (minor == null) return undefined;
  return `$${(minor / 100).toFixed(2)}`;
}

export const EvidenceGraphView: React.FC<EvidenceGraphViewProps> = ({ graph, className = '' }) => {
  const { isDark } = useTheme();

  // Convert API DTO into React Flow Nodes and Edges using Dagre layered layout
  const { nodes, edges } = useMemo(() => {
    let rawNodes: Node[] = [];
    let rawEdges: Edge[] = [];

    if (!graph || !graph.nodes || graph.nodes.length === 0) {
      // Canonical fallback graph matching Case PR-0142
      rawNodes = [
        {
          id: 'order',
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: 'PayPal order',
            sublabel: 'approved',
            source: 'PayPal',
            status: 'APPROVED',
          } satisfies EvidenceNodeData,
        },
        {
          id: 'capture',
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: 'Capture',
            sublabel: 'COMPLETED',
            source: 'PayPal',
            status: 'COMPLETED',
            amount: '$84.50',
          } satisfies EvidenceNodeData,
        },
        {
          id: 'webhook',
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: 'Webhook',
            sublabel: 'delivered twice',
            source: 'PayPal',
            status: 'DELIVERED',
          } satisfies EvidenceNodeData,
        },
        {
          id: 'shop_order',
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: 'Shop order #10482',
            sublabel: 'still PENDING',
            source: 'Shop',
            status: 'PENDING',
            amount: '$84.50',
          } satisfies EvidenceNodeData,
        },
        {
          id: 'shipment',
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: 'No shipment yet',
            sublabel: 'Unfulfilled',
            source: 'Shop',
            status: 'MISSING',
            isMissing: true,
          } satisfies EvidenceNodeData,
        },
      ];

      rawEdges = [
        { id: 'e1', source: 'order', target: 'capture', type: 'smoothstep', style: { stroke: isDark ? '#64748b' : '#94a3b8', strokeWidth: 1.5 } },
        { id: 'e2', source: 'order', target: 'webhook', type: 'smoothstep', style: { stroke: isDark ? '#64748b' : '#94a3b8', strokeWidth: 1.5 } },
        { id: 'e3', source: 'capture', target: 'webhook', type: 'smoothstep', style: { stroke: isDark ? '#64748b' : '#94a3b8', strokeWidth: 1.5 } },
        {
          id: 'e4',
          source: 'capture',
          target: 'shop_order',
          type: 'smoothstep',
          label: 'missing: PAID update',
          labelStyle: { fill: '#f59e0b', fontSize: 10, fontWeight: 600 },
          labelBgStyle: { fill: isDark ? '#1a2234' : '#ffffff', fillOpacity: 0.95, rx: 6, ry: 6 },
          labelBgPadding: [6, 3],
          style: { stroke: '#f59e0b', strokeWidth: 1.5, strokeDasharray: '4 4' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#f59e0b' },
        },
        {
          id: 'e5',
          source: 'shop_order',
          target: 'shipment',
          type: 'smoothstep',
          label: 'unfulfilled',
          labelStyle: { fill: isDark ? '#94a3b8' : '#64748b', fontSize: 9 },
          labelBgStyle: { fill: isDark ? '#1a2234' : '#ffffff', fillOpacity: 0.95, rx: 4, ry: 4 },
          labelBgPadding: [4, 2],
          style: { stroke: isDark ? '#64748b' : '#94a3b8', strokeWidth: 1.5, strokeDasharray: '4 4' },
        },
      ];
    } else {
      rawNodes = (graph.nodes || []).map((n: EvidenceNodeDto, idx: number) => {
        const isMissing = n.status === 'MISSING' || !!n.isMissing;
        const amountMinor = (n as { amountMinor?: number }).amountMinor ?? (n.metadata?.amountMinor as number | undefined);
        const formattedAmount = formatMinor(amountMinor);

        return {
          id: n.id || `node-${idx}`,
          type: 'evidence',
          position: { x: 0, y: 0 },
          data: {
            label: n.label || (n.type ? n.type.replace(/_/g, ' ').toLowerCase() : 'evidence'),
            sublabel: isMissing ? 'MISSING' : (n.status || 'KNOWN'),
            source: n.source,
            status: n.status || (isMissing ? 'MISSING' : 'KNOWN'),
            amount: formattedAmount,
            isMissing,
          } satisfies EvidenceNodeData,
        };
      });

      rawEdges = (graph.edges || []).map((e, idx) => {
        const isMissing = e.status === 'MISSING' || !!e.isDashed;
        return {
          id: `edge_${e.source}_${e.target}_${idx}`,
          source: e.source || '',
          target: e.target || '',
          type: 'smoothstep',
          label: e.label || (isMissing ? `missing: ${e.type?.toLowerCase() || 'link'}` : undefined),
          labelStyle: isMissing
            ? { fill: '#f59e0b', fontSize: 10, fontWeight: 600 }
            : { fill: isDark ? '#94a3b8' : '#64748b', fontSize: 10 },
          labelBgStyle: { fill: isDark ? '#1a2234' : '#ffffff', fillOpacity: 0.95, rx: 6, ry: 6 },
          labelBgPadding: [6, 3],
          style: isMissing
            ? { stroke: '#f59e0b', strokeWidth: 1.5, strokeDasharray: '4 4' }
            : { stroke: isDark ? '#64748b' : '#94a3b8', strokeWidth: 1.5 },
          markerEnd: isMissing ? { type: MarkerType.ArrowClosed, color: '#f59e0b' } : undefined,
        };
      });
    }

    return layoutDagreGraph(rawNodes, rawEdges);
  }, [graph, isDark]);

  return (
    <div className={`relative w-full h-[370px] bg-[#f8fafc] dark:bg-[#080d17] rounded-xl border border-slate-200/60 dark:border-slate-800/80 overflow-hidden ${className}`}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        attributionPosition="bottom-right"
        proOptions={{ hideAttribution: true }}
        nodesDraggable
        elementsSelectable
      >
        <Background gap={16} size={1} className="dark:opacity-20 opacity-30" />
        <Controls showInteractive={false} className="!bg-white dark:!bg-[#131b26] !border-slate-200 dark:!border-slate-800 !rounded-lg !shadow-xs" />
      </ReactFlow>

      {/* Graph Legend Overlay */}
      <div className="absolute bottom-2.5 left-3 flex items-center gap-3 px-3 py-1.5 rounded-lg bg-white/90 dark:bg-[#0e1522]/90 backdrop-blur-xs border border-slate-200/70 dark:border-slate-800/70 text-[11px] text-slate-600 dark:text-slate-400 pointer-events-none select-none z-10 shadow-2xs">
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-sm bg-blue-500 inline-block" />
          <span>Confirmed</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-0 border-t-2 border-dashed border-amber-500 inline-block" />
          <span>Missing Link</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-sm border border-dashed border-amber-500 bg-amber-500/20 inline-block" />
          <span>Missing Node</span>
        </div>
      </div>
    </div>
  );
};
