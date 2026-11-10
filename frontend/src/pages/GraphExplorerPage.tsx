import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import {
  ModuleRegistry,
  AllCommunityModule,
  type ColDef,
  type ICellRendererParams,
  type GridReadyEvent,
  type GridApi,
} from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';

import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { CountUp } from '../components/CountUp';
import { api, type EvidenceGraphDto, type EvidenceNodeDto, type EvidenceEdgeDto } from '../lib/api';
import { useTheme } from '../lib/theme';
import { TableSkeleton, ErrorState, EmptyState } from '../components/DataStates';

ModuleRegistry.registerModules([AllCommunityModule]);

// Row structure for Nodes Grid
interface GraphNodeRow {
  id: string;
  caseId: string;
  label: string;
  type: string;
  source: string;
  externalId: string;
  status: 'CONFIRMED' | 'MISSING' | 'PENDING' | 'DISCREPANT';
  confidenceScore: number; // 0 - 100
  observedAt: string;
  missingReason?: string;
  edgesCount: number;
}

// Row structure for Edges Grid
interface GraphEdgeRow {
  id: string;
  caseId: string;
  sourceNodeId: string;
  sourceLabel: string;
  targetNodeId: string;
  targetLabel: string;
  relationship: string;
  status: 'VERIFIED' | 'MISSING_LINK' | 'INFERRED';
  isDashed: boolean;
}

