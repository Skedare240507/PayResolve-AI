import { Link } from 'react-router-dom';
import { GoogleIcon } from './GoogleIcon';
import { InfoTip } from './InfoTip';
import type { OrderTraceDto, OrderTraceStepDto } from '../lib/api';
import type { MetricId } from '../lib/metricDefinitions';

const STEP_METRICS: Record<string, { metricId: MetricId }> = {
  ORDER_CREATED: { metricId: 'shop.trace.orderCreated' },
  PAYPAL_ORDER_CREATED: { metricId: 'shop.trace.paypalOrderCreated' },
  BUYER_APPROVED: { metricId: 'shop.trace.buyerApproved' },
  CAPTURED_API: { metricId: 'shop.trace.capturedApi' },
  WEBHOOK_RECEIVED: { metricId: 'shop.trace.webhookReceived' },
  WEBHOOK_PROCESSED: { metricId: 'shop.trace.webhookProcessed' },
  ORDER_MARKED_PAID: { metricId: 'shop.trace.orderMarkedPaid' },
  CASE_OPENED: { metricId: 'shop.trace.caseOpened' },
};

function formatTraceTimestamp(step: OrderTraceStepDto): string {
  if (!step.timestamp) {
    return step.timestampLabel || 'not yet';
  }
  try {
    const d = new Date(step.timestamp);
    return d.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
  } catch {
    return step.timestamp;
  }
}

function getStepStyle(step: OrderTraceStepDto): {
  dotBg: string;
  icon: string;
  iconColor: string;
  badgeClass: string;
} {
  const status = (step.status || '').toUpperCase();
  if (status === 'FAILED' || status === 'UNVERIFIED') {
    return {
      dotBg: 'bg-rose-50 dark:bg-rose-950/50 border-rose-300 dark:border-rose-700',
      icon: 'error',
      iconColor: 'text-rose-600 dark:text-rose-400',
      badgeClass: 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200 dark:border-rose-800',
    };
  }
  if (status === 'OPENED' || status === 'RETRYING') {
    return {
      dotBg: 'bg-amber-50 dark:bg-amber-950/50 border-amber-300 dark:border-amber-700',
      icon: status === 'OPENED' ? 'warning' : 'sync',
      iconColor: 'text-amber-600 dark:text-amber-400',
      badgeClass: 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-800',
    };
  }
  if (step.completed || status === 'COMPLETED' || status === 'VERIFIED') {
    return {
      dotBg: 'bg-emerald-50 dark:bg-emerald-950/50 border-emerald-300 dark:border-emerald-700',
      icon: 'check_circle',
      iconColor: 'text-emerald-600 dark:text-emerald-400',
      badgeClass: 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800',
    };
  }
  return {
    dotBg: 'bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-slate-700',
    icon: 'radio_button_unchecked',
    iconColor: 'text-slate-400 dark:text-slate-500',
    badgeClass: 'bg-slate-50 dark:bg-slate-800/60 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-700',
  };
}

interface PaymentTraceStepperProps {
  trace?: OrderTraceDto | null;
  isLoading?: boolean;
  isError?: boolean;
  compact?: boolean;
}

export function PaymentTraceStepper({
  trace,
  isLoading = false,
  isError = false,
  compact = false,
}: PaymentTraceStepperProps) {
  if (isLoading) {
    return (
      <div className="space-y-2.5 py-2 animate-pulse" aria-label="Loading payment trace">
        {[1, 2, 3, 4].map((n) => (
          <div key={n} className="h-8 rounded-lg bg-slate-100 dark:bg-slate-800/70" />
        ))}
      </div>
    );
  }

  if (isError || !trace) {
    return (
      <div className="p-3 rounded-xl bg-rose-50/60 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-800/60 text-xs text-rose-700 dark:text-rose-300">
        Unable to load payment trace for this order.
      </div>
    );
  }

  const steps = trace.steps || [];

  return (
    <div className="text-left" data-testid="payment-trace-stepper">
      <ol className={`relative ${compact ? 'space-y-2.5' : 'space-y-3'}`}>
        {steps.map((step, index) => {
          const isLast = index === steps.length - 1;
          const style = getStepStyle(step);
          const metricCfg = step.id ? STEP_METRICS[step.id] : undefined;
          const timeText = formatTraceTimestamp(step);

          return (
            <li key={step.id || index} className="relative flex gap-3">
              {!isLast && (
                <span
                  aria-hidden="true"
                  className="absolute left-[13px] top-7 bottom-[-12px] w-px bg-slate-200 dark:bg-slate-700/80"
                />
              )}

              <div
                className={`relative z-10 flex items-center justify-center rounded-full border shrink-0 ${
                  compact ? 'w-7 h-7' : 'w-7 h-7'
                } ${style.dotBg}`}
              >
                <GoogleIcon name={style.icon} size={15} className={style.iconColor} />
              </div>

              <div className="flex-1 min-w-0 pb-0.5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span
                      className={`font-semibold text-slate-900 dark:text-slate-100 ${
                        compact ? 'text-xs' : 'text-xs sm:text-sm'
                      }`}
                    >
                      {step.label}
                    </span>

                    {step.id === 'WEBHOOK_RECEIVED' && step.completed && (
                      <span
                        className={`text-[10px] font-mono px-1.5 py-0.5 rounded-full border ${style.badgeClass}`}
                      >
                        {step.status === 'VERIFIED' ? 'verified' : 'unverified'}
                      </span>
                    )}

                    {(step.status === 'FAILED' || step.status === 'RETRYING' || step.status === 'OPENED') && (
                      <span
                        className={`text-[10px] font-mono px-1.5 py-0.5 rounded-full border ${style.badgeClass}`}
                      >
                        {step.status.toLowerCase()}
                      </span>
                    )}

                    {metricCfg && (
                      <InfoTip
                        metricId={metricCfg.metricId}
                        size={13}
                        values={{
                          customDetail: step.timestamp
                            ? `${step.label}: ${step.timestamp}${step.detail ? ` | ${step.detail}` : ''}`
                            : `${step.label}: not yet${step.detail ? ` | ${step.detail}` : ''}`,
                        }}
                      />
                    )}
                  </div>

                  <span
                    title={step.timestamp || 'not yet'}
                    className={`shrink-0 font-mono text-[11px] ${
                      step.timestamp
                        ? 'text-slate-700 dark:text-slate-300 font-medium'
                        : 'text-slate-400 dark:text-slate-500 italic'
                    }`}
                  >
                    {timeText}
                  </span>
                </div>

                {step.detail && (
                  <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug break-words">
                    {step.detail}
                  </p>
                )}

                {step.id === 'CASE_OPENED' && step.externalRef && (
                  <Link
                    to={`/case/${step.externalRef}`}
                    className="inline-flex items-center gap-1 mt-1 text-[11px] font-semibold text-blue-600 dark:text-blue-400 hover:underline"
                  >
                    <span>Investigate {step.externalRef}</span>
                    <GoogleIcon name="arrow_forward" size={12} />
                  </Link>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
