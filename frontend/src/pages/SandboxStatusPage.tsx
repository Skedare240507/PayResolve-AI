import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AppLayout } from '../components/AppLayout';
import { GoogleIcon } from '../components/GoogleIcon';
import { CardSkeleton, ErrorState } from '../components/DataStates';
import { api, ApiError, type DiagnosticCheckDto } from '../lib/api';
import { useAuth } from '../lib/authStore';

function getCheckStyle(check: DiagnosticCheckDto): {
  icon: string;
  iconClass: string;
  badgeClass: string;
  label: string;
} {
  const status = (check.status || '').toUpperCase();
  if (status === 'FAIL') {
    return {
      icon: 'cancel',
      iconClass: 'text-rose-600 dark:text-rose-400',
      badgeClass: 'border-rose-300 dark:border-rose-700/70 text-rose-700 dark:text-rose-300 bg-rose-50 dark:bg-rose-950/30',
      label: 'FAIL',
    };
  }
  if (status === 'WARN') {
    return {
      icon: 'warning',
      iconClass: 'text-amber-600 dark:text-amber-400',
      badgeClass: 'border-amber-300 dark:border-amber-700/70 text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/30',
      label: 'WARN',
    };
  }
  if (status === 'INFO') {
    return {
      icon: 'info',
      iconClass: 'text-blue-600 dark:text-blue-400',
      badgeClass: 'border-blue-300 dark:border-blue-700/70 text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-950/30',
      label: 'INFO',
    };
  }
  return {
    icon: 'check_circle',
    iconClass: 'text-emerald-600 dark:text-emerald-400',
    badgeClass: 'border-emerald-300 dark:border-emerald-700/70 text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/30',
    label: 'PASS',
  };
}

