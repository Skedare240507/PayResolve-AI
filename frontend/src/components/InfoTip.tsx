import { useState, useRef, useId, useEffect } from 'react';
import {
  useFloating,
  autoUpdate,
  offset,
  flip,
  shift,
  useHover,
  useFocus,
  useDismiss,
  useRole,
  useInteractions,
  FloatingPortal,
} from '@floating-ui/react';
import { GoogleIcon } from './GoogleIcon';
import { METRICS, type MetricId } from '../lib/metricDefinitions';

export interface MetricValues {
  value?: string | number | null;
  numerator?: number | null;
  denominator?: number | null;
  n?: number | null;
  generatedAt?: string | null;
  customDetail?: string | null;
}

interface InfoTipProps {
  metricId: MetricId;
  values?: MetricValues;
  className?: string;
  size?: number;
}

export function InfoTip({
  metricId,
  values,
  className = '',
  size = 18,
}: InfoTipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const def = METRICS[metricId];
  const tooltipId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);

  // If a metricId is passed that is not in the registry, fail visibly in dev/test
  if (!def) {
    throw new Error(`[InfoTip] Unknown metricId: "${metricId}". Must be registered in METRIC_REGISTRY.`);
  }

  const { refs, floatingStyles, context } = useFloating({
    open: isOpen,
    onOpenChange: setIsOpen,
    placement: 'top',
    whileElementsMounted: autoUpdate,
    middleware: [
      offset(8),
      flip({ fallbackAxisSideDirection: 'start', padding: 8 }),
      shift({ padding: 8 }),
    ],
  });

  const hover = useHover(context, {
    delay: { open: 150, close: 100 },
    move: false,
  });
  const focus = useFocus(context);
  const dismiss = useDismiss(context, {
    escapeKey: true,
    outsidePress: true,
  });
  const role = useRole(context, { role: 'tooltip' });

  const { getReferenceProps, getFloatingProps } = useInteractions([
    hover,
    focus,
    dismiss,
    role,
  ]);

  // Handle click / tap toggle
  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    e.preventDefault();
    setIsOpen((prev) => !prev);
  };

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen]);

  // Construct worked real numbers line if values are supplied
  const renderWorkedLine = () => {
    if (!values) return null;

    if (values.customDetail) {
      return (
        <div className="font-mono text-[11px] text-blue-600 dark:text-blue-400 bg-blue-50/80 dark:bg-blue-950/40 p-2 rounded-lg border border-blue-200/50 dark:border-blue-900/40">
          {values.customDetail}
        </div>
      );
    }

    if (values.numerator != null && values.denominator != null) {
      const pct = values.denominator > 0
        ? ((values.numerator / values.denominator) * 100).toFixed(1)
        : '0.0';
      const sampleText = values.n != null ? ` (sample size n = ${values.n})` : '';
      return (
        <div className="font-mono text-[11px] text-blue-600 dark:text-blue-400 bg-blue-50/80 dark:bg-blue-950/40 p-2 rounded-lg border border-blue-200/50 dark:border-blue-900/40">
          Real numbers: {values.numerator} / {values.denominator} = {values.value ?? `${pct}%`}{sampleText}
        </div>
      );
    }

    if (values.value != null) {
      return (
        <div className="font-mono text-[11px] text-blue-600 dark:text-blue-400 bg-blue-50/80 dark:bg-blue-950/40 p-2 rounded-lg border border-blue-200/50 dark:border-blue-900/40">
          Current value: {values.value}
          {values.n != null ? ` (n = ${values.n})` : ''}
        </div>
      );
    }

    return null;
  };

  return (
    <span className="inline-flex items-center align-middle">
      <button
        type="button"
        ref={(node) => {
          triggerRef.current = node;
          refs.setReference(node);
        }}
        {...getReferenceProps({
          onClick: handleClick,
          'aria-label': `How is ${def.label} calculated?`,
          'aria-describedby': isOpen ? tooltipId : undefined,
          className: `inline-flex items-center justify-center p-1.5 -m-1 rounded-full text-slate-400 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-1 transition-colors cursor-pointer min-w-[32px] min-h-[32px] ${className}`,
        })}
      >
        <GoogleIcon name="info" size={size} />
      </button>

      {isOpen && (
        <FloatingPortal>
          <div
            ref={refs.setFloating}
            id={tooltipId}
            style={floatingStyles}
            {...getFloatingProps({
              className:
                'z-[9999] max-w-[320px] w-screen px-4 py-3.5 rounded-xl text-left shadow-xl border border-slate-200/90 dark:border-slate-800 bg-white/95 dark:bg-[#0c1320]/95 backdrop-blur-md text-slate-800 dark:text-slate-100 transition-opacity duration-150 animate-in fade-in zoom-in-95 pointer-events-auto',
            })}
          >
            {/* Header / Title */}
            <div className="flex items-center justify-between gap-2 pb-2 mb-2 border-b border-slate-100 dark:border-slate-800/80">
              <span className="font-semibold text-xs text-slate-900 dark:text-white tracking-tight">
                {def.label}
              </span>
              <span className="text-[10px] font-mono text-slate-400 dark:text-slate-400">
                {def.id}
              </span>
            </div>

            <div className="space-y-2.5 text-xs">
              {/* What it means */}
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-400 mb-0.5">
                  What it means
                </div>
                <p className="text-slate-600 dark:text-slate-300 text-[11px] leading-relaxed">
                  {def.meaning}
                </p>
              </div>

              {/* How it is calculated */}
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-400 mb-0.5">
                  How it is calculated
                </div>
                <p className="text-slate-600 dark:text-slate-300 text-[11px] leading-relaxed mb-1.5">
                  {def.formula}
                </p>
                {renderWorkedLine()}
              </div>

              {/* Data: Source & Window */}
              <div>
                <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-400 mb-0.5">
                  Data
                </div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 space-y-0.5">
                  <div>
                    <span className="font-medium text-slate-700 dark:text-slate-300">Source:</span> {def.source}
                  </div>
                  <div>
                    <span className="font-medium text-slate-700 dark:text-slate-300">Window:</span> {def.window}
                  </div>
                </div>
              </div>

              {/* Caveats / Note */}
              {def.caveats && (
                <div className="pt-1.5 border-t border-slate-100 dark:border-slate-800/80">
                  <div className="text-[10px] font-semibold uppercase tracking-wider text-amber-600 dark:text-amber-400 mb-0.5">
                    Note
                  </div>
                  <p className="text-[11px] text-amber-700 dark:text-amber-300/90 leading-relaxed">
                    {def.caveats}
                  </p>
                </div>
              )}
            </div>
          </div>
        </FloatingPortal>
      )}
    </span>
  );
}

