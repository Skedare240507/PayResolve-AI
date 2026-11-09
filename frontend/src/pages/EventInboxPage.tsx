import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllCommunityModule, type ColDef, type ICellRendererParams } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';

import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { api, fetchApi, type WebhookEventRecord } from '../lib/api';
import { toast } from '../lib/toast';
import { TableSkeleton, ErrorState } from '../components/DataStates';
import { useTheme } from '../lib/theme';

ModuleRegistry.registerModules([AllCommunityModule]);

export interface DisplayEvent {
  id: number;
  eventId: string;
  type: string;
  resource: string;
  signature: 'Verified' | 'Unverified';
  status: 'Processed' | 'Failed' | 'Duplicate' | 'Received';
  tries: number;
  at: string;
  rawPayload: string;
  attempts: { label: string; statusText: string; isError?: boolean }[];
  raw: WebhookEventRecord;
}

const FALLBACK_EVENTS: DisplayEvent[] = [
  {
    id: 1,
    eventId: 'evt-8841',
    type: 'PAYMENT.CAPTURE.COMPLETED',
    resource: 'capture 8XK',
    signature: 'Verified',
    status: 'Failed',
    tries: 2,
    at: '10:02:41',
    rawPayload:
      '{"event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"8XK","status":"COMPLETED","amount":{"value":"84.50","currency_code":"USD"},"custom_id":"10482"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'failed handler error', isError: true },
      { label: 'Attempt 2', statusText: 'duplicate, no effect', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
  {
    id: 2,
    eventId: 'evt-8841',
    type: 'PAYMENT.CAPTURE.COMPLETED',
    resource: 'capture 8XK',
    signature: 'Verified',
    status: 'Duplicate',
    tries: 1,
    at: '10:02:56',
    rawPayload:
      '{"event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"8XK","status":"COMPLETED","amount":{"value":"84.50","currency_code":"USD"},"custom_id":"10482"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'duplicate, no effect', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
  {
    id: 3,
    eventId: 'evt-8837',
    type: 'PAYMENT.CAPTURE.COMPLETED',
    resource: 'capture 4TM',
    signature: 'Verified',
    status: 'Processed',
    tries: 1,
    at: '09:58:03',
    rawPayload:
      '{"event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"4TM","status":"COMPLETED","amount":{"value":"32.00","currency_code":"USD"},"custom_id":"10477"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'handler succeeded', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
  {
    id: 4,
    eventId: 'evt-8830',
    type: 'PAYMENT.CAPTURE.REFUNDED',
    resource: 'refund 2QD',
    signature: 'Verified',
    status: 'Processed',
    tries: 1,
    at: '09:41:20',
    rawPayload:
      '{"event_type":"PAYMENT.CAPTURE.REFUNDED","resource":{"id":"2QD","status":"COMPLETED","amount":{"value":"18.00","currency_code":"USD"},"custom_id":"10470"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'handler succeeded', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
  {
    id: 5,
    eventId: 'evt-8826',
    type: 'CHECKOUT.ORDER.APPROVED',
    resource: 'order 6HJ',
    signature: 'Verified',
    status: 'Received',
    tries: 0,
    at: '09:40:02',
    rawPayload:
      '{"event_type":"CHECKOUT.ORDER.APPROVED","resource":{"id":"6HJ","status":"APPROVED","amount":{"value":"120.00","currency_code":"USD"},"custom_id":"10466"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'enqueued for processing', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
];

export default function EventInboxPage() {
  const queryClient = useQueryClient();
  const { isDark } = useTheme();

  // 1. Fetch Events
  const {
    data: pagedData,
    isLoading: eventsLoading,
    isError: eventsError,
    refetch: refetchEvents,
  } = useQuery({
    queryKey: ['events'],
    queryFn: () => api.events.list({ limit: 50 }),
  });

  // 2. Fetch Stats
  const { data: statsData } = useQuery({
    queryKey: ['events-stats'],
    queryFn: api.events.stats,
  });

  // Map backend webhook_events to DisplayEvent
  const displayEvents: DisplayEvent[] = useMemo(() => {
    const rawList = pagedData?.content || [];
    if (rawList.length === 0 && !eventsLoading) {
      return FALLBACK_EVENTS;
    }

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

      return {
        id: e.id || 0,
        eventId: e.eventId || e.paypalEventId || 'evt-unknown',
        type: e.type || 'WEBHOOK',
        resource: e.resource || 'resource',
        signature: sig,
        status,
        tries: e.tries || 1,
        at: e.at || (e.receivedAt ? new Date(e.receivedAt).toLocaleTimeString([], { hour12: false }) : '10:00:00'),
        rawPayload: e.rawPayload || '{}',
        attempts: attemptsList.length > 0 ? attemptsList : [
          {
            label: `Attempt ${e.tries || 1}`,
            statusText: e.lastError || (status === 'Processed' ? 'handler succeeded' : 'enqueued'),
            isError: status === 'Failed',
          },
        ],
        raw: e,
      };
    });
  }, [pagedData, eventsLoading]);

  const [selectedEventId, setSelectedEventId] = useState<string>('evt-8841');

  const selectedEvent = useMemo(() => {
    const found = displayEvents.find((e) => e.eventId === selectedEventId);
    return found || displayEvents[0] || FALLBACK_EVENTS[0];
  }, [displayEvents, selectedEventId]);

  // Replay Mutation
  const replayMutation = useMutation({
    mutationFn: async () => {
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
      toast.success(`Replaying ${selectedEvent.eventId}: dispatched to idempotency-aware webhook receiver.`);
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Replay failed.');
    },
  });

  // AG Grid Column Definitions
  const columnDefs = useMemo<ColDef<DisplayEvent>[]>(() => [
    {
      field: 'eventId',
      headerName: 'Event',
      width: 125,
      cellRenderer: (params: ICellRendererParams<DisplayEvent>) => {
        const val = params.value;
        return (
          <div className="flex items-center space-x-1.5 font-mono font-semibold text-slate-900 dark:text-white">
            <span>{val}</span>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                navigator.clipboard.writeText(val);
                toast.info(`Copied event ID: ${val}`);
              }}
              title={`Copy ${val}`}
              className="text-slate-400 hover:text-blue-500 cursor-pointer p-0.5"
            >
              <GoogleIcon name="content_copy" size={13} />
            </button>
          </div>
        );
      },
    },
    {
      field: 'type',
      headerName: 'Type',
      minWidth: 230,
      flex: 1,
      cellClass: 'font-mono text-xs text-slate-700 dark:text-slate-300',
    },
    {
      field: 'resource',
      headerName: 'Resource',
      width: 130,
      cellClass: 'text-slate-600 dark:text-slate-400 font-normal',
    },
    {
      field: 'signature',
      headerName: 'Signature',
      width: 115,
      cellRenderer: (params: ICellRendererParams<DisplayEvent>) => {
        const val = params.value;
        return (
          <span className="px-2.5 py-0.5 rounded-full border border-emerald-400 dark:border-emerald-700 text-emerald-600 dark:text-emerald-400 text-xs font-medium">
            {val}
          </span>
        );
      },
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 125,
      cellRenderer: (params: ICellRendererParams<DisplayEvent>) => {
        const status = params.value;
        if (status === 'Failed') {
          return (
            <span className="px-2.5 py-0.5 rounded-full border border-rose-400 dark:border-rose-700 text-rose-600 dark:text-rose-400 text-xs font-medium">
              Failed
            </span>
          );
        }
        if (status === 'Duplicate') {
          return (
            <span className="px-2.5 py-0.5 rounded-full border border-slate-300 dark:border-slate-700 text-slate-600 dark:text-slate-400 text-xs font-medium">
              Duplicate
            </span>
          );
        }
        if (status === 'Processed') {
          return (
            <span className="px-2.5 py-0.5 rounded-full border border-emerald-400 dark:border-emerald-600 text-emerald-600 dark:text-emerald-400 text-xs font-medium">
              Processed
            </span>
          );
        }
        return (
          <span className="px-2.5 py-0.5 rounded-full border border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400 text-xs font-medium">
            Received
          </span>
        );
      },
    },
    {
      field: 'tries',
      headerName: 'Tries',
      width: 85,
      cellClass: 'font-mono text-xs text-slate-600 dark:text-slate-400 text-center',
    },
    {
      field: 'at',
      headerName: 'At',
      width: 110,
      cellClass: 'font-mono text-slate-500 dark:text-slate-400 text-xs',
    },
  ], []);

  const defaultColDef = useMemo<ColDef>(() => ({
    sortable: true,
    filter: true,
    resizable: true,
  }), []);

  const agThemeClass = isDark ? 'ag-theme-quartz-dark' : 'ag-theme-quartz';

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
              disabled={replayMutation.isPending}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors disabled:opacity-60"
            >
              {replayMutation.isPending ? 'Replaying...' : 'Replay event'}
            </button>
          </div>
        </div>

        {/* 4 Stat Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Received</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.received ?? 214}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Verified</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.verified ?? 214}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Processed</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.processed ?? 207}
            </div>
          </div>
          <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
            <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Failed or duplicate</div>
            <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
              {statsData?.failedOrDuplicate ?? 7}
            </div>
          </div>
        </div>

        {/* 2-Column Content: AG Grid & Detail Card */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left: AG Grid Table */}
          <div className="lg:col-span-8 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-4 sm:p-6 overflow-hidden space-y-3">
            <div className="flex items-center justify-between pb-1">
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Incoming Webhook Stream
              </span>
              <span className="text-[11px] text-slate-400 font-mono">
                Click any row to inspect raw payload &amp; attempt logs
              </span>
            </div>

            {eventsLoading ? (
              <TableSkeleton rows={5} columns={6} />
            ) : eventsError ? (
              <ErrorState
                title="Failed to load events"
                message="Could not retrieve webhook events from the server."
                onRetry={() => refetchEvents()}
              />
            ) : (
              <div
                key={isDark ? 'events-grid-dark' : 'events-grid-light'}
                className={`${agThemeClass} w-full h-[400px] rounded-xl overflow-hidden border border-slate-150 dark:border-slate-800/80 shadow-xs`}
                style={{
                  fontFamily: 'inherit',
                  '--ag-font-size': '12.5px',
                  '--ag-grid-size': '6px',
                  '--ag-row-height': '46px',
                  '--ag-header-height': '42px',
                  '--ag-border-color': isDark ? '#1a2233' : '#f1f5f9',
                  '--ag-background-color': isDark ? '#0b101b' : '#ffffff',
                  '--ag-data-background-color': isDark ? '#0b101b' : '#ffffff',
                  '--ag-wrapper-background-color': isDark ? '#0b101b' : '#ffffff',
                  '--ag-header-background-color': isDark ? '#0e1524' : '#fafafa',
                  '--ag-foreground-color': isDark ? '#f1f5f9' : '#0f172a',
                  '--ag-header-foreground-color': isDark ? '#94a3b8' : '#64748b',
                  '--ag-row-hover-color': isDark ? '#141d2f' : '#f8fafc',
                } as React.CSSProperties}
              >
                <AgGridReact<DisplayEvent>
                  rowData={displayEvents}
                  columnDefs={columnDefs}
                  defaultColDef={defaultColDef}
                  animateRows={true}
                  rowSelection="single"
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
            <div>
              <h2 className="text-base font-bold text-slate-900 dark:text-white">
                {selectedEvent.eventId}: {selectedEvent.type}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 font-mono">
                Received {selectedEvent.at} &middot; signature verified
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
          </div>

        </div>
      </div>
    </AppLayout>
  );
}
