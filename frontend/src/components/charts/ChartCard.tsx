import type { ReactNode } from 'react';
import { GoogleIcon } from '../GoogleIcon';
import { InfoTip, type MetricValues } from '../InfoTip';
import type { MetricId } from '../../lib/metricDefinitions';

export interface AccessibleTableColumn {
  key: string;
  label: string;
}

export interface ChartCardProps {
  title: string;
  description: string;
  metricId?: MetricId;
  metricValues?: MetricValues;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  isEmpty?: boolean;
  emptyMessage?: string;
  lastUpdated?: string | null;
  heightClassName?: string;
  accessibleColumns?: AccessibleTableColumn[];
  accessibleRows?: Array<Record<string, string | number>>;
  children: ReactNode;
}

function formatUpdatedTime(iso?: string | null): string {
  if (!iso) return 'Live telemetry';
  try {
    const dt = new Date(iso);
    if (Number.isNaN(dt.getTime())) return iso;
    return `Updated ${dt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  } catch {
    return 'Live telemetry';
  }
}

export function ChartCard({
  title,
  description,
  metricId,
  metricValues,
  isLoading = false,
  isError = false,
  errorMessage = 'Failed to load chart telemetry.',
  isEmpty = false,
  emptyMessage = 'No telemetry data recorded yet.',
  lastUpdated,
  heightClassName = 'h-64',
  accessibleColumns,
  accessibleRows,
  children,
}: ChartCardProps) {
  return (
    <section
      aria-label={title}
      className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0e1522] p-5 shadow-xs flex flex-col justify-between transition-colors"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">
              {title}
            </h3>
            {metricId && <InfoTip metricId={metricId} values={metricValues} size={16} />}
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 line-clamp-1">
            {description}
          </p>
        </div>
        <span className="shrink-0 text-[11px] font-mono text-slate-500 dark:text-slate-400 px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800/80 border border-slate-200/80 dark:border-slate-700/80">
          {formatUpdatedTime(lastUpdated)}
        </span>
      </div>

      <div className={`relative w-full ${heightClassName}`}>
        {isLoading ? (
          <div
            role="status"
            aria-label={`Loading ${title}`}
            className="w-full h-full rounded-lg bg-slate-100 dark:bg-slate-800/50 animate-pulse flex flex-col justify-end p-4 gap-2"
          >
            <div className="h-3/5 w-full flex items-end gap-3 px-2">
              <div className="flex-1 h-2/3 bg-slate-200 dark:bg-slate-700/70 rounded-t" />
              <div className="flex-1 h-full bg-slate-200 dark:bg-slate-700/70 rounded-t" />
              <div className="flex-1 h-1/2 bg-slate-200 dark:bg-slate-700/70 rounded-t" />
              <div className="flex-1 h-4/5 bg-slate-200 dark:bg-slate-700/70 rounded-t" />
            </div>
            <span className="sr-only">Loading {title}</span>
          </div>
        ) : isError ? (
          <div
            role="alert"
            className="w-full h-full rounded-lg border border-rose-200 dark:border-rose-900/50 bg-rose-50/50 dark:bg-rose-950/20 flex flex-col items-center justify-center p-4 text-center gap-1.5"
          >
            <GoogleIcon name="error_outline" size={22} className="text-rose-600 dark:text-rose-400" />
            <p className="text-xs font-medium text-rose-700 dark:text-rose-300">{errorMessage}</p>
          </div>
        ) : isEmpty ? (
          <div className="w-full h-full rounded-lg border border-dashed border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/30 flex flex-col items-center justify-center p-4 text-center gap-1.5">
            <GoogleIcon name="bar_chart" size={24} className="text-slate-400 dark:text-slate-500" />
            <p className="text-xs font-medium text-slate-600 dark:text-slate-400">{emptyMessage}</p>
          </div>
        ) : (
          <div className="w-full h-full chart-fade-in">{children}</div>
        )}
      </div>

      {/* Visually hidden accessible data table mirroring exact chart numbers for screen readers */}
      {!isLoading && !isError && !isEmpty && accessibleColumns && accessibleRows && accessibleRows.length > 0 && (
        <table className="sr-only">
          <caption>{title} data summary</caption>
          <thead>
            <tr>
              {accessibleColumns.map((col) => (
                <th key={col.key} scope="col">
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {accessibleRows.map((row, idx) => (
              <tr key={idx}>
                {accessibleColumns.map((col) => (
                  <td key={col.key}>{row[col.key] ?? ''}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
