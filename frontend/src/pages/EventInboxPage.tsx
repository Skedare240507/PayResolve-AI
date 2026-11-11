import { useState, useMemo, useCallback, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import type {
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
import { api, fetchApi, type WebhookEventRecord } from '../lib/api';
import { toast } from '../lib/toast';
import { ErrorState, EmptyState } from '../components/DataStates';
import {
  useGridTheme,
  saveGridColumnState,
  restoreGridColumnState,
  resetGridColumnState,
} from '../lib/gridTheme';

const EVENTS_COL_STATE_KEY = 'payresolve_grid_events_cols_v1';

export interface DisplayEvent {
  id: number;
  eventId: string;
  type: string;
  resource: string;
  signature: 'Verified' | 'Unverified';
  status: 'Processed' | 'Failed' | 'Duplicate' | 'Received';
  tries: number;
  receivedAtDate: Date | null;
  at: string;
  rawPayload: string;
  attempts: { label: string; statusText: string; isError?: boolean }[];
  raw: WebhookEventRecord;
}

export default function EventInboxPage() {
  const queryClient = useQueryClient();
  const { gridTheme } = useGridTheme();
  const gridRef = useRef<AgGridReact<DisplayEvent>>(null);
  const [gridApi, setGridApi] = useState<GridApi<DisplayEvent> | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  // 1. Fetch Events
  const {
    data: pagedData,
    isLoading: eventsLoading,
    isError: eventsError,
    refetch: refetchEvents,
  } = useQuery({
    queryKey: ['events'],
    queryFn: () => api.events.list({ limit: 100 }),
  });

  // 2. Fetch Stats
  const { data: statsData } = useQuery({
    queryKey: ['events-stats'],
    queryFn: api.events.stats,
  });

  // Map backend webhook_events to DisplayEvent (real rows only, no mock fallback)
  const displayEvents: DisplayEvent[] = useMemo(() => {
    const rawList = pagedData?.content || [];
    return rawList.map((e) => {
      const status: DisplayEvent['status'] =
        e.status === 'PROCESSED' || e.status === 'Processed'
          ? 'Processed'
          : e.status === 'FAILED' || e.status === 'Failed'
          ? 'Failed'
          : e.status === 'DUPLICATE' || e.status === 'Duplicate'
          ? 'Duplicate'
          : 'Received';

      const sig: 'Verified' | 'Unverified' =
        e.signature === 'Verified' ? 'Verified' : 'Unverified';

      const attemptsList = (e.attempts || []).map((att) => ({
        label: att.label || 'Attempt 1',
        statusText: att.statusText || 'processed',
        isError: !!att.isError,
      }));

      const recDate = e.receivedAt ? new Date(e.receivedAt) : null;

      return {
        id: e.id || 0,
        eventId: e.eventId || e.paypalEventId || 'evt-unknown',
        type: e.type || 'WEBHOOK',
        resource: e.resource || 'resource',
        signature: sig,
        status,
        tries: Number(e.tries ?? 1),
        receivedAtDate: recDate,
        at: e.at || (recDate ? recDate.toLocaleTimeString([], { hour12: false }) : '—'),
        rawPayload: e.rawPayload || '{}',
        attempts:
          attemptsList.length > 0
            ? attemptsList
            : [
                {
                  label: `Attempt ${e.tries || 1}`,
                  statusText: e.lastError || (status === 'Processed' ? 'handler succeeded' : 'enqueued'),
                  isError: status === 'Failed',
                },
              ],
        raw: e,
      };
    });
  }, [pagedData]);

  const [selectedEventId, setSelectedEventId] = useState<string>('');

  const selectedEvent = useMemo(() => {
    if (displayEvents.length === 0) return null;
    return displayEvents.find((e) => e.eventId === selectedEventId) || displayEvents[0];
  }, [displayEvents, selectedEventId]);

  // Replay Mutation
  const replayMutation = useMutation({
    mutationFn: async () => {
      if (!selectedEvent) throw new Error('Select an event before replaying.');
      return fetchApi('/api/webhooks/paypal', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'PAYPAL-AUTH-ALGO': 'SHA256withRSA',
          'PAYPAL-TRANSMISSION-ID': 'tx-' + Math.random().toString(36).substring(2, 8),
          'PAYPAL-TRANSMISSION-TIME': new Date().toISOString(),
          'PAYPAL-CERT-URL': 'https://api.sandbox.paypal.com/v1/notifications/certs/CERT-360',
          'PAYPAL-TRANSMISSION-SIG': 'simulated-sig',
        },
        body: selectedEvent.rawPayload,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] });
      queryClient.invalidateQueries({ queryKey: ['events-stats'] });
      toast.success(
        `Replaying ${selectedEvent?.eventId || 'event'}: dispatched to idempotency-aware webhook receiver.`
      );
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Replay failed.');
    },
  });

  // AG Grid Community Column Definitions (pinned ID left, text/number/date filters, icon+text pills, copy button)
  const columnDefs = useMemo<ColDef<DisplayEvent>[]>(
    () => [
      {
        field: 'eventId',
        headerName: 'Event ID',
        pinned: 'left',
        lockPinned: true,
        width: 155,
        minWidth: 130,
        filter: 'agTextColumnFilter',
        tooltipField: 'eventId',
        cellRenderer: (params: ICellRendererParams<DisplayEvent>) => (
          <CopyableIdCell value={params.value} />
        ),
      },
      {
        field: 'type',
        headerName: 'Type',
        minWidth: 220,
        flex: 1.2,
        filter: 'agTextColumnFilter',
        tooltipField: 'type',
        cellClass: 'font-mono text-xs',
      },
      {
        field: 'resource',
        headerName: 'Resource',
        width: 145,
        filter: 'agTextColumnFilter',
        tooltipField: 'resource',
      },
      {
        field: 'signature',
        headerName: 'Signature',
        width: 140,
        filter: 'agTextColumnFilter',
        headerComponent: HeaderWithInfoTip,
        headerComponentParams: { metricId: 'events.col.signatureStatus' },
        cellRenderer: (params: ICellRendererParams<DisplayEvent>) => (
          <StatusPillCell status={params.value} />
        ),
      },
      {
        field: 'status',
        headerName: 'Status',
        width: 135,
        filter: 'agTextColumnFilter',
        cellRenderer: (params: ICellRendererParams<DisplayEvent>) => (
          <StatusPillCell status={params.value} />
        ),
      },
      {
        field: 'tries',
        headerName: 'Tries',
        width: 105,
        filter: 'agNumberColumnFilter',
        headerComponent: HeaderWithInfoTip,
        headerComponentParams: { metricId: 'events.col.tries' },
        cellClass: 'font-mono text-xs text-center',
      },
      {
        field: 'receivedAtDate',
        headerName: 'Received At',
        width: 150,
        filter: 'agDateColumnFilter',
        cellClass: 'font-mono text-xs',
        valueFormatter: (params: ValueFormatterParams<DisplayEvent, Date>) =>
          params.data?.at || (params.value ? params.value.toLocaleTimeString([], { hour12: false }) : ''),
        tooltipValueGetter: (params) => (params.value ? params.value.toISOString() : ''),
      },
    ],
    []
  );

  const defaultColDef = useMemo<ColDef<DisplayEvent>>(
    () => ({
      sortable: true,
      filter: true,
      resizable: true,
    }),
    []
  );

  const onGridReady = useCallback((event: GridReadyEvent<DisplayEvent>) => {
    setGridApi(event.api);
    restoreGridColumnState(EVENTS_COL_STATE_KEY, event.api);
  }, []);

  const persistColumns = useCallback(() => {
    if (gridApi) {
      saveGridColumnState(EVENTS_COL_STATE_KEY, gridApi);
    }
  }, [gridApi]);

  const handleExportCsv = () => {
    if (!gridApi) return;
    gridApi.exportDataAsCsv({
      fileName: 'payresolve_webhook_events.csv',
      columnKeys: ['eventId', 'type', 'resource', 'signature', 'status', 'tries', 'receivedAtDate'],
      processCellCallback: (params) => {
        if (params.column.getColId() === 'receivedAtDate' && params.value instanceof Date) {
          return params.value.toISOString();
        }
        return params.value != null ? String(params.value) : '';
      },
    });
    toast.success('Exported Event Inbox CSV via AG Grid Community.');
  };

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Event inbox
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Every PayPal webhook, stored raw before it is processed.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => {
                queryClient.invalidateQueries({ queryKey: ['events'] });
                queryClient.invalidateQueries({ queryKey: ['events-stats'] });
                toast.info('Event stream refreshed from server.');
              }}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs"
            >
              Refresh stream
            </button>
            <button
              onClick={() => replayMutation.mutate()}
              disabled={replayMutation.isPending || !selectedEvent}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors disabled:opacity-60"
            >
              {replayMutation.isPending ? 'Replaying...' : 'Replay event'}
            </button>
          </div>
        </div>

        {/* 4 Stat Cards (real aggregates only) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
              <span>Received</span>
              <InfoTip metricId="events.received" values={{ value: statsData?.received ?? 0 }} />
            </div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.received ?? 0}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
              <span>Verified</span>
              <InfoTip metricId="events.verified" values={{ value: statsData?.verified ?? 0 }} />
            </div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.verified ?? 0}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
              <span>Processed</span>
              <InfoTip metricId="events.processed" values={{ value: statsData?.processed ?? 0 }} />
            </div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.processed ?? 0}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="flex items-center justify-between text-xs font-normal text-slate-500 dark:text-slate-400">
              <span>Failed or duplicate</span>
              <InfoTip metricId="events.failedDuplicate" values={{ value: statsData?.failedOrDuplicate ?? 0 }} />
            </div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.failedOrDuplicate ?? 0}
            </div>
          </div>
        </div>

        {/* 2-Column Content: AG Grid & Detail Card */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left: AG Grid Table */}
          <div className="lg:col-span-8 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-4 sm:p-6 overflow-hidden space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-1">
              <div className="relative flex-1 max-w-xs">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <GoogleIcon name="search" size={15} />
                </span>
                <input
                  type="text"
                  aria-label="Quick filter webhook events"
                  placeholder="Quick filter event ID, type, resource..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-xs text-slate-900 dark:text-white bg-white dark:bg-[#131b26]"
                />
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    if (gridApi) {
                      resetGridColumnState(EVENTS_COL_STATE_KEY, gridApi);
                      toast.info('Reset event grid columns.');
                    }
                  }}
                  aria-label="Reset event inbox columns"
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300 text-xs hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                >
                  <GoogleIcon name="restart_alt" size={14} />
                  <span>Reset</span>
                </button>
                <button
                  type="button"
                  onClick={handleExportCsv}
                  disabled={!gridApi || displayEvents.length === 0}
                  aria-label="Export webhook events as CSV"
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer disabled:opacity-50"
                >
                  <GoogleIcon name="download" size={14} />
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

            {eventsError ? (
              <ErrorState
                title="Failed to load events"
                message="Could not retrieve webhook events from the server."
                onRetry={() => refetchEvents()}
              />
            ) : !eventsLoading && displayEvents.length === 0 ? (
              <EmptyState
                title="No webhook events recorded yet"
                description="Send a checkout in Demo Shop or trigger webhook verification to stream incoming PayPal events here."
              />
            ) : (
              <div
                role="region"
                aria-label="Webhook Event Inbox Table"
                className="w-full h-[460px] rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 shadow-xs"
              >
                <AgGridReact<DisplayEvent>
                  ref={gridRef}
                  theme={gridTheme}
                  rowData={displayEvents}
                  columnDefs={columnDefs}
                  defaultColDef={defaultColDef}
                  loading={eventsLoading}
                  quickFilterText={searchTerm}
                  animateRows={true}
                  pagination={true}
                  paginationPageSize={10}
                  paginationPageSizeSelector={[10, 20, 50]}
                  rowSelection={{ mode: 'singleRow', enableClickSelection: true, checkboxes: false }}
                  tooltipShowDelay={300}
                  overlayNoRowsTemplate='<span class="text-xs text-slate-500">No webhook events matched.</span>'
                  onGridReady={onGridReady}
                  onColumnMoved={persistColumns}
                  onColumnResized={persistColumns}
                  onColumnPinned={persistColumns}
                  onSortChanged={persistColumns}
                  onRowSelected={(event) => {
                    if (event.node.isSelected() && event.data?.eventId) {
                      setSelectedEventId(event.data.eventId);
                    }
                  }}
                  onRowClicked={(event) => {
                    if (event.data?.eventId) {
                      setSelectedEventId(event.data.eventId);
                    }
                  }}
                />
              </div>
            )}
          </div>

          {/* Right Detail Card */}
          <div className="lg:col-span-4 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-4 sm:p-6 space-y-5">
            {selectedEvent ? (
              <>
                <div>
                  <h2 className="text-base font-bold text-slate-900 dark:text-white truncate" title={selectedEvent.type}>
                    {selectedEvent.eventId}: {selectedEvent.type}
                  </h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 font-mono">
                    Received {selectedEvent.at} &middot; {selectedEvent.signature}
                  </p>
                </div>

                {/* Attempts / Processing Log */}
                <div className="space-y-2">
                  <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 block">
                    Processing log
                  </span>
                  <div className="space-y-1.5 text-xs font-mono">
                    {selectedEvent.attempts.map((att, idx) => (
                      <div key={idx} className="flex items-center justify-between text-slate-600 dark:text-slate-400">
                        <span>{att.label}:</span>
                        <span className={att.isError ? 'text-rose-500 dark:text-rose-400 font-semibold' : ''}>
                          {att.statusText}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Raw JSON Box with Pretty-Print & Copy */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                      Raw JSON payload
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        let formatted = selectedEvent.rawPayload;
                        try {
                          formatted = JSON.stringify(JSON.parse(selectedEvent.rawPayload), null, 2);
                        } catch {}
                        navigator.clipboard.writeText(formatted);
                        toast.info('Raw JSON payload copied to clipboard');
                      }}
                      className="flex items-center space-x-1 text-[11px] text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
                    >
                      <GoogleIcon name="content_copy" size={12} />
                      <span>Copy JSON</span>
                    </button>
                  </div>
                  <div className="p-3 bg-slate-950 text-slate-300 font-mono text-xs rounded-xl overflow-x-auto max-h-64 border border-slate-800">
                    <pre className="whitespace-pre">
                      {(() => {
                        try {
                          return JSON.stringify(JSON.parse(selectedEvent.rawPayload), null, 2);
                        } catch {
                          return selectedEvent.rawPayload;
                        }
                      })()}
                    </pre>
                  </div>
                </div>
              </>
            ) : (
              <div className="py-12 text-center space-y-2">
                <GoogleIcon name="webhook" size={28} className="text-slate-400 mx-auto" />
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Select a webhook event row to inspect its raw JSON payload and delivery attempts.
                </p>
              </div>
            )}
          </div>

        </div>
      </div>
    </AppLayout>
  );
}
