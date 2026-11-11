import { useState, useMemo, useRef, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import type {
  CellKeyDownEvent,
  ColDef,
  GridApi,
  GridReadyEvent,
  ICellRendererParams,
  ValueFormatterParams,
} from 'ag-grid-community';

import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { InfoTip } from '../components/InfoTip';
import { LastUpdatedLabel } from '../components/LastUpdatedLabel';
import { HeaderWithInfoTip } from '../components/grid/HeaderWithInfoTip';
import { CountUp } from '../components/CountUp';
import { CopyableIdCell, SeverityPillCell, StatusPillCell } from '../components/grid/GridCellRenderers';
import { api, type CaseRecord } from '../lib/api';
import { toast } from '../lib/toast';
import {
  useGridTheme,
  saveGridColumnState,
  restoreGridColumnState,
  resetGridColumnState,
} from '../lib/gridTheme';
import { ErrorState, EmptyState } from '../components/DataStates';

const QUEUE_COL_STATE_KEY = 'payresolve_grid_queue_cols_v1';

interface GridCaseRow {
  id: string;
  type: string;
  order: string;
  amountMinor: number;
  amount: string;
  severity: 'High' | 'Medium' | 'Low';
  openedAtDate: Date | null;
  age: string;
  status: 'Open' | 'Investigating' | 'Proposed' | 'Resolved';
  raw: CaseRecord;
}

function calculateAge(detectedAt?: string): string {
  if (!detectedAt) return '1 min';
  const diffMs = Date.now() - new Date(detectedAt).getTime();
  const mins = Math.max(1, Math.floor(diffMs / 60000));
  if (mins < 60) return `${mins} min`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.floor(hours / 24);
  return `${days} d`;
}

function formatCaseType(type: string): string {
  switch (type) {
    case 'PAID_NOT_MARKED':
      return 'Paid, not marked';
    case 'AMOUNT_MISMATCH':
      return 'Amount mismatch';
    case 'DUPLICATE_EVENT':
      return 'Duplicate event';
    case 'REFUND_MISMATCH':
      return 'Refund mismatch';
    case 'ORPHAN_CAPTURE':
      return 'Orphan capture';
    default:
      return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }
}

function formatMinor(minor?: number): string {
  if (minor == null) return '$0.00';
  return `$${(minor / 100).toFixed(2)}`;
}

export default function ExceptionQueuePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState('All');
  const [selectedStatus, setSelectedStatus] = useState('All');

  // 1. Fetch Cases (auto-refresh every 5s when tab is visible)
  const {
    data: pagedData,
    isLoading: casesLoading,
    isError: casesError,
    refetch: refetchCases,
    dataUpdatedAt: casesUpdatedAt,
    isFetching: casesFetching,
  } = useQuery({
    queryKey: ['cases', selectedStatus],
    queryFn: () =>
      api.cases.list({
        status: selectedStatus !== 'All' ? selectedStatus.toUpperCase() : undefined,
        size: 50,
      }),
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
  });

  // 2. Fetch Stats
  const { data: statsData } = useQuery({
    queryKey: ['case-stats'],
    queryFn: api.cases.stats,
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
  });

  // Run Detectors Mutation
  const detectMutation = useMutation({
    mutationFn: api.cases.detect,
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
      const count = (res.casesOpened ?? 0) + (res.casesUpdated ?? 0);
      toast.success(
        res.message || `Detectors executed: ${count} discrepancies evaluated across PayPal Orders v2 & webhook log.`
      );
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Detector execution failed.');
    },
  });

  // Seed Canonical Cases Mutation
  const seedMutation = useMutation({
    mutationFn: api.cases.seed,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
      toast.success('Sample fixture dataset reloaded with canonical cases.');
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Failed to seed sample cases.');
    },
  });

  const gridRef = useRef<AgGridReact<GridCaseRow>>(null);
  const [gridApi, setGridApi] = useState<GridApi<GridCaseRow> | null>(null);
  const { gridTheme } = useGridTheme();

  // Map API records to grid rows (real rows only, no mock fallback)
  const rowData: GridCaseRow[] = useMemo(() => {
    const rawList = pagedData?.content || [];
    return rawList.map((c) => {
      const orderNum = c.order || '#n/a';
      const severity = c.severity === 'HIGH' || c.severity === 'High' ? 'High' : c.severity === 'MEDIUM' || c.severity === 'Medium' ? 'Medium' : 'Low';
      const status =
        c.status === 'OPEN' || c.status === 'Open'
          ? 'Open'
          : c.status === 'INVESTIGATING' || c.status === 'Investigating'
          ? 'Investigating'
          : c.status === 'ACTION_PROPOSED' || c.status === 'Proposed'
          ? 'Proposed'
          : 'Resolved';

      return {
        id: c.id || 'PR-0000',
        type: c.typeLabel || formatCaseType(c.type),
        order: orderNum,
        amountMinor: Number(c.amountMinor ?? 0),
        amount: c.amount || formatMinor(c.amountMinor),
        severity,
        openedAtDate: c.openedAt ? new Date(c.openedAt) : null,
        age: c.age || calculateAge(c.openedAt),
        status,
        raw: c,
      };
    });
  }, [pagedData]);

  // Pre-filter pill buttons while letting AG Grid quickFilterText handle text search across all columns
  const filteredRows = useMemo(() => {
    return rowData.filter((r) => {
      const matchesSeverity = selectedSeverity === 'All' || r.severity === selectedSeverity;
      const matchesStatus = selectedStatus === 'All' || r.status === selectedStatus;
      return matchesSeverity && matchesStatus;
    });
  }, [rowData, selectedSeverity, selectedStatus]);

  // AG Grid Community column definitions (text, number, date filters + pinned Case ID + tooltips)
  const columnDefs: ColDef<GridCaseRow>[] = useMemo(() => [
    {
      field: 'id',
      headerName: 'Case',
      pinned: 'left',
      lockPinned: true,
      width: 125,
      minWidth: 115,
      filter: 'agTextColumnFilter',
      tooltipValueGetter: (params) => `Case ${params.value ?? ''} — Press Enter to inspect`,
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        if (!params.value) return null;
        return (
          <div className="flex items-center gap-1.5 min-w-0">
            <Link
              to={`/case/${params.value}`}
              onClick={(e) => e.stopPropagation()}
              className="text-blue-600 dark:text-blue-400 font-semibold hover:underline cursor-pointer truncate"
            >
              {params.value}
            </Link>
            <CopyableIdCell value={String(params.value)} displayText="" />
          </div>
        );
      },
    },
    {
      field: 'type',
      headerName: 'Type',
      flex: 1.2,
      minWidth: 155,
      filter: 'agTextColumnFilter',
      tooltipField: 'type',
    },
    {
      field: 'order',
      headerName: 'Order / Capture',
      width: 165,
      filter: 'agTextColumnFilter',
      tooltipField: 'order',
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => {
        const val = String(params.value || '#n/a');
        const copyVal = val.startsWith('capture:') ? val.replace('capture:', '') : val;
        return <CopyableIdCell value={val} copyValue={copyVal} />;
      },
    },
    {
      field: 'amountMinor',
      headerName: 'Amount',
      width: 125,
      filter: 'agNumberColumnFilter',
      headerComponent: HeaderWithInfoTip,
      headerComponentParams: { metricId: 'queue.col.amount' },
      cellClass: 'font-mono font-medium',
      valueFormatter: (params: ValueFormatterParams<GridCaseRow, number>) =>
        formatMinor(params.value ?? undefined),
      tooltipValueGetter: (params) =>
        params.data ? `${formatMinor(params.data.amountMinor)} (${params.data.amountMinor} cents)` : '',
    },
    {
      field: 'severity',
      headerName: 'Severity',
      width: 135,
      filter: 'agTextColumnFilter',
      headerComponent: HeaderWithInfoTip,
      headerComponentParams: { metricId: 'queue.col.severity' },
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => (
        <SeverityPillCell severity={params.value} />
      ),
    },
    {
      field: 'openedAtDate',
      headerName: 'Age',
      width: 145,
      filter: 'agDateColumnFilter',
      headerComponent: HeaderWithInfoTip,
      headerComponentParams: { metricId: 'queue.col.age' },
      cellClass: 'text-slate-500 dark:text-slate-400 text-xs font-mono',
      valueFormatter: (params: ValueFormatterParams<GridCaseRow, Date>) => {
        if (!params.value) return '';
        return `${params.data?.age || ''} (${params.value.toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })})`;
      },
      tooltipValueGetter: (params) => (params.value ? params.value.toISOString() : ''),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 135,
      filter: 'agTextColumnFilter',
      cellRenderer: (params: ICellRendererParams<GridCaseRow>) => (
        <StatusPillCell status={params.value} />
      ),
    },
  ], []);

  const defaultColDef = useMemo<ColDef<GridCaseRow>>(() => ({
    sortable: true,
    filter: true,
    resizable: true,
  }), []);

  const onGridReady = useCallback((event: GridReadyEvent<GridCaseRow>) => {
    setGridApi(event.api);
    restoreGridColumnState(QUEUE_COL_STATE_KEY, event.api);
  }, []);

  const persistColumns = useCallback(() => {
    if (gridApi) {
      saveGridColumnState(QUEUE_COL_STATE_KEY, gridApi);
    }
  }, [gridApi]);

  const handleExportCSV = () => {
    if (!gridApi) return;
    gridApi.exportDataAsCsv({
      fileName: 'payresolve_exception_queue.csv',
      columnKeys: ['id', 'type', 'order', 'amountMinor', 'severity', 'openedAtDate', 'status'],
      processCellCallback: (params) => {
        if (params.column.getColId() === 'amountMinor') {
          return formatMinor(Number(params.value));
        }
        if (params.column.getColId() === 'openedAtDate' && params.value instanceof Date) {
          return params.value.toISOString();
        }
        return params.value != null ? String(params.value) : '';
      },
    });
    toast.success('Exported Exception Queue via AG Grid Community CSV.');
  };

  const handleCellKeyDown = (event: CellKeyDownEvent<GridCaseRow>) => {
    const keyEvent = event.event as KeyboardEvent | undefined;
    if (keyEvent?.key === 'Enter' && event.data?.id) {
      navigate(`/case/${event.data.id}`);
    }
  };

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Exception queue
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Every detected case, newest first.
            </p>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <LastUpdatedLabel dataUpdatedAt={casesUpdatedAt} isFetching={casesFetching} />
            <button
              onClick={() => seedMutation.mutate()}
              disabled={seedMutation.isPending}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
            >
              {seedMutation.isPending ? 'Seeding...' : 'Sample data'}
            </button>
            <button
              onClick={() => detectMutation.mutate()}
              disabled={detectMutation.isPending}
              className="px-4 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-medium shadow-sm cursor-pointer transition-all disabled:opacity-70"
            >
              {detectMutation.isPending ? 'Scanning...' : 'Run detectors'}
            </button>
          </div>
        </div>

          {/* 4 Stat Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
            <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
              <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
                <span className="flex items-center gap-1.5">
                  Open cases
                  <InfoTip metricId="queue.openCases" size={14} />
                </span>
                <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  <CountUp value={statsData?.openCount ?? filteredRows.filter(r => r.status === 'Open').length} />
                </span>
                <span className="text-slate-500 dark:text-slate-400 font-normal text-xs">active</span>
              </div>
            </div>
            <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
              <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
                <span className="flex items-center gap-1.5">
                  Awaiting approval
                  <InfoTip metricId="queue.awaitingApproval" size={14} />
                </span>
                <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  <CountUp value={statsData?.awaitingApprovalCount ?? filteredRows.filter(r => r.status === 'Proposed').length} />
                </span>
                <span className="text-xs text-slate-400">requires review</span>
              </div>
            </div>
            <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
              <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
                <span className="flex items-center gap-1.5">
                  Duplicates blocked
                  <InfoTip metricId="queue.duplicatesBlocked" size={14} />
                </span>
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  <CountUp value={statsData?.duplicatesBlockedToday ?? 0} />
                </span>
                <span className="text-xs text-emerald-600 dark:text-emerald-400">Idempotent</span>
              </div>
            </div>
            <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
              <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
                <span className="flex items-center gap-1.5">
                  Oldest open case
                  <InfoTip metricId="queue.oldestOpenCase" size={14} />
                </span>
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
              </div>
              <div className="flex items-baseline justify-between">
                <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  <CountUp
                    value={statsData?.oldestOpenMinutes ?? 0}
                    suffix=" min"
                  />
                </div>
                <span className="text-xs text-slate-400">SLA: 60m</span>
              </div>
            </div>
          </div>

        {/* Main Table Card */}
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-4 sm:p-6 space-y-5 overflow-hidden">
          
            {/* Filter Bar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex flex-wrap items-center gap-2.5 sm:gap-3 flex-1">
                <div className="relative w-full sm:w-80">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                    <GoogleIcon name="search" size={16} />
                  </span>
                  <input
                    type="text"
                    placeholder="Search case, order or capture id"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="w-full pl-9 pr-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-xs sm:text-sm text-slate-900 dark:text-white placeholder:text-slate-400 bg-white dark:bg-[#131b26] focus:outline-none focus:ring-1 focus:ring-blue-600"
                  />
                </div>
                <div className="flex items-center gap-2 overflow-x-auto max-w-full pb-1 sm:pb-0">
                  <button
                    type="button"
                    onClick={() => setSelectedSeverity(selectedSeverity === 'All' ? 'High' : 'All')}
                    className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-normal cursor-pointer transition-colors shrink-0 ${
                      selectedSeverity !== 'All'
                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400'
                        : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 bg-white dark:bg-[#131b26]'
                    }`}
                  >
                    <span>Type</span>
                    <GoogleIcon name="expand_more" size={14} className="text-slate-400" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelectedSeverity(selectedSeverity === 'High' ? 'All' : 'High')}
                    className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-normal cursor-pointer transition-colors shrink-0 ${
                      selectedSeverity === 'High'
                        ? 'border-rose-400 bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400'
                        : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 bg-white dark:bg-[#131b26]'
                    }`}
                  >
                    <span>Severity</span>
                    <GoogleIcon name="expand_more" size={14} className="text-slate-400" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelectedStatus(selectedStatus === 'Open' ? 'All' : 'Open')}
                    className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-normal cursor-pointer transition-colors shrink-0 ${
                      selectedStatus === 'Open'
                        ? 'border-amber-400 bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400'
                        : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 bg-white dark:bg-[#131b26]'
                    }`}
                  >
                    <span>Status</span>
                    <GoogleIcon name="expand_more" size={14} className="text-slate-400" />
                  </button>
                </div>
              </div>

              <div className="flex items-center gap-2 self-start sm:self-auto shrink-0">
                <button
                  type="button"
                  onClick={() => {
                    if (gridApi) {
                      resetGridColumnState(QUEUE_COL_STATE_KEY, gridApi);
                      toast.info('Reset column order & widths.');
                    }
                  }}
                  aria-label="Reset queue table columns"
                  className="inline-flex items-center gap-1 px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300 text-xs hover:bg-slate-50 dark:hover:bg-slate-800 bg-white dark:bg-[#131b26] cursor-pointer transition-colors"
                >
                  <GoogleIcon name="restart_alt" size={14} />
                  <span>Reset layout</span>
                </button>
                <button
                  type="button"
                  onClick={handleExportCSV}
                  disabled={!gridApi || filteredRows.length === 0}
                  aria-label="Export exception queue as CSV"
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 bg-white dark:bg-[#131b26] cursor-pointer shadow-2xs transition-colors disabled:opacity-50"
                >
                  <GoogleIcon name="download" size={14} className="text-slate-500 dark:text-slate-400" />
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

          {/* AG Grid Community (Theming API + Quick Filter + Column Pinning + Pagination + CSV Export) */}
          {casesError ? (
            <ErrorState
              title="Failed to load exception queue"
              message="Could not retrieve detected exception cases from the backend."
              onRetry={() => refetchCases()}
            />
          ) : !casesLoading && filteredRows.length === 0 ? (
            <EmptyState
              title="No exception cases match the filter"
              description="Adjust your search criteria or run detectors to discover newly pending exceptions."
              action={{ label: 'Reset filters', onClick: () => { setSearchTerm(''); setSelectedSeverity('All'); setSelectedStatus('All'); } }}
            />
          ) : (
            <div
              role="region"
              aria-label="Exception Queue Data Table"
              className="w-full h-[460px] rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-xs"
            >
              <AgGridReact<GridCaseRow>
                ref={gridRef}
                theme={gridTheme}
                rowData={filteredRows}
                columnDefs={columnDefs}
                defaultColDef={defaultColDef}
                loading={casesLoading}
                quickFilterText={searchTerm}
                animateRows={true}
                pagination={true}
                paginationPageSize={10}
                paginationPageSizeSelector={[10, 20, 50]}
                rowSelection={{ mode: 'singleRow', enableClickSelection: true, checkboxes: false }}
                tooltipShowDelay={300}
                overlayNoRowsTemplate='<span class="text-xs text-slate-500">No exception cases found.</span>'
                onGridReady={onGridReady}
                onColumnMoved={persistColumns}
                onColumnResized={persistColumns}
                onColumnPinned={persistColumns}
                onSortChanged={persistColumns}
                onCellKeyDown={handleCellKeyDown}
                onRowClicked={(event) => {
                  if (event.data?.id) {
                    navigate(`/case/${event.data.id}`);
                  }
                }}
              />
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}