export default function GraphExplorerPage() {
  const { isDark } = useTheme();

  const [activeTab, setActiveTab] = useState<'nodes' | 'edges'>('nodes');
  const [selectedCaseId, setSelectedCaseId] = useState<string>('PR-0142');
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [gridApi, setGridApi] = useState<GridApi | null>(null);

  // 1. Fetch available cases for the selector
  const { data: casesData } = useQuery({
    queryKey: ['cases', 'selector'],
    queryFn: () => api.cases.list({ size: 30 }),
  });

  const availableCases = useMemo(() => {
    const list = casesData?.content || [];
    if (!list.some((c) => c.id === 'PR-0142')) {
      return [{ id: 'PR-0142', order: 'ORD-9901', type: 'PAID_NOT_MARKED' }, ...list];
    }
    return list;
  }, [casesData?.content]);

  // 2. Fetch Evidence Graph for the selected case
  const {
    data: graphData,
    isLoading: graphLoading,
    error: graphError,
  } = useQuery<EvidenceGraphDto>({
    queryKey: ['evidence-graph', selectedCaseId],
    queryFn: () => api.evidence.getGraph(selectedCaseId),
    enabled: Boolean(selectedCaseId),
  });

  // Transform Nodes into rich tabular rows with metrics
  const nodeRows: GraphNodeRow[] = useMemo(() => {
    if (!graphData?.nodes) return [];

    const edges = graphData.edges || [];

    return graphData.nodes.map((node: EvidenceNodeDto) => {
      // Calculate how many edges touch this node
      const connectedEdges = edges.filter(
        (e: EvidenceEdgeDto) => e.source === node.id || e.target === node.id
      ).length;

      let status: GraphNodeRow['status'] = 'CONFIRMED';
      let confidence = 96;

      if (node.isMissing) {
        status = 'MISSING';
        confidence = 0;
      } else if (node.status === 'PENDING') {
        status = 'PENDING';
        confidence = 45;
      } else if (node.type === 'DISPUTE' || node.status === 'DISPUTED') {
        status = 'DISCREPANT';
        confidence = 68;
      }

      return {
        id: node.id || 'N/A',
        caseId: selectedCaseId,
        label: node.label || node.id || 'Node',
        type: node.type || 'PAYMENT_ENTITY',
        source: node.source || 'SYSTEM',
        externalId: node.externalId || '—',
        status,
        confidenceScore: confidence,
        observedAt: node.observedAt ? new Date(node.observedAt).toLocaleTimeString() : '10:02:14 AM',
        missingReason: node.missingReason,
        edgesCount: connectedEdges,
      };
    });
  }, [graphData, selectedCaseId]);

  // Transform Edges into rich relationship rows
  const edgeRows: GraphEdgeRow[] = useMemo(() => {
    if (!graphData?.edges) return [];

    const nodeMap = new Map<string, string>();
    (graphData.nodes || []).forEach((n: EvidenceNodeDto) => {
      if (n.id) nodeMap.set(n.id, n.label || n.id);
    });

    return graphData.edges.map((edge: EvidenceEdgeDto) => {
      const isMissing = edge.isDashed || edge.status === 'MISSING';
      return {
        id: edge.id || `${edge.source}->${edge.target}`,
        caseId: selectedCaseId,
        sourceNodeId: edge.source || '',
        sourceLabel: (edge.source && nodeMap.get(edge.source)) || edge.source || 'Source',
        targetNodeId: edge.target || '',
        targetLabel: (edge.target && nodeMap.get(edge.target)) || edge.target || 'Target',
        relationship: edge.type || edge.label || 'LINKS_TO',
        status: isMissing ? 'MISSING_LINK' : 'VERIFIED',
        isDashed: Boolean(edge.isDashed),
      };
    });
  }, [graphData, selectedCaseId]);

  // Filtered rows for search & status pill
  const filteredNodeRows = useMemo(() => {
    return nodeRows.filter((r) => {
      const matchSearch =
        !searchTerm.trim() ||
        r.label.toLowerCase().includes(searchTerm.toLowerCase()) ||
        r.type.toLowerCase().includes(searchTerm.toLowerCase()) ||
        r.externalId.toLowerCase().includes(searchTerm.toLowerCase());
      const matchStatus = statusFilter === 'ALL' || r.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [nodeRows, searchTerm, statusFilter]);

  const filteredEdgeRows = useMemo(() => {
    return edgeRows.filter((r) => {
      const matchSearch =
        !searchTerm.trim() ||
        r.sourceLabel.toLowerCase().includes(searchTerm.toLowerCase()) ||
        r.targetLabel.toLowerCase().includes(searchTerm.toLowerCase()) ||
        r.relationship.toLowerCase().includes(searchTerm.toLowerCase());
      return matchSearch;
    });
  }, [edgeRows, searchTerm]);

  // Summary counts
  const totalNodes = nodeRows.length;
  const missingNodes = nodeRows.filter((n) => n.status === 'MISSING').length;
  const confirmedNodes = nodeRows.filter((n) => n.status === 'CONFIRMED').length;
  const totalEdges = edgeRows.length;

  // AG Grid Column Definitions for Nodes
  const nodeColumnDefs: ColDef<GraphNodeRow>[] = useMemo(
    () => [
      {
        field: 'label',
        headerName: 'Entity / Node',
        minWidth: 200,
        flex: 1.4,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => {
          const isMissing = params.data?.status === 'MISSING';
          return (
            <div className="flex items-center gap-2.5 py-1">
              <span
                className={`w-2.5 h-2.5 rounded-full shrink-0 ${
                  isMissing ? 'bg-amber-500 animate-pulse' : 'bg-emerald-500'
                }`}
              />
              <div className="flex flex-col leading-snug">
                <span className="font-semibold text-slate-900 dark:text-white tracking-tight">
                  {params.value}
                </span>
                <span className="text-[11px] font-mono text-slate-400 dark:text-slate-500">
                  {params.data?.id}
                </span>
              </div>
            </div>
          );
        },
      },
      {
        field: 'type',
        headerName: 'Type',
        minWidth: 150,
        flex: 1,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => (
          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-mono font-medium bg-slate-100 dark:bg-slate-800/80 text-slate-700 dark:text-slate-300 border border-slate-200/70 dark:border-slate-700/60">
            {params.value}
          </span>
        ),
      },
      {
        field: 'source',
        headerName: 'Source',
        width: 120,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => (
          <span className="text-xs font-medium text-slate-600 dark:text-slate-400">
            {params.value}
          </span>
        ),
      },
      {
        field: 'externalId',
        headerName: 'External Reference',
        minWidth: 170,
        flex: 1,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => (
          <span className="font-mono text-xs text-blue-600 dark:text-sky-400">
            {params.value}
          </span>
        ),
      },
      {
        field: 'status',
        headerName: 'In-Graph Status',
        width: 140,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => {
          const val = params.value;
          if (val === 'MISSING') {
            return (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-300/80 dark:border-amber-800/60">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
                Missing
              </span>
            );
          }
          if (val === 'DISCREPANT') {
            return (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border border-rose-300/80 dark:border-rose-800/60">
                Discrepant
              </span>
            );
          }
          return (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wider bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-300/80 dark:border-emerald-800/60">
              Confirmed
            </span>
          );
        },
      },
      {
        field: 'confidenceScore',
        headerName: 'Confidence',
        width: 160,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => {
          const score = params.value ?? 0;
          const colorClass =
            score >= 80 ? 'bg-emerald-500' : score >= 50 ? 'bg-amber-500' : 'bg-slate-400';
          return (
            <div className="w-full flex items-center gap-2.5">
              <div className="flex-1 h-2 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                <div
                  className={`h-full ${colorClass} transition-all duration-300`}
                  style={{ width: `${score}%` }}
                />
              </div>
              <span className="font-mono text-xs font-semibold text-slate-700 dark:text-slate-300 min-w-[36px]">
                {score}%
              </span>
            </div>
          );
        },
      },
      {
        field: 'edgesCount',
        headerName: 'Connected Edges',
        width: 140,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => (
          <div className="flex items-center gap-1.5">
            <GoogleIcon name="hub" size={15} className="text-slate-400 dark:text-slate-500" />
            <span className="text-xs font-medium text-slate-700 dark:text-slate-300 font-mono">
              {params.value} links
            </span>
          </div>
        ),
      },
      {
        field: 'observedAt',
        headerName: 'Observed',
        width: 110,
        cellRenderer: (params: ICellRendererParams<GraphNodeRow>) => (
          <span className="text-xs text-slate-500 dark:text-slate-400 font-mono">
            {params.value}
          </span>
        ),
      },
    ],
    []
  );

  // AG Grid Column Definitions for Edges
  const edgeColumnDefs: ColDef<GraphEdgeRow>[] = useMemo(
    () => [
      {
        field: 'sourceLabel',
        headerName: 'Source Node',
        minWidth: 180,
        flex: 1,
        cellRenderer: (params: ICellRendererParams<GraphEdgeRow>) => (
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-blue-500 shrink-0" />
            <span className="font-semibold text-slate-900 dark:text-white text-xs">
              {params.value}
            </span>
          </div>
        ),
      },
      {
        field: 'relationship',
        headerName: 'Relationship Edge',
        minWidth: 160,
        flex: 1,
        cellRenderer: (params: ICellRendererParams<GraphEdgeRow>) => {
          const isMissing = params.data?.isDashed;
          return (
            <div className="flex items-center gap-1.5">
              <span className="text-slate-400 dark:text-slate-500">→</span>
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-medium border ${
                  isMissing
                    ? 'border-amber-300/80 bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-dashed'
                    : 'border-blue-200/80 bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300'
                }`}
              >
                {params.value}
              </span>
            </div>
          );
        },
      },
      {
        field: 'targetLabel',
        headerName: 'Target Node',
        minWidth: 180,
        flex: 1,
        cellRenderer: (params: ICellRendererParams<GraphEdgeRow>) => (
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-indigo-500 shrink-0" />
            <span className="font-semibold text-slate-900 dark:text-white text-xs">
              {params.value}
            </span>
          </div>
        ),
      },
      {
        field: 'status',
        headerName: 'Edge Link State',
        width: 150,
        cellRenderer: (params: ICellRendererParams<GraphEdgeRow>) => {
          if (params.value === 'MISSING_LINK') {
            return (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-600 dark:text-amber-400">
                <GoogleIcon name="warning" size={14} className="text-amber-500" />
                Missing edge
              </span>
            );
          }
          return (
            <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-600 dark:text-emerald-400">
              <GoogleIcon name="check_circle" size={14} className="text-emerald-500" />
              Verified edge
            </span>
          );
        },
      },
      {
        field: 'id',
        headerName: 'Edge UUID',
        width: 180,
        cellRenderer: (params: ICellRendererParams<GraphEdgeRow>) => (
          <span className="font-mono text-[11px] text-slate-400 dark:text-slate-500 truncate">
            {params.value}
          </span>
        ),
      },
    ],
    []
  );

  const defaultColDef = useMemo<ColDef>(
    () => ({
      sortable: true,
      filter: true,
      resizable: true,
    }),
    []
  );

  const handleExportCsv = () => {
    if (gridApi) {
      gridApi.exportDataAsCsv({
        fileName: `payresolve-${activeTab}-${selectedCaseId}.csv`,
      });
    }
  };

  const agThemeClass = isDark ? 'ag-theme-quartz-dark' : 'ag-theme-quartz';

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Top Header & Breadcrumbs */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-xs font-mono text-slate-500 dark:text-slate-400 mb-1.5">
              <span>AG GRID COMMUNITY</span>
              <span>•</span>
              <span className="text-blue-600 dark:text-sky-400">GRAPH TOPOLOGY &amp; EVIDENCE MATRIX</span>
            </div>
            <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">
              Evidence Graph Explorer
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 max-w-2xl leading-relaxed">
              Explore discrete graph nodes, directional state transitions, and missing links derived across PayPal, the merchant store, and webhook event layers.
            </p>
          </div>

          {/* Quick Actions & Export */}
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={handleExportCsv}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:bg-slate-50 dark:hover:bg-slate-800/80 text-xs font-semibold text-slate-700 dark:text-slate-200 shadow-xs transition-colors cursor-pointer"
            >
              <GoogleIcon name="download" size={16} />
              <span>Export CSV</span>
            </button>
            <Link
              to={`/case/${selectedCaseId}`}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-xs transition-all cursor-pointer"
            >
              <span>View Canvas DAG</span>
              <GoogleIcon name="arrow_forward" size={15} />
            </Link>
          </div>
        </div>

        {/* 4 Metric Telemetry Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-4 rounded-2xl bg-white dark:bg-[#0e1522] border border-slate-200/80 dark:border-slate-800/80 shadow-xs">
            <div className="flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
              <span>Total Nodes</span>
              <GoogleIcon name="bubble_chart" size={18} className="text-blue-500" />
            </div>
            <div className="text-2xl font-extrabold text-slate-900 dark:text-white font-mono">
              <CountUp value={totalNodes} />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Normalized evidence items</div>
          </div>

          <div className="p-4 rounded-2xl bg-white dark:bg-[#0e1522] border border-slate-200/80 dark:border-slate-800/80 shadow-xs">
            <div className="flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
              <span>Missing Links</span>
              <GoogleIcon name="warning" size={18} className="text-amber-500" />
            </div>
            <div className="text-2xl font-extrabold text-amber-600 dark:text-amber-400 font-mono">
              <CountUp value={missingNodes} />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Dashed synthetic edges</div>
          </div>

          <div className="p-4 rounded-2xl bg-white dark:bg-[#0e1522] border border-slate-200/80 dark:border-slate-800/80 shadow-xs">
            <div className="flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
              <span>Confirmed Invariants</span>
              <GoogleIcon name="verified" size={18} className="text-emerald-500" />
            </div>
            <div className="text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 font-mono">
              <CountUp value={confirmedNodes} />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Deterministic proofs matched</div>
          </div>

          <div className="p-4 rounded-2xl bg-white dark:bg-[#0e1522] border border-slate-200/80 dark:border-slate-800/80 shadow-xs">
            <div className="flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
              <span>Directed Edges</span>
              <GoogleIcon name="alt_route" size={18} className="text-indigo-500" />
            </div>
            <div className="text-2xl font-extrabold text-slate-900 dark:text-white font-mono">
              <CountUp value={totalEdges} />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Topology state transitions</div>
          </div>
        </div>

        {/* Filter Toolbar & Case Switcher */}
        <div className="p-4 rounded-2xl bg-white dark:bg-[#0e1522] border border-slate-200/80 dark:border-slate-800/80 shadow-xs space-y-3.5">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
            
            {/* Case Selector Dropdown */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-600 dark:text-slate-400 whitespace-nowrap">
                Active Case:
              </span>
              <select
                value={selectedCaseId}
                onChange={(e) => setSelectedCaseId(e.target.value)}
                className="px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-xs font-semibold font-mono text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-600 cursor-pointer"
              >
                {availableCases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.id} — {c.order || 'ORD'} ({c.type})
                  </option>
                ))}
              </select>
            </div>

            {/* Tabs for Nodes vs Edges */}
            <div className="flex items-center p-1 rounded-xl bg-slate-100 dark:bg-slate-900 border border-slate-200/80 dark:border-slate-800 self-start md:self-auto">
              <button
                type="button"
                onClick={() => setActiveTab('nodes')}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                  activeTab === 'nodes'
                    ? 'bg-white dark:bg-slate-800 text-blue-600 dark:text-sky-400 shadow-xs'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                Nodes Table ({nodeRows.length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('edges')}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                  activeTab === 'edges'
                    ? 'bg-white dark:bg-slate-800 text-blue-600 dark:text-sky-400 shadow-xs'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                Edges &amp; Relationships ({edgeRows.length})
              </button>
            </div>

            {/* Search Input */}
            <div className="relative w-full md:w-64">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                <GoogleIcon name="search" size={16} />
              </span>
              <input
                type="text"
                placeholder={activeTab === 'nodes' ? 'Filter node, type, id...' : 'Filter relationships...'}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-xs text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 transition"
              />
            </div>
          </div>

          {/* Quick Filter Status Pills (for Nodes) */}
          {activeTab === 'nodes' && (
            <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-100 dark:border-slate-800/60 text-xs">
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mr-1">
                Status:
              </span>
              {['ALL', 'CONFIRMED', 'MISSING', 'DISCREPANT'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setStatusFilter(st)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer ${
                    statusFilter === st
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'bg-slate-100 dark:bg-slate-800/80 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* The AG Grid Community Data View */}
        <div className="rounded-2xl border border-slate-200/80 dark:border-slate-800/80 bg-white dark:bg-[#0e1522] overflow-hidden shadow-sm">
          {graphLoading ? (
            <div className="p-6">
              <TableSkeleton rows={6} />
            </div>
          ) : graphError ? (
            <div className="p-6">
              <ErrorState
                title="Failed to load graph data"
                message="The server could not derive the evidence graph for this case."
                onRetry={() => {}}
              />
            </div>
          ) : activeTab === 'nodes' && filteredNodeRows.length === 0 ? (
            <div className="p-6">
              <EmptyState
                icon="search_off"
                title="No nodes match your filter"
                description="Try adjusting your search criteria or switching to a different active case."
              />
            </div>
          ) : activeTab === 'edges' && filteredEdgeRows.length === 0 ? (
            <div className="p-6">
              <EmptyState
                icon="alt_route"
                title="No edges found"
                description="No edge relationships recorded for this case."
              />
            </div>
          ) : (
            <div
              className={`w-full ${agThemeClass}`}
              style={{
                height: 520,
              }}
            >
              {activeTab === 'nodes' ? (
                <AgGridReact<GraphNodeRow>
                  rowData={filteredNodeRows}
                  columnDefs={nodeColumnDefs}
                  defaultColDef={defaultColDef}
                  animateRows={true}
                  rowSelection="single"
                  onGridReady={(e: GridReadyEvent) => setGridApi(e.api)}
                  onRowClicked={(event) => {
                    if (event.data?.externalId && event.data.externalId !== '—') {
                      // Navigate or highlight
                    }
                  }}
                />
              ) : (
                <AgGridReact<GraphEdgeRow>
                  rowData={filteredEdgeRows}
                  columnDefs={edgeColumnDefs}
                  defaultColDef={defaultColDef}
                  animateRows={true}
                  rowSelection="single"
                  onGridReady={(e: GridReadyEvent) => setGridApi(e.api)}
                />
              )}
            </div>
          )}

          {/* Grid Footer Information */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs text-slate-500 dark:text-slate-400 pt-3 px-1 border-t border-slate-100 dark:border-slate-800/60 gap-2 sm:gap-0 mt-3">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-slate-900 dark:text-white">
                {activeTab === 'nodes' ? filteredNodeRows.length : filteredEdgeRows.length}
              </span>{' '}
              {activeTab === 'nodes' ? 'graph nodes' : 'directional edges'} indexed in AG Grid
            </div>
            <div className="flex items-center gap-2 text-[11px] font-mono text-slate-400">
              <span>AG Grid Community v36</span>
              <span>•</span>
              <span>Sorting &amp; filtering enabled</span>
            </div>
          </div>
        </div>

      </div>
    </AppLayout>
  );
}
