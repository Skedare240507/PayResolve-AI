import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AgStudio, createWidgets } from 'ag-studio-react';
import { AgStudioLicenseManager, studioTheme } from 'ag-studio';

import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { api } from '../lib/api';
import { useTheme } from '../lib/theme';
import { SEMANTIC_COLORS } from '../lib/chartTheme';
import { BarChartCard } from '../components/charts/BarChartCard';
import { DonutChartCard } from '../components/charts/DonutChartCard';
import { LineAreaChartCard } from '../components/charts/LineAreaChartCard';
import {
  CasesByTypeWidget,
  TimeToCauseWidget,
  EvidenceCoverageWidget,
  type OperationsContext,
} from '../components/operations/OperationsWidgets';
import { OperationsAgentAssistant } from '../components/operations/OperationsAgentAssistant';

// Register AG Studio free trial license if present in env (never hardcoded or committed)
const agLicense = import.meta.env.VITE_AG_LICENSE;
if (agLicense) {
  try {
    AgStudioLicenseManager.setLicenseKey(agLicense);
  } catch {
    // ignore
  }
}

export default function OperationsPage() {
  const [studioMode, setStudioMode] = useState<'view' | 'edit'>('view');
  const [showAgent, setShowAgent] = useState(false);
  const { isDark } = useTheme();

  // 1. Fetch live telemetry data
  const { data: casesData } = useQuery({
    queryKey: ['cases', 'all'],
    queryFn: () => api.cases.list({ size: 100 }),
  });

  const { data: eventsData } = useQuery({
    queryKey: ['events', 'inbox'],
    queryFn: () => api.events.list({ limit: 100 }),
  });

  const { data: benchmarkData } = useQuery({
    queryKey: ['benchmark', 'latest'],
    queryFn: () => api.benchmark.latest(),
  });

  const statsQuery = useQuery({
    queryKey: ['stats', 'overview'],
    queryFn: () => api.stats.overview(),
  });

  const statsData = statsQuery.data;
  const casesList = useMemo(() => casesData?.content || [], [casesData?.content]);
  const eventsList = useMemo(() => eventsData?.content || [], [eventsData?.content]);

  const hasStudioData =
    casesList.length > 0 || eventsList.length > 0 || (benchmarkData?.cases?.length ?? 0) > 0;

  // Real API rows only — never synthesize placeholder rows
  const studioData = useMemo(() => {
    const caseRows = casesList.map((c) => ({
      id: c.id,
      category: c.typeLabel || c.type,
      orderId: c.order,
      amountMinor: c.amountMinor,
      severity: c.severity,
      status: c.status,
      age: c.age,
    }));

    const eventRows = eventsList.map((e) => ({
      id: e.id,
      eventId: e.eventId || e.paypalEventId,
      eventType: e.type,
      signature: e.signature,
      status: e.status,
      tries: e.tries,
    }));

    const benchmarkRows = (benchmarkData?.cases || []).map((b) => ({
      caseId: b.caseId,
      type: b.type,
      status: b.passed ? 'PASSED' : 'MISS',
      expected: b.expectedOutcome,
      actual: b.actualOutcome,
      latencyMs: b.durationMs,
    }));

    const sources: Array<{ id: string; data: Array<Record<string, unknown>> }> = [];
    if (caseRows.length > 0) sources.push({ id: 'cases', data: caseRows });
    if (eventRows.length > 0) sources.push({ id: 'events', data: eventRows });
    if (benchmarkRows.length > 0) sources.push({ id: 'benchmark', data: benchmarkRows });

    return { sources };
  }, [casesList, eventsList, benchmarkData]);

  // Context passed into custom widgets
  const widgetContext: OperationsContext = useMemo(() => {
    return {
      cases: casesList,
      events: eventsList,
      benchmark: benchmarkData,
      stats: statsData,
      isDark,
    };
  }, [casesList, eventsList, benchmarkData, statsData, isDark]);

  // Register Custom Widgets
  const widgetsConfig = useMemo(() => {
    return createWidgets<any>({
      additionalTypes: [
        {
          id: 'cases-by-type',
          label: 'Cases by Type',
          comp: CasesByTypeWidget,
          defaultSize: { width: 340, height: 480 },
          minSize: { width: 280, height: 360 },
          form: (p) => p.createDefaults({}),
        },
        {
          id: 'time-to-cause',
          label: 'Time to Root Cause',
          comp: TimeToCauseWidget,
          defaultSize: { width: 340, height: 480 },
          minSize: { width: 280, height: 360 },
          form: (p) => p.createDefaults({}),
        },
        {
          id: 'evidence-coverage',
          label: 'Evidence Coverage',
          comp: EvidenceCoverageWidget,
          defaultSize: { width: 340, height: 480 },
          minSize: { width: 280, height: 360 },
          form: (p) => p.createDefaults({}),
        },
      ],
      menu: [
        {
          label: 'PayResolve Operations',
          widgetIds: ['cases-by-type', 'time-to-cause', 'evidence-coverage'],
        },
      ],
      defaultType: 'cases-by-type',
    });
  }, []);

  // Initial report layout with the 3 custom PayResolve analytics widgets
  const initialState = useMemo(() => {
    return {
      pages: [
        {
          id: 'operations-page',
          widgets: {
            'cases-widget': {
              type: 'cases-by-type',
              dataMapping: {},
              format: { title: { text: 'Exception Categories' } },
            },
            'time-widget': {
              type: 'time-to-cause',
              dataMapping: {},
              format: { title: { text: 'Investigation Latency' } },
            },
            'coverage-widget': {
              type: 'evidence-coverage',
              dataMapping: {},
              format: { title: { text: 'Evidence Invariants' } },
            },
          },
          widgetLayout: {
            'cases-widget': { xTrack: 0, yTrack: 0, xSpan: 8, ySpan: 24 },
            'time-widget': { xTrack: 8, yTrack: 0, xSpan: 8, ySpan: 24 },
            'coverage-widget': { xTrack: 16, yTrack: 0, xSpan: 8, ySpan: 24 },
          },
        },
      ],
      selectedPageId: 'operations-page',
    };
  }, []);

  // Theme matching PayResolve
  const studioActiveTheme = studioTheme;

  return (
    <AppLayout>
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center space-x-2.5">
              <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
                Operations
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 dark:bg-blue-950/60 text-[#0066cc] dark:text-blue-400 border border-blue-200/60 dark:border-blue-900/40">
                AG Studio
              </span>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Executive dashboard powered by AG Studio with custom PayResolve intelligence widgets.
            </p>
          </div>

          {/* Action Controls */}
          <div className="flex items-center space-x-3">
            <button
              onClick={() => setShowAgent((prev) => !prev)}
              className={`px-3.5 py-2 rounded-lg text-xs font-medium cursor-pointer transition-all flex items-center space-x-1.5 shadow-2xs ${
                showAgent
                  ? 'bg-[#0066cc] text-white shadow-sm'
                  : 'bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'
              }`}
            >
              <GoogleIcon name="smart_toy" size={15} />
              <span>{showAgent ? 'Hide AI Agent' : 'Ask AI Agent'}</span>
            </button>

            <button
              onClick={() => setStudioMode((prev) => (prev === 'view' ? 'edit' : 'view'))}
              className="px-4 py-2 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer shadow-2xs transition-colors flex items-center space-x-1.5"
            >
              <GoogleIcon name={studioMode === 'view' ? 'tune' : 'visibility'} size={15} />
              <span>{studioMode === 'view' ? 'Customize layout' : 'View layout'}</span>
            </button>
          </div>
        </div>

        {/* License Notice if missing */}
        {!agLicense && (
          <div className="px-4 py-2.5 rounded-xl border border-amber-500/30 bg-amber-50/80 dark:bg-amber-950/30 text-amber-900 dark:text-amber-200 text-xs flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <GoogleIcon name="info" size={16} className="text-amber-600 dark:text-amber-400" />
              <span>
                <strong>AG Studio Evaluation Mode:</strong> <code className="px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/60 font-mono text-[11px]">VITE_AG_LICENSE</code> is not set in <code className="px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/60 font-mono text-[11px]">frontend/.env</code>. AG Studio core features and custom widgets run in unrestricted trial mode with evaluation watermark. Exception Queue uses Community AG Grid (no license required).
              </span>
            </div>
            <a
              href="https://www.ag-grid.com/license-pricing/"
              target="_blank"
              rel="noreferrer"
              className="text-amber-700 dark:text-amber-300 underline font-medium hover:text-amber-800 whitespace-nowrap ml-3"
            >
              Get License
            </a>
          </div>
        )}

        {/* 4 Summary Stat Cards (Real API Aggregates Only) */}
        {(() => {
          const totalCasesCount = statsData?.totalCases ?? casesList.length;
          const totalConfirmed = (statsData?.evidenceCoverageByType || []).reduce(
            (sum, item) => sum + (item.confirmedNodes || 0),
            0
          );
          const totalMissing = (statsData?.evidenceCoverageByType || []).reduce(
            (sum, item) => sum + (item.missingNodes || 0),
            0
          );
          const totalNodes = totalConfirmed + totalMissing;
          const integrityPct = totalNodes > 0 ? Math.round((totalConfirmed / totalNodes) * 100) : null;
          const medianLatency = benchmarkData?.medianTimeToCauseSeconds;
          const benchSample = benchmarkData?.totalCases ?? statsData?.benchmarkComparison?.sampleSize;
          const groundingPct = benchmarkData?.groundingRatePct ?? statsData?.benchmarkComparison?.groundingRatePct;

          return (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-5">
              <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
                <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Total Exceptions</div>
                <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  {totalCasesCount}
                </div>
                <div className="text-xs text-slate-400 dark:text-slate-500">
                  {statsData?.casesByType?.length ? `Across ${statsData.casesByType.length} detector categories` : 'No cases recorded yet'}
                </div>
              </div>

              <div className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2">
                <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Investigation Latency</div>
                <div className="text-3xl sm:text-4xl font-bold text-emerald-600 dark:text-emerald-400">
                  {medianLatency != null ? `${medianLatency.toFixed(1)} s` : '—'}
                </div>
                <div className="text-xs text-slate-400 dark:text-slate-500">
                  {benchSample ? `Median AI latency (n=${benchSample})` : 'Run benchmark to measure'}
                </div>
              </div>

              <div
                className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2 cursor-help"
                title="AI Grounding Formula: (Verified cited entity IDs / Total cited entity IDs) * 100"
              >
                <div className="text-xs font-normal text-slate-500 dark:text-slate-400">AI Grounding Rate</div>
                <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  {groundingPct != null ? `${groundingPct.toFixed(1)}%` : '—'}
                </div>
                <div className="text-xs text-slate-400 dark:text-slate-500">
                  Formula: Verified citations / total cited
                </div>
              </div>

              <div
                className="bg-white dark:bg-[#0e1522] p-6 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-2 cursor-help"
                title="Evidence Integrity Formula: (Confirmed evidence links / Total expected evidence links) * 100"
              >
                <div className="text-xs font-normal text-slate-500 dark:text-slate-400">Evidence Integrity</div>
                <div className="text-3xl sm:text-4xl font-bold text-slate-900 dark:text-white">
                  {integrityPct != null ? `${integrityPct}%` : '—'}
                </div>
                <div className="text-xs text-slate-400 dark:text-slate-500">
                  Formula: {totalNodes > 0 ? `${totalConfirmed} confirmed / ${totalMissing} missing` : 'Confirmed links vs missing'}
                </div>
              </div>
            </div>
          );
        })()}

        {/* Charts Section (Independent of AG Studio — Powered by AG Charts Community MIT) */}
        <section aria-label="Telemetry Charts" className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white tracking-tight">
                Live Operational Telemetry
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Real-time charts rendered with AG Charts Community from <code className="font-mono">/api/stats/overview</code>.
              </p>
            </div>
            <span className="self-start sm:self-auto px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
              AG Charts Community (MIT)
            </span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* 1. Cases by Type (Horizontal Bar) */}
            <BarChartCard
              title="Cases by Exception Type"
              description="Distribution across PayPal reconciliation detector categories."
              data={(statsData?.casesByType || []).map((c) => ({
                label: c.typeLabel || c.type || 'Unknown',
                count: c.count ?? 0,
              }))}
              xKey="label"
              xLabel="Exception Type"
              direction="horizontal"
              series={[{ yKey: 'count', yName: 'Cases', fill: SEMANTIC_COLORS.primary }]}
              yAxisTitle="Case Count"
              isLoading={statsQuery.isLoading}
              isError={statsQuery.isError}
              emptyMessage="No exception cases detected yet."
              lastUpdated={statsData?.generatedAt}
            />

            {/* 2. Severity Mix (Donut) */}
            {(() => {
              const sevColorMap: Record<string, string> = {
                HIGH: '#dc2626',
                MEDIUM: '#d97706',
                LOW: '#059669',
              };
              const sevOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
              const severityRows = [...(statsData?.casesBySeverity || [])]
                .sort((a, b) => (sevOrder[a.severity ?? 'MEDIUM'] ?? 9) - (sevOrder[b.severity ?? 'MEDIUM'] ?? 9))
                .map((s) => ({
                  severity: s.severity || 'MEDIUM',
                  count: s.count ?? 0,
                  fill: sevColorMap[s.severity || 'MEDIUM'] ?? '#0284c7',
                }));
              return (
                <DonutChartCard
                  title="Severity Mix"
                  description="Active discrepancies segmented by risk level."
                  data={severityRows}
                  angleKey="count"
                  calloutLabelKey="severity"
                  fills={severityRows.map((r) => r.fill)}
                  isLoading={statsQuery.isLoading}
                  isError={statsQuery.isError}
                  emptyMessage="No severity breakdown available yet."
                  lastUpdated={statsData?.generatedAt}
                />
              );
            })()}

            {/* 3. Investigation Time: Rule-only vs AI Investigator (Benchmark data, n shown, no manual baseline) */}
            <BarChartCard
              title="Rule-Only vs AI Investigator Latency"
              description={
                statsData?.benchmarkComparison
                  ? `Measured median comparison from benchmark run (n=${statsData.benchmarkComparison.sampleSize}).`
                  : 'Run benchmark to measure rule-only vs AI investigator latency.'
              }
              data={
                statsData?.benchmarkComparison
                  ? [
                      {
                        stage: `Rule Snapshot Detector (n=${statsData.benchmarkComparison.sampleSize})`,
                        latencyMs: statsData.benchmarkComparison.ruleTimeMs ?? 0,
                      },
                      {
                        stage: `AI Investigator + Verifier (n=${statsData.benchmarkComparison.sampleSize})`,
                        latencyMs: statsData.benchmarkComparison.aiInvestigatorTimeMs ?? 0,
                      },
                    ]
                  : []
              }
              xKey="stage"
              xLabel="Investigation Stage"
              direction="vertical"
              series={[{ yKey: 'latencyMs', yName: 'Latency (ms)', fill: SEMANTIC_COLORS.secondary }]}
              yAxisTitle="Duration (ms)"
              valueSuffix=" ms"
              isLoading={statsQuery.isLoading}
              isError={statsQuery.isError}
              emptyMessage="No benchmark run executed yet."
              lastUpdated={statsData?.benchmarkComparison?.runAt}
            />

            {/* 4. Evidence Coverage: Confirmed vs Missing by Case Type (Stacked Bar) */}
            <BarChartCard
              title="Evidence Coverage by Case Type"
              description="Confirmed PayPal/shop nodes versus dashed missing invariant links."
              data={(statsData?.evidenceCoverageByType || []).map((cov) => ({
                category: cov.typeLabel || cov.type || 'Unknown',
                confirmed: cov.confirmedNodes ?? 0,
                missing: cov.missingNodes ?? 0,
              }))}
              xKey="category"
              xLabel="Case Type"
              direction="vertical"
              series={[
                { yKey: 'confirmed', yName: 'Confirmed Nodes', fill: SEMANTIC_COLORS.confirmed, stacked: true },
                { yKey: 'missing', yName: 'Missing Invariants', fill: SEMANTIC_COLORS.missing, stacked: true },
              ]}
              yAxisTitle="Evidence Graph Nodes"
              isLoading={statsQuery.isLoading}
              isError={statsQuery.isError}
              emptyMessage="No evidence graphs derived yet."
              lastUpdated={statsData?.generatedAt}
            />

            {/* 5. Webhook Events Received per Hour with Duplicate Deliveries Highlighted (Area / Line) */}
            <div className="lg:col-span-2">
              <LineAreaChartCard
                title="Webhook Events Received per Hour"
                description="Verified deliveries vs deduplicated replay attempts grouped by UTC hour."
                data={(statsData?.webhooksByHour || []).map((w) => ({
                  hour: w.hourLabel || w.hourIso || 'Unknown',
                  totalReceived: w.totalReceived ?? 0,
                  duplicateDeliveries: w.duplicateDeliveries ?? 0,
                }))}
                xKey="hour"
                xLabel="Hour (UTC)"
                yAxisTitle="Event Count"
                series={[
                  {
                    yKey: 'totalReceived',
                    yName: 'Total Deliveries',
                    type: 'area',
                    stroke: '#2563eb',
                    fill: '#2563eb',
                    markerShape: 'circle',
                  },
                  {
                    yKey: 'duplicateDeliveries',
                    yName: 'Duplicate Deliveries',
                    type: 'line',
                    stroke: '#d97706',
                    markerShape: 'diamond',
                    lineDash: [5, 5],
                  },
                ]}
                isLoading={statsQuery.isLoading}
                isError={statsQuery.isError}
                emptyMessage="No webhook events received yet. Replay or send sandbox webhooks to plot traffic."
                lastUpdated={statsData?.generatedAt}
              />
            </div>
          </div>
        </section>

        {/* Operations Workspace Grid: AG Studio + Optional Agent Panel */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Main AG Studio Canvas */}
          <div className={`${showAgent ? 'lg:col-span-8' : 'lg:col-span-12'} transition-all duration-300`}>
            <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs overflow-hidden">
              
              {/* Studio Canvas Status Bar */}
              <div className="px-5 py-3 border-b border-slate-100 dark:border-slate-800/80 flex items-center justify-between text-xs text-slate-500 bg-slate-50/50 dark:bg-[#0b101a]">
                <div className="flex items-center space-x-2">
                  <span className="w-2 h-2 rounded-full bg-emerald-500" />
                  <span className="font-medium text-slate-700 dark:text-slate-300">
                    Mode: {studioMode === 'view' ? 'Reader view' : 'Drag & drop edit panel'}
                  </span>
                </div>
                <div className="flex items-center space-x-3 text-[11px]">
                  <span>Sources: cases, events, benchmark</span>
                  <span>•</span>
                  <span>3 Custom widgets active</span>
                </div>
              </div>

              {/* AG Studio Container (strictly empty state if API has zero real rows) */}
              <div className="w-full h-[720px] relative">
                {hasStudioData ? (
                  <AgStudio<any>
                    data={studioData}
                    mode={studioMode}
                    theme={studioActiveTheme}
                    widgets={widgetsConfig}
                    initialState={initialState}
                    context={widgetContext}
                    style={{ width: '100%', height: '100%' }}
                  />
                ) : (
                  <div className="w-full h-full flex flex-col items-center justify-center p-8 text-center space-y-2 bg-slate-50/40 dark:bg-[#0b101a]/40">
                    <GoogleIcon name="dashboard_customize" size={32} className="text-slate-400 dark:text-slate-500" />
                    <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                      No telemetry sources available yet
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400 max-w-md">
                      AG Studio widgets populate directly from live exception cases, webhooks, and benchmark runs without synthetic placeholder rows. Seed cases or run a detector scan to begin.
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Stretch: Studio Agent Framework Assistant Panel */}
          {showAgent && (
            <div className="lg:col-span-4 h-[780px] sticky top-6">
              <OperationsAgentAssistant
                cases={casesList}
                events={eventsList}
                benchmark={benchmarkData}
                onClose={() => setShowAgent(false)}
              />
            </div>
          )}

        </div>

      </div>
    </AppLayout>
  );
}
