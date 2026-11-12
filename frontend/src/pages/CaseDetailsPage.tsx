import { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AppLayout } from '../components/AppLayout';
import { EvidenceGraphView } from '../components/EvidenceGraphView';
import { GoogleIcon } from '../components/GoogleIcon';
import { InfoTip } from '../components/InfoTip';
import { BarChartCard } from '../components/charts/BarChartCard';
import { DonutChartCard } from '../components/charts/DonutChartCard';
import { SEMANTIC_COLORS } from '../lib/chartTheme';
import { api, ApiError, type InvestigationSummaryDto, type ProposedActionDto } from '../lib/api';
import { toast } from '../lib/toast';
import { TableSkeleton, ErrorState, EmptyState } from '../components/DataStates';

function calculateAge(dateStr?: string): string {
  if (!dateStr) return '1 min';
  const diffMs = Date.now() - new Date(dateStr).getTime();
  const mins = Math.max(1, Math.floor(diffMs / 60000));
  if (mins < 60) return `${mins} min`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h`;
  const days = Math.floor(hours / 24);
  return `${days} d`;
}

export default function CaseDetailsPage() {
  const { id = 'PR-0142' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'graph' | 'timeline' | 'raw'>('graph');
  const [investigationResult, setInvestigationResult] = useState<InvestigationSummaryDto | null>(null);

  // Dispute checklist states
  const [orderReceipt, setOrderReceipt] = useState(true);
  const [proofOfDelivery, setProofOfDelivery] = useState(false);
  const [customerMessages, setCustomerMessages] = useState(false);

  // 1. Fetch Case Details
  const {
    data: caseData,
    isLoading: caseLoading,
    isError: caseError,
    refetch: refetchCase,
  } = useQuery({
    queryKey: ['case', id],
    queryFn: () => api.cases.getById(id),
  });

  // 2. Fetch Evidence Graph
  const { data: graphData } = useQuery({
    queryKey: ['case-graph', id],
    queryFn: () => api.evidence.getGraph(id),
  });

  // 3. Fetch Timeline
  const { data: timelineData } = useQuery({
    queryKey: ['case-timeline', id],
    queryFn: () => api.evidence.getTimeline(id),
  });

  // 4. Fetch Actions for this case
  const { data: caseActions } = useQuery({
    queryKey: ['case-actions', id],
    queryFn: () => api.actions.getForCase(id),
  });

  // 5. Fetch Attachments (Multimodal Evidence)
  const { data: attachments = [], refetch: refetchAttachments } = useQuery({
    queryKey: ['case-attachments', id],
    queryFn: () => api.attachments.list(id),
    enabled: !!id,
  });

  // 6. Fetch LLM Routing Telemetry Calls
  const { data: llmCalls = [], refetch: refetchLlmCalls } = useQuery({
    queryKey: ['case-llm-calls', id],
    queryFn: () => api.investigator.getLlmCalls(id),
    enabled: !!id,
  });

  // 7. Fetch Latest Persisted Investigation (with investigation_steps latencies)
  const latestInvestigationQuery = useQuery({
    queryKey: ['case-investigation-latest', id],
    queryFn: () => api.investigator.getLatestForCase(id),
    enabled: !!id,
  });

  // Upload Mutation
  const uploadMutation = useMutation({
    mutationFn: (file: File) => api.attachments.upload(id, file),
    onSuccess: (data) => {
      refetchAttachments();
      toast.success(`Uploaded ${data.filename}. Click 'Analyze with Gemini' to extract evidence.`);
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Upload failed. Allowed: PNG, JPEG, PDF up to 4MB');
    },
  });

  // Analyze Mutation
  const analyzeMutation = useMutation({
    mutationFn: (attachmentId: string) => api.attachments.analyze(id, attachmentId),
    onSuccess: (data) => {
      refetchAttachments();
      refetchLlmCalls();
      queryClient.invalidateQueries({ queryKey: ['case-graph', id] });
      queryClient.invalidateQueries({ queryKey: ['case-timeline', id] });
      toast.success(`Extracted evidence via ${data.provider} (${data.model})`);
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Multimodal extraction failed.');
    },
  });

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 4 * 1024 * 1024) {
      toast.error('File exceeds 4MB limit.');
      return;
    }
    uploadMutation.mutate(file);
    e.target.value = '';
  };

  // Action mutation: Approve
  const approveMutation = useMutation({
    mutationFn: async (actionId: number) => {
      return api.actions.approve(actionId);
    },
    onSuccess: (data: ProposedActionDto) => {
      queryClient.invalidateQueries({ queryKey: ['case', id] });
      queryClient.invalidateQueries({ queryKey: ['case-actions', id] });
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      toast.success(`Action APPROVED: ${data.type} executed idempotently.`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.status === 403) {
        navigate('/403');
        return;
      }
      toast.error(err instanceof Error ? err.message : 'Failed to approve action.');
    },
  });

  // Action mutation: Reject
  const rejectMutation = useMutation({
    mutationFn: async (actionId: number) => {
      return api.actions.reject(actionId);
    },
    onSuccess: (data: ProposedActionDto) => {
      queryClient.invalidateQueries({ queryKey: ['case', id] });
      queryClient.invalidateQueries({ queryKey: ['case-actions', id] });
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      toast.info(`Action REJECTED: ${data.type} declined.`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.status === 403) {
        navigate('/403');
        return;
      }
      toast.error(err instanceof Error ? err.message : 'Failed to reject action.');
    },
  });

  // AI Investigation Mutation
  const investigateMutation = useMutation({
    mutationFn: () => api.investigator.investigate(id),
    onSuccess: (data) => {
      setInvestigationResult(data);
      queryClient.setQueryData(['case-investigation-latest', id], data);
      queryClient.invalidateQueries({ queryKey: ['case', id] });
      queryClient.invalidateQueries({ queryKey: ['case-actions', id] });
      queryClient.invalidateQueries({ queryKey: ['case-llm-calls', id] });
      toast.success('Investigation completed with deterministic verification.');
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Investigation failed.');
    },
  });

  const activeInvestigation = investigationResult || latestInvestigationQuery.data;

  // Derived Evidence Coverage Breakdown (Confirmed, Conflicting, Missing) from real graph nodes
  const evidenceCoverageDonutData = useMemo(() => {
    const nodes = graphData?.nodes || [];
    if (nodes.length === 0) return [];
    let confirmed = 0;
    let conflicting = 0;
    let missing = 0;
    nodes.forEach((n) => {
      if (n.isMissing || n.status === 'MISSING') {
        missing += 1;
      } else if (
        n.status === 'MISMATCH' ||
        n.status === 'CONFLICT' ||
        n.status === 'DISCREPANCY' ||
        n.status === 'FAILED' ||
        n.metadata?.discrepancy === true
      ) {
        conflicting += 1;
      } else {
        confirmed += 1;
      }
    });
    if (conflicting === 0 && (activeInvestigation?.report?.conflicts?.length ?? 0) > 0) {
      conflicting = activeInvestigation?.report?.conflicts?.length ?? 0;
    }
    return [
      { category: 'Confirmed', count: confirmed, fill: SEMANTIC_COLORS.confirmed },
      { category: 'Conflicting', count: conflicting, fill: SEMANTIC_COLORS.conflicting },
      { category: 'Missing', count: missing, fill: SEMANTIC_COLORS.missing },
    ].filter((row) => row.count > 0);
  }, [activeInvestigation?.report?.conflicts?.length, graphData?.nodes]);

  const toolCalls = useMemo(
    () =>
      (activeInvestigation?.steps || []).map((s) => {
        const rawTool = s.tool || 'tool';
        // Drop "get" / "list" prefix for concise axis labels
        const shortName = rawTool.replace(/^get/i, '').replace(/^list/i, '');
        return {
          stepLabel: shortName,
          toolName: rawTool,
          latencyMs: Number(s.latencyMs ?? 0),
        };
      }),
    [activeInvestigation?.steps]
  );

  // Investigation report: prefer persisted/live report; show real status notice if not yet run
  const report = activeInvestigation?.report;

  const formatProviderLabel = (raw?: string | null) => {
    if (!raw) return 'AI Model';
    const lower = raw.toLowerCase();
    if (lower === 'groq') return 'Groq';
    if (lower === 'gemini') return 'Gemini';
    return raw;
  };

  const effectiveLlmCall = llmCalls.find((c) => c.status !== 'FAILED') ?? llmCalls[0];
  const isRuleBased = Boolean(
    activeInvestigation &&
      (activeInvestigation.providerMode === 'RULE_BASED' ||
        activeInvestigation.provider === 'rule_fallback' ||
        activeInvestigation.status === 'DEGRADED' ||
        report?.degraded ||
        effectiveLlmCall?.provider === 'rule_fallback')
  );

  const verifiedCitations = activeInvestigation?.verifiedCitations ?? report?.verifiedCitations;
  const totalCitations = activeInvestigation?.totalCitations ?? report?.totalCitations;
  const citationsBadgeText = !report && !activeInvestigation
    ? 'Awaiting AI run'
    : isRuleBased
    ? 'Not verified'
    : typeof verifiedCitations === 'number' && typeof totalCitations === 'number'
    ? `${verifiedCitations} of ${totalCitations} citations verified`
    : 'Citations verified';

  const providerBanner = (() => {
    if (!activeInvestigation && !effectiveLlmCall) return null;
    if (isRuleBased) {
      const reason =
        activeInvestigation?.fallbackReason ||
        effectiveLlmCall?.fallbackReason ||
        'All LLM providers unavailable';
      return {
        tone: 'rule' as const,
        label: `Rule-based (AI unavailable: ${reason})`,
        metrics: 'No LLM call',
      };
    }

    const providerKey = activeInvestigation?.provider || effectiveLlmCall?.provider;
    const displayProvider = formatProviderLabel(providerKey);
    const modelLabel =
      effectiveLlmCall?.model ||
      activeInvestigation?.model?.replace(/^(groq|gemini)\s*\((.*)\)$/i, '$2') ||
      'unknown model';
    const isFallback =
      activeInvestigation?.providerMode === 'FALLBACK' || Boolean(effectiveLlmCall?.fallbackUsed);
    const fallbackReason =
      activeInvestigation?.fallbackReason || effectiveLlmCall?.fallbackReason || 'primary failed';
    const latency = activeInvestigation?.latencyMs ?? effectiveLlmCall?.latencyMs;
    const inTokens = activeInvestigation?.promptTokens ?? effectiveLlmCall?.promptTokens;
    const outTokens = activeInvestigation?.completionTokens ?? effectiveLlmCall?.completionTokens;

    const metrics =
      typeof latency === 'number' && latency > 0
        ? `${latency} ms · ${inTokens ?? 0} in / ${outTokens ?? 0} out`
        : typeof inTokens === 'number' || typeof outTokens === 'number'
        ? `${inTokens ?? 0} in / ${outTokens ?? 0} out`
        : 'Completed';

    if (isFallback) {
      return {
        tone: 'fallback' as const,
        label: `Fallback: ${displayProvider} (${fallbackReason}) · ${modelLabel}`,
        metrics,
      };
    }

    return {
      tone: 'primary' as const,
      label: `${displayProvider}: ${modelLabel}`,
      metrics,
    };
  })();

  if (caseLoading) {
    return (
      <AppLayout>
        <div className="max-w-7xl mx-auto space-y-7">
          <div className="h-10 bg-slate-200 dark:bg-slate-800 rounded w-1/3 animate-pulse" />
          <TableSkeleton rows={6} />
        </div>
      </AppLayout>
    );
  }

  if (caseError || !caseData) {
    return (
      <AppLayout>
        <div className="max-w-7xl mx-auto space-y-7">
          <ErrorState
            title={`Could not load case ${id}`}
            message="Case record was not found or could not be loaded from the database."
            onRetry={() => refetchCase()}
          />
        </div>
      </AppLayout>
    );
  }

  // Active proposed action (if any)
  const proposedAction = caseActions && caseActions.length > 0 ? caseActions[0] : null;
  const isApproved = proposedAction?.status === 'APPROVED' || proposedAction?.status === 'EXECUTED';
  const isRejected = proposedAction?.status === 'REJECTED';

  // Format type string: PAID_NOT_MARKED -> Paid, not marked
  const typeFormatted = (() => {
    switch (caseData.type) {
      case 'PAID_NOT_MARKED': return 'Paid, not marked';
      case 'AMOUNT_MISMATCH': return 'Amount mismatch';
      case 'DUPLICATE_EVENT': return 'Duplicate event';
      case 'REFUND_MISMATCH': return 'Refund mismatch';
      case 'ORPHAN_CAPTURE': return 'Orphan capture';
      default: return caseData.type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
    }
  })();

  const normalizedStatus = (caseData.status || '').toUpperCase();
  const normalizedSeverity = (caseData.severity || '').toUpperCase();

  // Severity pill classes
  const severityBadge =
    normalizedSeverity === 'HIGH' ? (
      <span className="text-rose-600 dark:text-rose-400 font-semibold inline-flex items-center gap-1">
        severity high <InfoTip metricId="case.severity" size={14} />
      </span>
    ) : normalizedSeverity === 'MEDIUM' ? (
      <span className="text-amber-600 dark:text-amber-400 font-semibold inline-flex items-center gap-1">
        severity medium <InfoTip metricId="case.severity" size={14} />
      </span>
    ) : (
      <span className="text-slate-500 font-normal inline-flex items-center gap-1">
        severity low <InfoTip metricId="case.severity" size={14} />
      </span>
    );

  const statusBadge =
    normalizedStatus === 'OPEN' ? (
      <span className="px-3.5 py-1 rounded-full border border-amber-400 dark:border-amber-700 text-amber-600 dark:text-amber-400 text-xs font-normal inline-flex items-center gap-1">
        Open <InfoTip metricId="case.status" size={14} />
      </span>
    ) : normalizedStatus === 'INVESTIGATING' ? (
      <span className="px-3.5 py-1 rounded-full border border-blue-400 dark:border-blue-700 text-blue-600 dark:text-blue-400 text-xs font-normal inline-flex items-center gap-1">
        Investigating <InfoTip metricId="case.status" size={14} />
      </span>
    ) : normalizedStatus === 'ACTION_PROPOSED' || normalizedStatus === 'PROPOSED' ? (
      <span className="px-3.5 py-1 rounded-full border border-purple-400 dark:border-purple-700 text-purple-600 dark:text-purple-400 text-xs font-normal inline-flex items-center gap-1">
        Proposed <InfoTip metricId="case.status" size={14} />
      </span>
    ) : normalizedStatus === 'RESOLVED' ? (
      <span className="px-3.5 py-1 rounded-full border border-emerald-400 dark:border-emerald-700 text-emerald-600 dark:text-emerald-400 text-xs font-normal inline-flex items-center gap-1">
        Resolved <InfoTip metricId="case.status" size={14} />
      </span>
    ) : (
      <span className="px-3.5 py-1 rounded-full border border-slate-400 dark:border-slate-700 text-slate-600 dark:text-slate-400 text-xs font-normal inline-flex items-center gap-1">
        {caseData.status || 'Unknown'} <InfoTip metricId="case.status" size={14} />
      </span>
    );

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-7">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
              Case {caseData.id}: {typeFormatted}
            </h1>
            <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1 flex flex-wrap items-center gap-1.5">
              <span>Order #{caseData.shopOrderId?.replace('ORD-', '') || caseData.paypalOrderId}</span>
              <span>&middot;</span>
              <span>opened {calculateAge(caseData.openedAt)} ago</span>
              {caseData.resolvedAt && (
                <>
                  <span>&middot;</span>
                  <span>resolved {calculateAge(caseData.resolvedAt)} ago</span>
                </>
              )}
              <span>&middot;</span>
              {severityBadge}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2 sm:gap-2.5 self-start sm:self-auto">
            {statusBadge}
            <label className="px-3 sm:px-3.5 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] text-xs sm:text-sm font-medium shadow-2xs cursor-pointer transition-all flex items-center space-x-1.5">
              <GoogleIcon name="attach_file" size={17} />
              <span>{uploadMutation.isPending ? 'Uploading...' : 'Add evidence'}</span>
              <input
                type="file"
                accept="image/png,image/jpeg,application/pdf"
                className="hidden"
                disabled={uploadMutation.isPending}
                onChange={handleFileUpload}
              />
            </label>
            <button
              onClick={() => investigateMutation.mutate()}
              disabled={investigateMutation.isPending}
              className="px-4 sm:px-5 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs sm:text-sm font-medium shadow-sm cursor-pointer transition-all disabled:opacity-70"
            >
              {investigateMutation.isPending
                ? 'Investigating...'
                : normalizedStatus === 'RESOLVED'
                ? 'Re-investigate'
                : 'Investigate'}
            </button>
          </div>
        </div>

        {/* 2-Column Top: Evidence Graph & AI Investigator */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left: Evidence Graph */}
          <div className="lg:col-span-7 bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-4 sm:p-6 space-y-4 shadow-2xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-bold text-slate-900 dark:text-white">Evidence graph</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  Dashed outlines mark links that should exist but do not.
                </p>
              </div>

              {/* Segmented control */}
              <div className="flex items-center rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#131b26] p-0.5 text-xs self-start sm:self-auto">
                <button
                  onClick={() => setActiveTab('graph')}
                  className={`px-3 py-1 rounded-md cursor-pointer transition-colors ${
                    activeTab === 'graph'
                      ? 'bg-slate-100 dark:bg-[#1f2b3e] text-slate-900 dark:text-white font-medium shadow-2xs'
                      : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  Graph
                </button>
                <button
                  onClick={() => setActiveTab('timeline')}
                  className={`px-3 py-1 rounded-md cursor-pointer transition-colors ${
                    activeTab === 'timeline'
                      ? 'bg-slate-100 dark:bg-[#1f2b3e] text-slate-900 dark:text-white font-medium shadow-2xs'
                      : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  Timeline
                </button>
                <button
                  onClick={() => setActiveTab('raw')}
                  className={`px-3 py-1 rounded-md cursor-pointer transition-colors ${
                    activeTab === 'raw'
                      ? 'bg-slate-100 dark:bg-[#1f2b3e] text-slate-900 dark:text-white font-medium shadow-2xs'
                      : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  Raw
                </button>
              </div>
            </div>

            {/* Inner Content depending on Active Tab */}
            {activeTab === 'graph' && (
              <EvidenceGraphView graph={graphData} />
            )}

            {activeTab === 'timeline' && (
              <div className="w-full h-[360px] overflow-y-auto bg-[#f1f5f9]/70 dark:bg-[#080d17] rounded-xl border border-slate-200/60 dark:border-slate-800/80 p-4">
                {timelineData && timelineData.length > 0 ? (
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 font-normal">
                        <th className="py-2 pr-3">Time</th>
                        <th className="py-2 px-3">Source</th>
                        <th className="py-2 pl-3">Event</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200/60 dark:divide-slate-800/60">
                      {timelineData.map((t, idx) => (
                        <tr key={idx} className="hover:bg-slate-50 dark:hover:bg-slate-900/40">
                          <td className="py-2.5 pr-3 font-mono text-slate-500">
                            {t.formattedTime || (t.timestamp ? new Date(t.timestamp).toLocaleTimeString([], { hour12: false }) : '--:--')}
                          </td>
                          <td className="py-2.5 px-3 font-medium text-slate-700 dark:text-slate-300">
                            {t.source || t.actor || 'SYSTEM'}
                          </td>
                          <td className="py-2.5 pl-3 text-slate-800 dark:text-slate-200">
                            {t.title || t.description || t.type || 'Event'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <EmptyState title="No timeline events recorded" description="Events will populate as transactions occur." />
                )}
              </div>
            )}

            {activeTab === 'raw' && (
              <div className="w-full h-[360px] overflow-y-auto bg-slate-950 text-slate-300 rounded-xl p-4 font-mono text-xs border border-slate-800">
                <pre>{JSON.stringify(caseData, null, 2)}</pre>
              </div>
            )}
          </div>

          {/* Right: AI Investigator (Sticky) */}
          <div className="lg:col-span-5 lg:sticky lg:top-4 self-start bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-4 sm:p-6 space-y-5 shadow-2xs">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <h2 className="text-base font-bold text-slate-900 dark:text-white">AI investigator</h2>
                <InfoTip metricId="case.investigationStatus" />
              </div>
              <span
                className={
                  isRuleBased
                    ? 'px-3 py-0.5 rounded-full border border-amber-500/50 bg-amber-50/60 dark:bg-amber-950/20 text-amber-800 dark:text-amber-300 text-xs font-normal inline-flex items-center gap-1'
                    : 'px-3 py-0.5 rounded-full border border-emerald-500/50 bg-emerald-50/50 dark:bg-emerald-950/20 text-emerald-700 dark:text-emerald-400 text-xs font-normal inline-flex items-center gap-1'
                }
              >
                {citationsBadgeText}
                <InfoTip metricId="case.citationsVerified" size={13} />
              </span>
            </div>

            {/* AI Provider Execution Chip */}
            {providerBanner && (
              <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-xl bg-slate-50 dark:bg-[#131b26] border border-slate-200/70 dark:border-slate-800 text-[11px] text-slate-600 dark:text-slate-400">
                <div className="flex items-center space-x-1.5 font-mono min-w-0">
                  <span
                    className={
                      providerBanner.tone === 'primary'
                        ? 'w-2 h-2 rounded-full bg-emerald-500 shrink-0'
                        : 'w-2 h-2 rounded-full bg-amber-500 shrink-0'
                    }
                  />
                  <span className="font-semibold text-slate-800 dark:text-slate-200 break-words">
                    {providerBanner.label}
                  </span>
                  <InfoTip metricId="case.providerChip" size={13} />
                </div>
                <div className="font-mono text-slate-500 dark:text-slate-400 shrink-0">
                  {providerBanner.metrics}
                </div>
              </div>
            )}

            {toolCalls.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 text-xs font-mono">
                {toolCalls.map((tc, idx) => (
                  <span
                    key={idx}
                    className="px-2.5 py-1 rounded-full border border-blue-400/60 dark:border-blue-700/60 bg-blue-50/40 dark:bg-blue-950/20 text-blue-700 dark:text-blue-300 inline-flex items-center gap-1"
                  >
                    {tc.stepLabel} {tc.latencyMs} ms
                    <InfoTip
                      metricId="case.toolLatency"
                      values={{ customDetail: `${tc.toolName}: ${tc.latencyMs} ms execution latency` }}
                      size={12}
                    />
                  </span>
                ))}
              </div>
            )}

            {/* Case Telemetry Charts (AG Charts Community) */}
            <div className="grid grid-cols-1 gap-4">
              <DonutChartCard
                title="Case Evidence Coverage"
                description="Confirmed, conflicting, and missing evidence items in this case DAG."
                metricId="case.evidenceCoverage"
                data={evidenceCoverageDonutData}
                angleKey="count"
                calloutLabelKey="category"
                fills={evidenceCoverageDonutData.map((d) => d.fill)}
                heightClassName="h-52"
                emptyMessage="No evidence nodes derived yet."
                lastUpdated={caseData.updatedAt}
              />

              <BarChartCard
                title="Investigation Tool-Call Latencies"
                description="Measured read-only tool latencies from persisted investigation_steps."
                metricId="case.toolLatency"
                data={toolCalls}
                xKey="stepLabel"
                xLabel="Tool Step"
                direction="horizontal"
                series={[{ yKey: 'latencyMs', yName: 'Latency', fill: SEMANTIC_COLORS.primary }]}
                yAxisTitle="ms"
                valueSuffix=" ms"
                heightClassName="h-56"
                isLoading={latestInvestigationQuery.isLoading || investigateMutation.isPending}
                emptyMessage="Run an AI investigation to measure real tool-call latencies."
                lastUpdated={activeInvestigation?.finishedAt || activeInvestigation?.startedAt}
              />
            </div>

            {/* Structured findings */}
            {report ? (
              <div className="space-y-3.5 text-xs sm:text-sm">
                {report.confirmed && report.confirmed.length > 0 && (
                  <div className="space-y-0.5">
                    <span className="font-semibold text-emerald-600 dark:text-emerald-400 block">Confirmed</span>
                    <p className="text-slate-700 dark:text-slate-300">
                      {report.confirmed.map((item: any) => typeof item === 'string' ? item : item?.statement || item?.claim || item?.text || JSON.stringify(item)).join(' ')}
                    </p>
                  </div>
                )}

                {report.conflicts && report.conflicts.length > 0 && (
                  <div className="space-y-0.5">
                    <span className="font-semibold text-rose-600 dark:text-rose-400 block">Conflict</span>
                    <p className="text-slate-700 dark:text-slate-300">
                      {report.conflicts.map((item: any) => typeof item === 'string' ? item : item?.statement || item?.claim || item?.text || JSON.stringify(item)).join(' ')}
                    </p>
                  </div>
                )}

                {report.missing && report.missing.length > 0 && (
                  <div className="space-y-0.5">
                    <span className="font-semibold text-amber-600 dark:text-amber-400 block">Missing</span>
                    <p className="text-slate-700 dark:text-slate-300">
                      {report.missing.map((item: any) => typeof item === 'string' ? item : item?.statement || item?.claim || item?.text || JSON.stringify(item)).join(' ')}
                    </p>
                  </div>
                )}
              </div>
            ) : (
              <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#131b26] border border-dashed border-slate-200 dark:border-slate-800 text-xs text-slate-500 dark:text-slate-400">
                No AI investigation persisted for this case yet. Click <strong>Run investigator</strong> above to inspect read-only PayPal and merchant records.
              </div>
            )}

            {/* Blue Recommended Callout Box */}
            {report && (
              <div className="p-4 rounded-xl bg-[#edf3fa] dark:bg-[#131d2e] border border-blue-200/50 dark:border-blue-900/50 space-y-1">
                <span className="text-xs font-semibold text-slate-900 dark:text-white block">Recommended</span>
                <p className="text-xs sm:text-sm text-slate-700 dark:text-slate-300">
                  {report.recommendedAction?.type === 'SYNC_ORDER_STATUS'
                    ? 'Sync order status to PAID. Needs approval.'
                    : report.recommendedAction?.type === 'HOLD_REFUND'
                    ? 'Hold refund flag on order. Needs approval.'
                    : 'Manual operator review recommended.'}
                </p>
              </div>
            )}

            {/* Action buttons (only displayed when proposed action exists) */}
            {proposedAction ? (
              <div className="flex items-center gap-3 pt-1">
                <button
                  type="button"
                  onClick={() => approveMutation.mutate(proposedAction.id)}
                  disabled={approveMutation.isPending || isApproved}
                  className="px-5 py-2 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {approveMutation.isPending ? 'Approving...' : isApproved ? 'Approved' : 'Approve'}
                </button>

                <button
                  type="button"
                  onClick={() => rejectMutation.mutate(proposedAction.id)}
                  disabled={rejectMutation.isPending || isRejected || isApproved}
                  className="px-5 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-2xs cursor-pointer transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {rejectMutation.isPending ? 'Rejecting...' : isRejected ? 'Rejected' : 'Reject'}
                </button>
              </div>
            ) : (
              <div className="pt-2 text-xs text-slate-500 dark:text-slate-400">
                {normalizedStatus === 'RESOLVED'
                  ? 'Case resolved. All operator actions completed.'
                  : 'No action currently awaiting operator approval. Run the investigator to propose next steps.'}
              </div>
            )}
          </div>
        </div>

        {/* Bottom: Dispute Evidence Checklist & Multimodal Attachments (Timeline already available above in Evidence Graph panel) */}
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 sm:p-7 space-y-6 shadow-2xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100 dark:border-slate-800/80">
            <div>
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400">
                  <GoogleIcon name="fact_check" size={18} />
                </span>
                <h2 className="text-base sm:text-lg font-bold text-slate-900 dark:text-white">
                  Dispute evidence checklist
                </h2>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Extracted via Gemini multimodal reasoning or verified merchant store records.
              </p>
            </div>
            <div className="flex items-center gap-2 self-start sm:self-auto">
              <span className="text-xs font-mono px-3 py-1 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 font-medium">
                {attachments.length} attachment{attachments.length !== 1 ? 's' : ''}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Checklist Items Column */}
            <div className="lg:col-span-6 space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
                Required Evidence Fields
              </h3>
              
              <div className="p-3.5 rounded-xl bg-slate-50/70 dark:bg-[#131b26] border border-slate-200/70 dark:border-slate-800 flex items-center justify-between transition-colors">
                <label className="flex items-center space-x-3 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={orderReceipt}
                    onChange={(e) => setOrderReceipt(e.target.checked)}
                    className="w-4 h-4 rounded text-blue-600 border-slate-300 focus:ring-blue-500 cursor-pointer"
                  />
                  <div>
                    <span className="text-sm font-medium text-slate-800 dark:text-slate-200 block">Order receipt</span>
                    <span className="text-[11px] text-slate-400">Merchant checkout cart and SKU snapshot</span>
                  </div>
                </label>
                <span className="px-3 py-0.5 rounded-full border border-emerald-500/60 bg-emerald-50/50 dark:bg-emerald-950/30 text-emerald-700 dark:text-emerald-400 text-xs font-medium">
                  Have
                </span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-50/70 dark:bg-[#131b26] border border-slate-200/70 dark:border-slate-800 flex items-center justify-between transition-colors">
                <label className="flex items-center space-x-3 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={proofOfDelivery || attachments.some(a => Boolean(a.extractedData))}
                    onChange={(e) => setProofOfDelivery(e.target.checked)}
                    className="w-4 h-4 rounded text-blue-600 border-slate-300 focus:ring-blue-500 cursor-pointer"
                  />
                  <div>
                    <span className="text-sm font-medium text-slate-800 dark:text-slate-200 block">Proof of delivery</span>
                    <span className="text-[11px] text-slate-400">Carrier tracking or digital service fulfillment</span>
                  </div>
                </label>
                <span className={`px-3 py-0.5 rounded-full border text-xs font-medium ${
                  proofOfDelivery || attachments.some(a => Boolean(a.extractedData))
                    ? 'border-emerald-500/60 bg-emerald-50/50 dark:bg-emerald-950/30 text-emerald-700 dark:text-emerald-400'
                    : 'border-amber-600/60 bg-amber-50/50 dark:bg-amber-950/30 text-amber-700 dark:text-amber-400'
                }`}>
                  {proofOfDelivery || attachments.some(a => Boolean(a.extractedData)) ? 'Verified' : 'Missing'}
                </span>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-50/70 dark:bg-[#131b26] border border-slate-200/70 dark:border-slate-800 flex items-center justify-between transition-colors">
                <label className="flex items-center space-x-3 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={customerMessages}
                    onChange={(e) => setCustomerMessages(e.target.checked)}
                    className="w-4 h-4 rounded text-blue-600 border-slate-300 focus:ring-blue-500 cursor-pointer"
                  />
                  <div>
                    <span className="text-sm font-medium text-slate-800 dark:text-slate-200 block">Customer messages</span>
                    <span className="text-[11px] text-slate-400">Support chat logs and email notifications</span>
                  </div>
                </label>
                <span className="px-3 py-0.5 rounded-full border border-amber-600/60 bg-amber-50/50 dark:bg-amber-950/30 text-amber-700 dark:text-amber-400 text-xs font-medium">
                  Missing
                </span>
              </div>
            </div>

            {/* Uploaded Evidence Files & Gemini Analysis Column */}
            <div className="lg:col-span-6 space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
                Multimodal Attachments ({attachments.length})
              </h3>

              {attachments.length === 0 ? (
                <div className="p-6 rounded-xl border border-dashed border-slate-200 dark:border-slate-800 text-center text-xs text-slate-400 space-y-1">
                  <GoogleIcon name="cloud_upload" size={24} className="mx-auto text-slate-400" />
                  <div>No receipt or invoice documents uploaded yet.</div>
                  <div className="text-[11px] text-slate-500">Upload a PDF or image receipt above to trigger Gemini multimodal analysis.</div>
                </div>
              ) : (
                <div className="space-y-2.5">
                  {attachments.map((att) => {
                    let parsedData: any = null;
                    if (att.extractedData) {
                      try {
                        parsedData = JSON.parse(att.extractedData);
                      } catch {}
                    }
                    return (
                      <div
                        key={att.id}
                        className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#131b26] border border-slate-200/60 dark:border-slate-800/80 text-xs space-y-2 shadow-2xs"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-slate-800 dark:text-slate-200 truncate max-w-[280px]">
                            {att.filename}
                          </span>
                          <span className="text-[10px] text-slate-400 font-mono">
                            {(att.fileSize / 1024).toFixed(0)} KB
                          </span>
                        </div>

                        {parsedData ? (
                          <div className="text-[11px] text-slate-600 dark:text-slate-400 bg-emerald-50/50 dark:bg-emerald-950/30 p-3 rounded-lg border border-emerald-200/60 dark:border-emerald-800/40 space-y-1">
                            <div className="text-emerald-700 dark:text-emerald-400 font-semibold flex items-center space-x-1">
                              <GoogleIcon name="verified" size={13} />
                              <span>Gemini Multimodal Extracted</span>
                            </div>
                            <div className="grid grid-cols-2 gap-1 text-[11px] pt-0.5">
                              <div><strong className="text-slate-700 dark:text-slate-300">Merchant:</strong> {parsedData.merchant || 'N/A'}</div>
                              <div><strong className="text-slate-700 dark:text-slate-300">Amount:</strong> ${parsedData.amount ?? '--'} {parsedData.currency || 'USD'}</div>
                            </div>
                            {parsedData.trackingNumber && <div><strong className="text-slate-700 dark:text-slate-300">Tracking:</strong> {parsedData.trackingNumber}</div>}
                            {parsedData.notes && <div className="italic text-slate-500 pt-0.5">{parsedData.notes}</div>}
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => analyzeMutation.mutate(att.id)}
                            disabled={analyzeMutation.isPending}
                            className="px-3.5 py-1.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 text-white text-[11px] font-medium shadow-2xs cursor-pointer flex items-center space-x-1.5 transition-colors"
                          >
                            <GoogleIcon name="auto_awesome" size={14} />
                            <span>{analyzeMutation.isPending ? 'Extracting...' : 'Analyze with Gemini'}</span>
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  );
}
