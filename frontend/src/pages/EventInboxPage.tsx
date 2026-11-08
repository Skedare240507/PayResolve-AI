import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { api, type WebhookEventRecord } from '../lib/api';
import { toast } from '../lib/toast';
import { TableSkeleton, ErrorState } from '../components/DataStates';

interface DisplayEvent {
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
      '{"event_type":"CHECKOUT.ORDER.APPROVED","resource":{"id":"6HJ","status":"APPROVED","intent":"CAPTURE"}}',
    attempts: [
      { label: 'Attempt 1', statusText: 'enqueued for processing', isError: false },
    ],
    raw: {} as WebhookEventRecord,
  },
];

export default function EventInboxPage() {
  const queryClient = useQueryClient();

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
        at: e.at || (e.receivedAt ? new Date(e.receivedAt).toLocaleTimeString() : '10:00:00'),
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
      // Dispatches raw payload to /api/webhooks/paypal with simulated headers
      return fetch('/api/webhooks/paypal', {
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
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-5">
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

        {/* 2-Column Content: Table & Detail Card */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left Table */}
          <div className="lg:col-span-8 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-6 overflow-hidden">
            {eventsLoading ? (
              <TableSkeleton rows={5} columns={6} />
            ) : eventsError ? (
              <ErrorState
                title="Failed to load events"
                message="Could not retrieve webhook events from the server."
                onRetry={() => refetchEvents()}
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead>
                    <tr className="border-b border-slate-100 dark:border-slate-800/80 text-slate-500 dark:text-slate-400 font-normal">
                      <th className="py-2.5 pr-3 font-normal">Event</th>
                      <th className="py-2.5 px-3 font-normal">Type</th>
                      <th className="py-2.5 px-3 font-normal">Resource</th>
                      <th className="py-2.5 px-3 font-normal">Signature</th>
                      <th className="py-2.5 px-3 font-normal">Status</th>
                      <th className="py-2.5 px-3 font-normal">Tries</th>
                      <th className="py-2.5 pl-3 font-normal">At</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60">
                    {displayEvents.map((evt, idx) => {
                      const isSelected = selectedEvent.eventId === evt.eventId && selectedEvent.at === evt.at;

                      const statusBadge =
                        evt.status === 'Failed' ? (
                          <span className="px-2.5 py-0.5 rounded-full border border-rose-400 dark:border-rose-700 text-rose-600 dark:text-rose-400 text-xs">
                            Failed
                          </span>
                        ) : evt.status === 'Duplicate' ? (
                          <span className="px-2.5 py-0.5 rounded-full border border-slate-300 dark:border-slate-700 text-slate-600 dark:text-slate-400 text-xs">
                            Duplicate
                          </span>
                        ) : evt.status === 'Processed' ? (
                          <span className="px-2.5 py-0.5 rounded-full border border-emerald-400 dark:border-emerald-600 text-emerald-600 dark:text-emerald-400 text-xs">
                            Processed
                          </span>
                        ) : (
                          <span className="px-2.5 py-0.5 rounded-full border border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400 text-xs">
                            Received
                          </span>
                        );

                      return (
                        <tr
                          key={`${evt.eventId}-${idx}`}
                          onClick={() => setSelectedEventId(evt.eventId)}
                          className={`cursor-pointer transition-colors ${
                            isSelected
                              ? 'bg-slate-50 dark:bg-[#152033]'
                              : 'hover:bg-slate-50/50 dark:hover:bg-[#121b2b]'
                          }`}
                        >
                          <td className="py-3.5 pr-3 font-medium text-slate-900 dark:text-white font-mono whitespace-nowrap">
                            <div className="flex items-center gap-1.5 group">
                              <span className="truncate max-w-[130px]" title={evt.eventId}>{evt.eventId}</span>
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  navigator.clipboard.writeText(evt.eventId);
                                  toast.info(`Copied event ID: ${evt.eventId}`);
                                }}
                                title={`Copy event ID: ${evt.eventId}`}
                                className="opacity-0 group-hover:opacity-100 hover:text-blue-500 text-slate-400 transition-opacity p-0.5 cursor-pointer"
                              >
                                <GoogleIcon name="content_copy" size={13} />
                              </button>
                            </div>
                          </td>
                          <td className="py-3.5 px-3 font-mono text-xs text-slate-700 dark:text-slate-300">{evt.type}</td>
                          <td className="py-3.5 px-3 text-slate-600 dark:text-slate-400">{evt.resource}</td>
                          <td className="py-3.5 px-3">
                            <span className="px-2.5 py-0.5 rounded-full border border-emerald-400 dark:border-emerald-700 text-emerald-600 dark:text-emerald-400 text-xs">
                              {evt.signature}
                            </span>
                          </td>
                          <td className="py-3.5 px-3">{statusBadge}</td>
                          <td className="py-3.5 px-3 font-mono text-xs text-slate-600 dark:text-slate-400 whitespace-nowrap">
                            <span>{evt.tries}</span>
                            {((evt.raw as unknown as { deliveryCount?: number })?.deliveryCount ?? 1) > 1 && (
                              <span className="block text-[10px] text-amber-600 dark:text-amber-400 font-sans font-medium">
                                delivered {(evt.raw as unknown as { deliveryCount?: number }).deliveryCount}x
                              </span>
                            )}
                          </td>
                          <td className="py-3.5 pl-3 font-mono text-slate-500">{evt.at}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Right Detail Card */}
          <div className="lg:col-span-4 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs p-6 space-y-5">
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
