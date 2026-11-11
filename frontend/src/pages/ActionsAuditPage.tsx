import { useState, useMemo, useCallback, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
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
import { HeaderWithInfoTip } from '../components/grid/HeaderWithInfoTip';
import { CopyableIdCell, StatusPillCell } from '../components/grid/GridCellRenderers';
import { api, ApiError, type ProposedActionDto, type AuditLogEntryDto } from '../lib/api';
import { toast } from '../lib/toast';
import {
  useGridTheme,
  saveGridColumnState,
  restoreGridColumnState,
  resetGridColumnState,
} from '../lib/gridTheme';
import { CardSkeleton, ErrorState } from '../components/DataStates';

const ACTIONS_COL_STATE_KEY = 'payresolve_grid_actions_cols_v1';

interface ActionRow {
  id: number;
  caseId: string;
  actionType: string;
  status: string;
  proposedBy: string;
  decidedBy: string;
  createdAtDate: Date | null;
  description: string;
}

interface AuditRow {
  id: number;
  caseOrSubject: string;
  action: string;
  actor: string;
  subjectType: string;
  atDate: Date | null;
  atFormatted: string;
}

export default function ActionsAuditPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { gridTheme } = useGridTheme();

  const [ledgerTab, setLedgerTab] = useState<'actions' | 'audit'>('actions');
  const [quickFilter, setQuickFilter] = useState('');
  const gridRef = useRef<AgGridReact<ActionRow | AuditRow>>(null);
  const [gridApi, setGridApi] = useState<GridApi<ActionRow | AuditRow> | null>(null);

  // Fetch proposed actions
  const actionsQuery = useQuery({
    queryKey: ['actions'],
    queryFn: () => api.actions.list({ page: 0, size: 100 }),
  });

  // Fetch audit log entries
  const auditQuery = useQuery({
    queryKey: ['audit'],
    queryFn: () => api.audit.list(0, 100),
  });

  // Approve mutation
  const approveMutation = useMutation({
    mutationFn: (id: number) => api.actions.approve(id),
    onSuccess: (action) => {
      toast.success(`Action approved: ${formatActionTitle(action.type)}`);
      queryClient.invalidateQueries({ queryKey: ['actions'] });
      queryClient.invalidateQueries({ queryKey: ['audit'] });
      queryClient.invalidateQueries({ queryKey: ['cases'] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.status === 403) {
        toast.error('Only OPERATOR role can approve actions.');
        navigate('/403');
      } else {
        toast.error(err instanceof Error ? err.message : 'Failed to approve action');
      }
    },
  });

  // Reject mutation
  const rejectMutation = useMutation({
    mutationFn: (id: number) => api.actions.reject(id),
    onSuccess: (action) => {
      toast.info(`Action rejected: ${formatActionTitle(action.type)}`);
      queryClient.invalidateQueries({ queryKey: ['actions'] });
      queryClient.invalidateQueries({ queryKey: ['audit'] });
      queryClient.invalidateQueries({ queryKey: ['cases'] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.status === 403) {
        toast.error('Only OPERATOR role can reject actions.');
        navigate('/403');
      } else {
        toast.error(err instanceof Error ? err.message : 'Failed to reject action');
      }
    },
  });

  // Seed sample data mutation
  const seedMutation = useMutation({
    mutationFn: async () => {
      await api.cases.seed();
      await api.cases.detect();
    },
    onSuccess: () => {
      toast.success('Sample approval scenarios and immutable audit records active.');
      queryClient.invalidateQueries({ queryKey: ['actions'] });
      queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Failed to seed sample data');
    },
  });

  const formatActionTitle = (type?: string) => {
    switch (type) {
      case 'SYNC_ORDER_STATUS':
        return 'Sync order status to PAID';
      case 'HOLD_REFUND':
        return 'Hold second refund';
      case 'REQUEST_REVIEW':
        return 'Request human review';
      default:
        return type ? type.replace(/_/g, ' ') : 'Proposed action';
    }
  };

  const formatActionDescription = (action: ProposedActionDto) => {
    if (action.payload && typeof action.payload === 'object' && 'reason' in action.payload) {
      return String(action.payload.reason);
    }
    switch (action.type) {
      case 'SYNC_ORDER_STATUS':
        return 'Capture is completed in PayPal but the shop order is pending.';
      case 'HOLD_REFUND':
        return 'A refund already completed in PayPal. Flag the order to prevent a duplicate.';
      case 'REQUEST_REVIEW':
        return 'Discrepancy detected that requires human investigation and review.';
      default:
        return 'The AI investigator proposed this action for human review.';
    }
  };

  const formatActionStatusLabel = (status?: string) => {
    switch (status) {
      case 'PROPOSED':
      case 'AWAITING_APPROVAL':
        return 'Awaiting approval';
      case 'APPROVED':
      case 'EXECUTED':
        return 'Approved';
      case 'REJECTED':
        return 'Rejected';
      default:
        return status || 'Pending';
    }
  };

  const actionsList = useMemo(() => actionsQuery.data?.content || [], [actionsQuery.data?.content]);
  const auditEntries = useMemo(() => auditQuery.data?.content || [], [auditQuery.data?.content]);

  const actionRows: ActionRow[] = useMemo(
    () =>
      actionsList.map((a) => ({
        id: Number(a.id ?? 0),
        caseId: a.caseId || `ACT-${a.id ?? 0}`,
        actionType: formatActionTitle(a.type),
        status: formatActionStatusLabel(a.status),
        proposedBy: a.proposedBy || 'AI',
        decidedBy: a.decidedBy || '—',
        createdAtDate: a.createdAt ? new Date(a.createdAt) : null,
        description: formatActionDescription(a),
      })),
    [actionsList]
  );

  const auditRows: AuditRow[] = useMemo(
    () =>
      auditEntries.map((log: AuditLogEntryDto, idx) => {
        const dt = log.at ? new Date(log.at) : null;
        return {
          id: Number(log.id ?? idx + 1),
          caseOrSubject: log.subjectId || `LOG-${log.id ?? idx + 1}`,
          action: log.action || 'EVENT',
          actor: log.actor || 'system',
          subjectType: log.subjectType || 'CASE',
          atDate: dt,
          atFormatted: dt
            ? `${dt.toISOString().slice(0, 10)} ${dt.toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: false,
              })}`
            : '—',
        };
      }),
    [auditEntries]
  );

  const actionColumns = useMemo<ColDef<ActionRow>[]>(
    () => [
      {
        field: 'caseId',
        headerName: 'Case ID',
        pinned: 'left',
        lockPinned: true,
        width: 145,
        filter: 'agTextColumnFilter',
        tooltipField: 'caseId',
        cellRenderer: (params: ICellRendererParams<ActionRow>) => (
          <div className="flex items-center gap-1.5 min-w-0">
            <Link
              to={`/case/${params.value}`}
              onClick={(e) => e.stopPropagation()}
              className="text-blue-600 dark:text-blue-400 font-semibold hover:underline truncate"
            >
              {params.value}
            </Link>
            <CopyableIdCell value={String(params.value || '')} displayText="" />
          </div>
        ),
      },
      {
        field: 'id',
        headerName: 'Action #',
        width: 100,
        filter: 'agNumberColumnFilter',
        cellClass: 'font-mono text-xs',
      },
      {
        field: 'actionType',
        headerName: 'Action',
        minWidth: 185,
        flex: 1,
        filter: 'agTextColumnFilter',
        tooltipField: 'description',
      },
      {
        field: 'status',
        headerName: 'Status',
        width: 155,
        filter: 'agTextColumnFilter',
        headerComponent: HeaderWithInfoTip,
        headerComponentParams: { metricId: 'actions.col.auditStatus' },
        cellRenderer: (params: ICellRendererParams<ActionRow>) => (
          <StatusPillCell status={params.value} />
        ),
      },
      {
        field: 'proposedBy',
        headerName: 'Proposed By',
        width: 120,
        filter: 'agTextColumnFilter',
      },
      {
        field: 'decidedBy',
        headerName: 'Decided By',
        width: 120,
        filter: 'agTextColumnFilter',
      },
      {
        field: 'createdAtDate',
        headerName: 'Created',
        width: 140,
        filter: 'agDateColumnFilter',
        cellClass: 'font-mono text-xs',
        valueFormatter: (params: ValueFormatterParams<ActionRow, Date>) =>
          params.value ? params.value.toLocaleTimeString([], { hour12: false }) : '',
        tooltipValueGetter: (params) => (params.value ? params.value.toISOString() : ''),
      },
    ],
    []
  );

  const auditColumns = useMemo<ColDef<AuditRow>[]>(
    () => [
      {
        field: 'caseOrSubject',
        headerName: 'Subject / Case',
        pinned: 'left',
        lockPinned: true,
        width: 155,
        filter: 'agTextColumnFilter',
        tooltipField: 'caseOrSubject',
        cellRenderer: (params: ICellRendererParams<AuditRow>) => (
          <CopyableIdCell value={String(params.value || '')} />
        ),
      },
      {
        field: 'id',
        headerName: 'Entry #',
        width: 95,
        filter: 'agNumberColumnFilter',
        cellClass: 'font-mono text-xs',
      },
      {
        field: 'action',
        headerName: 'Audit Event',
        minWidth: 170,
        flex: 1,
        filter: 'agTextColumnFilter',
        tooltipField: 'action',
      },
      {
        field: 'actor',
        headerName: 'Actor',
        width: 120,
        filter: 'agTextColumnFilter',
      },
      {
        field: 'subjectType',
        headerName: 'Scope',
        width: 110,
        filter: 'agTextColumnFilter',
      },
      {
        field: 'atDate',
        headerName: 'Timestamp',
        width: 175,
        filter: 'agDateColumnFilter',
        cellClass: 'font-mono text-xs',
        valueFormatter: (params: ValueFormatterParams<AuditRow, Date>) =>
          params.data?.atFormatted || (params.value ? params.value.toISOString() : ''),
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

  const storageKey = `${ACTIONS_COL_STATE_KEY}_${ledgerTab}`;

  const onGridReady = useCallback(
    (event: GridReadyEvent<ActionRow | AuditRow>) => {
      setGridApi(event.api);
      restoreGridColumnState(storageKey, event.api);
    },
    [storageKey]
  );

  const persistColumns = useCallback(() => {
    if (gridApi) {
      saveGridColumnState(storageKey, gridApi);
    }
  }, [gridApi, storageKey]);

  const handleExportCsv = () => {
    if (!gridApi) return;
    gridApi.exportDataAsCsv({
      fileName: ledgerTab === 'actions' ? 'payresolve_proposed_actions.csv' : 'payresolve_audit_log.csv',
    });
    toast.success(`Exported ${ledgerTab} CSV via AG Grid Community.`);
  };

  const handleCellKeyDown = (event: CellKeyDownEvent<ActionRow | AuditRow>) => {
    const keyEvent = event.event as KeyboardEvent | undefined;
    if (keyEvent?.key === 'Enter' && event.data && 'caseId' in event.data && event.data.caseId.startsWith('PR-')) {
      navigate(`/case/${event.data.caseId}`);
    }
  };

  const isLedgerLoading = ledgerTab === 'actions' ? actionsQuery.isLoading : auditQuery.isLoading;
  const isLedgerError = ledgerTab === 'actions' ? actionsQuery.isError : auditQuery.isError;
  const activeRowsCount = ledgerTab === 'actions' ? actionRows.length : auditRows.length;

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Actions and audit
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              The AI only proposes. Nothing runs until an operator approves.
            </p>
          </div>
          <div>
            <button
              onClick={() => seedMutation.mutate()}
              disabled={seedMutation.isPending}
              title="Demo helper: populate canonical exception scenarios & pending proposals"
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors"
            >
              {seedMutation.isPending ? 'Seeding...' : 'Demo helper'}
            </button>
          </div>
        </div>

        {/* 2-Column Content: Action Approval Cards & AG Grid Community Ledger */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Left: Human-in-the-Loop Action Approval Queue */}
          <div className="lg:col-span-5 space-y-4">
            <div className="flex items-center justify-between pb-1">
              <div className="flex items-center gap-1.5">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">Proposed actions</h2>
                <InfoTip
                  metricId="actions.pendingCount"
                  values={{
                    value: actionsList.filter((a) => a.status === 'PROPOSED' || a.status === 'AWAITING_APPROVAL').length,
                    customDetail: `${actionsList.filter((a) => a.status === 'PROPOSED' || a.status === 'AWAITING_APPROVAL').length} actions awaiting human approval`,
                  }}
                  size={15}
                />
              </div>
              <span className="text-xs text-slate-500 font-mono">
                {actionsList.filter((a) => a.status === 'PROPOSED' || a.status === 'AWAITING_APPROVAL').length} pending
              </span>
            </div>
            {actionsQuery.isLoading ? (
              <div className="space-y-4">
                <CardSkeleton />
                <CardSkeleton />
              </div>
            ) : actionsQuery.isError ? (
              <ErrorState
                title="Failed to load proposed actions"
                message="Could not retrieve action proposals."
                onRetry={() => actionsQuery.refetch()}
              />
            ) : actionsList.length === 0 ? (
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-8 text-center space-y-3 shadow-2xs">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">No pending proposed actions</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                  When the AI investigator suggests an action or a detector flags an anomaly, proposals will appear here for human operator review.
                </p>
                <button
                  onClick={() => seedMutation.mutate()}
                  className="px-4 py-2 rounded-lg bg-[#0066cc] text-white text-xs font-medium hover:bg-blue-600 cursor-pointer"
                >
                  Seed sample actions
                </button>
              </div>
            ) : (
              actionsList.map((act) => {
                const statusLabel = formatActionStatusLabel(act.status);
                const isPending = act.status === 'PROPOSED' || act.status === 'AWAITING_APPROVAL';
                const isActionMutating =
                  (approveMutation.isPending && approveMutation.variables === act.id) ||
                  (rejectMutation.isPending && rejectMutation.variables === act.id);

                return (
                  <div
                    key={act.id}
                    className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 space-y-3.5 shadow-2xs"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2.5">
                        <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                          {formatActionTitle(act.type)}
                        </h2>
                        <StatusPillCell status={statusLabel} />
                      </div>
                      <span className="text-xs text-slate-500 dark:text-slate-400 font-mono">
                        {act.caseId || `Action #${act.id}`}
                      </span>
                    </div>

                    <p className="text-xs text-slate-600 dark:text-slate-300">
                      {formatActionDescription(act)}
                    </p>

                    <div className="flex items-center space-x-3 pt-1">
                      {isPending ? (
                        <>
                          <button
                            onClick={() => act.id && approveMutation.mutate(act.id)}
                            disabled={isActionMutating}
                            className="px-4 py-1.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs shadow-sm cursor-pointer transition-all disabled:opacity-50"
                          >
                            {approveMutation.isPending && approveMutation.variables === act.id ? 'Approving...' : 'Approve'}
                          </button>
                          <button
                            onClick={() => act.id && rejectMutation.mutate(act.id)}
                            disabled={isActionMutating}
                            className="px-4 py-1.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs shadow-2xs cursor-pointer transition-all disabled:opacity-50"
                          >
                            {rejectMutation.isPending && rejectMutation.variables === act.id ? 'Rejecting...' : 'Reject'}
                          </button>
                        </>
                      ) : (
                        <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">
                          {statusLabel}
                          {act.decidedBy ? ` by ${act.decidedBy}` : ''}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Right: AG Grid Community Ledger (Actions & Immutable Audit Trail) */}
          <div className="lg:col-span-7 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 space-y-4 shadow-2xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-bold text-slate-900 dark:text-white">
                  Actions &amp; Audit Ledger
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  Append-only records powered by AG Grid Community.
                </p>
              </div>
              <div className="flex items-center gap-1 p-1 rounded-lg bg-slate-100 dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setLedgerTab('actions')}
                  className={`px-3 py-1 rounded-md text-xs font-medium cursor-pointer transition-colors ${
                    ledgerTab === 'actions'
                      ? 'bg-white dark:bg-[#1e293b] text-slate-900 dark:text-white shadow-xs'
                      : 'text-slate-500 dark:text-slate-400'
                  }`}
                >
                  Actions ({actionRows.length})
                </button>
                <button
                  type="button"
                  onClick={() => setLedgerTab('audit')}
                  className={`px-3 py-1 rounded-md text-xs font-medium cursor-pointer transition-colors ${
                    ledgerTab === 'audit'
                      ? 'bg-white dark:bg-[#1e293b] text-slate-900 dark:text-white shadow-xs'
                      : 'text-slate-500 dark:text-slate-400'
                  }`}
                >
                  Audit log ({auditRows.length})
                </button>
              </div>
            </div>

            {/* Grid Controls: Quick Filter, Reset Columns, Export CSV */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="relative flex-1 max-w-xs">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <GoogleIcon name="search" size={15} />
                </span>
                <input
                  type="text"
                  aria-label="Quick filter actions or audit rows"
                  placeholder="Quick filter case, action, actor..."
                  value={quickFilter}
                  onChange={(e) => setQuickFilter(e.target.value)}
                  className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-xs text-slate-900 dark:text-white bg-white dark:bg-[#131b26]"
                />
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    if (gridApi) {
                      resetGridColumnState(storageKey, gridApi);
                      toast.info('Reset ledger column layout.');
                    }
                  }}
                  aria-label="Reset actions table columns"
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300 text-xs hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  <GoogleIcon name="restart_alt" size={14} />
                  <span>Reset</span>
                </button>
                <button
                  type="button"
                  onClick={handleExportCsv}
                  disabled={!gridApi || activeRowsCount === 0}
                  aria-label="Export ledger table as CSV"
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer disabled:opacity-50"
                >
                  <GoogleIcon name="download" size={14} />
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

            {isLedgerError ? (
              <ErrorState
                title="Failed to load ledger records"
                message="Could not load records from backend."
                onRetry={() => (ledgerTab === 'actions' ? actionsQuery.refetch() : auditQuery.refetch())}
              />
            ) : (
              <div
                role="region"
                aria-label="Actions and Audit Data Table"
                className="w-full h-[460px] rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-xs"
              >
                {ledgerTab === 'actions' ? (
                  <AgGridReact<ActionRow>
                    ref={gridRef as React.RefObject<AgGridReact<ActionRow>>}
                    key="actions-grid"
                    theme={gridTheme}
                    rowData={actionRows}
                    columnDefs={actionColumns}
                    defaultColDef={defaultColDef}
                    loading={isLedgerLoading}
                    quickFilterText={quickFilter}
                    animateRows={true}
                    pagination={true}
                    paginationPageSize={10}
                    paginationPageSizeSelector={[10, 20, 50]}
                    rowSelection={{ mode: 'singleRow', enableClickSelection: true, checkboxes: false }}
                    tooltipShowDelay={300}
                    overlayNoRowsTemplate='<span class="text-xs text-slate-500">No proposed actions recorded.</span>'
                    onGridReady={onGridReady as (e: GridReadyEvent<ActionRow>) => void}
                    onColumnMoved={persistColumns}
                    onColumnResized={persistColumns}
                    onColumnPinned={persistColumns}
                    onSortChanged={persistColumns}
                    onCellKeyDown={handleCellKeyDown as (e: CellKeyDownEvent<ActionRow>) => void}
                  />
                ) : (
                  <AgGridReact<AuditRow>
                    ref={gridRef as React.RefObject<AgGridReact<AuditRow>>}
                    key="audit-grid"
                    theme={gridTheme}
                    rowData={auditRows}
                    columnDefs={auditColumns}
                    defaultColDef={defaultColDef}
                    loading={isLedgerLoading}
                    quickFilterText={quickFilter}
                    animateRows={true}
                    pagination={true}
                    paginationPageSize={10}
                    paginationPageSizeSelector={[10, 20, 50]}
                    rowSelection={{ mode: 'singleRow', enableClickSelection: true, checkboxes: false }}
                    tooltipShowDelay={300}
                    overlayNoRowsTemplate='<span class="text-xs text-slate-500">No audit records logged.</span>'
                    onGridReady={onGridReady as (e: GridReadyEvent<AuditRow>) => void}
                    onColumnMoved={persistColumns}
                    onColumnResized={persistColumns}
                    onColumnPinned={persistColumns}
                    onSortChanged={persistColumns}
                  />
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </AppLayout>
  );
}