export default function SandboxStatusPage() {
  const { user } = useAuth();
  const isOperator = user?.role === 'operator';

  const healthQuery = useQuery({
    queryKey: ['actuator-health'],
    queryFn: api.health.get,
    retry: 1,
  });

  const diagnosticsQuery = useQuery({
    queryKey: ['paypal-diagnostics'],
    queryFn: api.paypal.diagnostics,
    enabled: isOperator,
    retry: false,
  });

  const isForbidden =
    diagnosticsQuery.error instanceof ApiError && diagnosticsQuery.error.status === 403;

  const diag = diagnosticsQuery.data;

  return (
    <AppLayout>
      <div className="max-w-5xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
                Sandbox diagnostics
              </h1>
              <span
                className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-0.5 rounded-full border ${
                  healthQuery.data?.status === 'UP'
                    ? 'border-emerald-300 dark:border-emerald-700 text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/30'
                    : 'border-amber-300 dark:border-amber-700 text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/30'
                }`}
              >
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    healthQuery.data?.status === 'UP' ? 'bg-emerald-500' : 'bg-amber-500'
                  }`}
                />
                Backend {healthQuery.data?.status ?? (healthQuery.isLoading ? 'checking...' : 'unreachable')}
              </span>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Live checks for PayPal Sandbox OAuth credentials, registered webhook URL, events, and delivery health. No secrets are exposed.
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            {isOperator && (
              <button
                type="button"
                onClick={() => diagnosticsQuery.refetch()}
                disabled={diagnosticsQuery.isFetching}
                className="px-4 py-2 rounded-xl bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white text-xs font-semibold shadow-sm cursor-pointer transition-all flex items-center gap-1.5 disabled:opacity-60"
              >
                <GoogleIcon
                  name="refresh"
                  size={15}
                  className={diagnosticsQuery.isFetching ? 'animate-spin' : ''}
                />
                <span>{diagnosticsQuery.isFetching ? 'Checking...' : 'Re-run diagnostics'}</span>
              </button>
            )}
            <Link
              to="/shop"
              className="px-4 py-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#131b26] text-slate-700 dark:text-slate-300 text-xs font-medium hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
            >
              Demo Shop
            </Link>
          </div>
        </div>

        {/* Viewer Role Notice */}
        {!isOperator || isForbidden ? (
          <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-amber-200 dark:border-amber-800/70 p-6 space-y-3 shadow-xs">
            <div className="flex items-start gap-3">
              <div className="p-2 rounded-xl bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400 shrink-0">
                <GoogleIcon name="lock" size={20} />
              </div>
              <div className="space-y-1.5">
                <h2 className="text-sm font-bold text-slate-900 dark:text-white">
                  Operator role required for PayPal diagnostics
                </h2>
                <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                  Live credential verification and registered webhook inspection (<code className="font-mono text-xs px-1 py-0.5 rounded bg-slate-100 dark:bg-slate-800">GET /api/paypal/diagnostics</code>) are restricted to operators. You can still inspect existing cases, the evidence graph, and the event inbox in read-only mode.
                </p>
                <div className="pt-2 flex items-center gap-3">
                  <Link
                    to="/queue"
                    className="px-4 py-2 rounded-lg bg-[#0066cc] text-white text-xs font-semibold hover:bg-blue-600 transition-colors"
                  >
                    Go to Exception Queue
                  </Link>
                  <Link
                    to="/sign-in"
                    className="px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-800 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                  >
                    Switch account
                  </Link>
                </div>
              </div>
            </div>
          </div>
        ) : diagnosticsQuery.isLoading ? (
          <CardSkeleton count={3} />
        ) : diagnosticsQuery.isError ? (
          <ErrorState
            message={
              diagnosticsQuery.error instanceof Error
                ? diagnosticsQuery.error.message
                : 'Failed to run PayPal diagnostics.'
            }
            onRetry={() => diagnosticsQuery.refetch()}
          />
        ) : diag ? (
          <>
            {/* Overview Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 space-y-1.5 shadow-2xs">
                <span className="text-xs text-slate-500 dark:text-slate-400">Overall status</span>
                <div className="flex items-center gap-2">
                  <GoogleIcon
                    name={diag.overallOk ? 'verified' : 'error_outline'}
                    size={22}
                    className={
                      diag.overallOk
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : 'text-rose-600 dark:text-rose-400'
                    }
                  />
                  <span className="text-base font-bold text-slate-900 dark:text-white">
                    {diag.overallOk ? 'Ready for Sandbox payments' : 'Configuration needs fixes'}
                  </span>
                </div>
                <p className="text-[11px] font-mono text-slate-400">
                  Checked {diag.checkedAt ? new Date(diag.checkedAt).toLocaleTimeString([], { hour12: false }) : 'now'}
                </p>
              </div>

              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 space-y-1.5 shadow-2xs">
                <span className="text-xs text-slate-500 dark:text-slate-400">Expected webhook target</span>
                <div className="text-xs font-mono font-semibold text-slate-900 dark:text-slate-100 break-all">
                  {diag.expectedWebhookUrl || 'Not configured'}
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Last received:{' '}
                  <span className="font-mono">
                    {diag.lastWebhookReceivedAt
                      ? new Date(diag.lastWebhookReceivedAt).toLocaleString()
                      : 'none yet'}
                  </span>
                </p>
              </div>

              <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 p-5 space-y-1.5 shadow-2xs">
                <span className="text-xs text-slate-500 dark:text-slate-400">Sandbox app &amp; business account</span>
                <div className="text-xs font-mono font-semibold text-slate-900 dark:text-slate-100 break-all">
                  {diag.businessAccount || diag.appId || 'Unavailable (OAuth failed)'}
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  {diag.appId ? `PayPal App ID: ${diag.appId}` : 'Connect valid credentials to read App ID'}
                </p>
              </div>
            </div>

            {/* Diagnostics Checklist */}
            <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs overflow-hidden">
              <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-slate-900 dark:text-white">
                    PayPal Sandbox Checklist
                  </h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Every check shows clear fix hints when configuration or webhook registration fails.
                  </p>
                </div>
              </div>

              <ul className="divide-y divide-slate-100 dark:divide-slate-800/80" data-testid="diagnostics-checklist">
                {(diag.checks || []).map((check) => {
                  const style = getCheckStyle(check);
                  return (
                    <li key={check.id || check.label} className="p-5 sm:px-6 space-y-2.5">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-start gap-3">
                          <GoogleIcon
                            name={style.icon}
                            size={20}
                            className={`${style.iconClass} mt-0.5 shrink-0`}
                          />
                          <div className="space-y-1">
                            <div className="text-sm font-semibold text-slate-900 dark:text-white">
                              {check.label}
                            </div>
                            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
                              {check.detail}
                            </p>
                          </div>
                        </div>

                        <span
                          className={`shrink-0 text-[11px] font-mono font-bold px-2.5 py-0.5 rounded-full border ${style.badgeClass}`}
                        >
                          {style.label}
                        </span>
                      </div>

                      {check.fixHint && (
                        <div
                          className={`ml-8 p-3 rounded-xl border text-xs leading-relaxed flex items-start gap-2 ${
                            check.passed
                              ? 'bg-slate-50 dark:bg-[#131b26] border-slate-200/80 dark:border-slate-800 text-slate-600 dark:text-slate-300'
                              : 'bg-amber-50/80 dark:bg-amber-950/20 border-amber-200 dark:border-amber-800/60 text-amber-900 dark:text-amber-200'
                          }`}
                        >
                          <GoogleIcon
                            name="lightbulb"
                            size={15}
                            className={
                              check.passed
                                ? 'text-blue-500 shrink-0 mt-0.5'
                                : 'text-amber-600 dark:text-amber-400 shrink-0 mt-0.5'
                            }
                          />
                          <div>
                            <span className="font-semibold">
                              {check.passed ? 'Note: ' : 'How to fix: '}
                            </span>
                            {check.fixHint}
                          </div>
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          </>
        ) : null}
      </div>
    </AppLayout>
  );
}
