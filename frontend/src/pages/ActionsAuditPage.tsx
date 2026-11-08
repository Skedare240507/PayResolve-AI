import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { AppLayout } from '../components/AppLayout';
import { api, ApiError, type ProposedActionDto, type AuditLogEntryDto } from '../lib/api';
import { toast } from '../lib/toast';
import { CardSkeleton, TableSkeleton } from '../components/DataStates';

export default function ActionsAuditPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  // Fetch proposed actions
  const actionsQuery = useQuery({
    queryKey: ['actions'],
    queryFn: () => api.actions.list({ page: 0, size: 20 }),
  });

  // Fetch audit log entries
  const auditQuery = useQuery({
    queryKey: ['audit'],
    queryFn: () => api.audit.list(0, 20),
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

  const formatActionStatus = (status?: string) => {
    switch (status) {
      case 'PROPOSED':
        return { label: 'Awaiting approval', color: 'border-amber-500/60 text-amber-600 dark:text-amber-400' };
      case 'APPROVED':
        return { label: 'Approved', color: 'border-emerald-500/60 text-emerald-600 dark:text-emerald-400' };
      case 'REJECTED':
        return { label: 'Rejected', color: 'border-slate-400 text-slate-500 dark:text-slate-400' };
      default:
        return { label: status || 'Pending', color: 'border-slate-400 text-slate-500 dark:text-slate-400' };
    }
  };

  const formatAuditTime = (timestamp?: string) => {
    if (!timestamp) return '--:--';
    try {
      const date = new Date(timestamp);
      const dateStr = date.toISOString().slice(0, 10);
      const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
      return `${dateStr} ${timeStr}`;
    } catch {
      return timestamp;
    }
  };

  const actionsList = actionsQuery.data?.content || [];
  const auditEntries = auditQuery.data?.content || [];

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

        {/* 2-Column Content: Proposed Actions & Audit Log */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left: Action Cards */}
          <div className="lg:col-span-7 space-y-5">
            {actionsQuery.isLoading ? (
              <div className="space-y-4">
                <CardSkeleton />
                <CardSkeleton />
              </div>
            ) : actionsQuery.isError ? (
              <div className="p-6 bg-white dark:bg-[#0e1522] rounded-2xl border border-rose-300 dark:border-rose-900/60 text-center">
                <p className="text-sm text-rose-600 dark:text-rose-400 font-medium">Failed to load proposed actions.</p>
                <button
                  onClick={() => actionsQuery.refetch()}
                  className="mt-3 px-3 py-1.5 text-xs rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300"
                >
                  Retry
                </button>
              </div>
            ) : actionsList.length === 0 ? (
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-8 text-center space-y-3 shadow-2xs">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">No pending proposed actions</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto">
                  When the AI investigator suggests an action or a detector flags an anomaly, proposals will appear here for human operator review.
                </p>
                <button
                  onClick={() => seedMutation.mutate()}
                  className="px-4 py-2 rounded-lg bg-[#0066cc] text-white text-xs font-medium hover:bg-blue-600"
                >
                  Seed sample actions
                </button>
              </div>
            ) : (
              actionsList.map((act) => {
                const statusMeta = formatActionStatus(act.status);
                const isPending = act.status === 'PROPOSED';
                const isActionMutating =
                  (approveMutation.isPending && approveMutation.variables === act.id) ||
                  (rejectMutation.isPending && rejectMutation.variables === act.id);

                return (
                  <div
                    key={act.id}
                    className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center space-x-3">
                        <h2 className="text-sm sm:text-base font-bold text-slate-900 dark:text-white">
                          {formatActionTitle(act.type)}
                        </h2>
                        <span className={`px-3 py-0.5 rounded-full border text-xs font-normal ${statusMeta.color}`}>
                          {statusMeta.label}
                        </span>
                      </div>
                      <span className="text-xs text-slate-500 dark:text-slate-400 font-mono">
                        {act.caseId || `Action #${act.id}`}
                      </span>
                    </div>

                    <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300">
                      {formatActionDescription(act)}
                    </p>

                    <div className="flex items-center space-x-3 pt-1">
                      {isPending ? (
                        <>
                          <button
                            onClick={() => act.id && approveMutation.mutate(act.id)}
                            disabled={isActionMutating}
                            className="px-5 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all disabled:opacity-50"
                          >
                            {approveMutation.isPending && approveMutation.variables === act.id ? 'Approving...' : 'Approve'}
                          </button>
                          <button
                            onClick={() => act.id && rejectMutation.mutate(act.id)}
                            disabled={isActionMutating}
                            className="px-5 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-2xs cursor-pointer transition-all disabled:opacity-50"
                          >
                            {rejectMutation.isPending && rejectMutation.variables === act.id ? 'Rejecting...' : 'Reject'}
                          </button>
                        </>
                      ) : (
                        <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">
                          {act.status === 'APPROVED' ? 'Approved' : 'Rejected'}
                          {act.decidedBy ? ` by ${act.decidedBy}` : ''}
                        </span>
                      )}
                      <span className="text-xs text-slate-500 dark:text-slate-400 ml-2">
                        Cited: capture, order
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Right: Audit Log Card */}
          <div className="lg:col-span-5 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-6 space-y-4 shadow-2xs">
            <div>
              <h2 className="text-base font-bold text-slate-900 dark:text-white">Audit log</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Append-only. Every proposal and decision is recorded.
              </p>
            </div>

            <div className="overflow-x-auto">
              {auditQuery.isLoading ? (
                <TableSkeleton rows={4} columns={4} />
              ) : auditQuery.isError ? (
                <div className="py-6 text-center text-xs text-rose-500">Failed to load audit records.</div>
              ) : auditEntries.length === 0 ? (
                <div className="py-8 text-center text-xs text-slate-400">No audit records logged yet.</div>
              ) : (
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-100 dark:border-slate-800/80 text-slate-500 dark:text-slate-400">
                      <th className="py-2.5 pr-2 font-normal">Time</th>
                      <th className="py-2.5 px-2 font-normal">Actor</th>
                      <th className="py-2.5 px-2 font-normal">Action</th>
                      <th className="py-2.5 pl-2 font-normal">Subject</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60 font-mono">
                    {auditEntries.map((log: AuditLogEntryDto, idx: number) => (
                      <tr key={log.id ?? idx} className="hover:bg-slate-50/50 dark:hover:bg-[#121b2b]">
                        <td className="py-3 pr-2 text-slate-500 dark:text-slate-400">
                          {formatAuditTime(log.at)}
                        </td>
                        <td className="py-3 px-2 text-slate-700 dark:text-slate-300">
                          {log.actor || 'system'}
                        </td>
                        <td className="py-3 px-2 text-slate-800 dark:text-slate-200 font-sans text-[11px] sm:text-xs">
                          {log.action}
                        </td>
                        <td className="py-3 pl-2 text-slate-600 dark:text-slate-400 font-sans text-xs">
                          {log.subjectType ? `${log.subjectType} ` : ''}{log.subjectId || ''}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

        </div>
      </div>
    </AppLayout>
  );
}
