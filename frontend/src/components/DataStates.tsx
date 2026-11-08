import React from 'react';
import { GoogleIcon } from './GoogleIcon';
import { PayResolveSpinner } from './PayResolveSpinner';

export const TableSkeleton: React.FC<{ rows?: number; columns?: number }> = ({
  rows = 5,
  columns = 4,
}) => {
  return (
    <div className="w-full space-y-3 p-4 animate-pulse" role="status" aria-label="Loading content">
      <div className="h-8 bg-slate-200 dark:bg-slate-800 rounded-md w-full mb-4" />
      {Array.from({ length: rows }).map((_, rIdx) => (
        <div key={rIdx} className="flex items-center space-x-4 py-2 border-b border-slate-100 dark:border-slate-800/60">
          {Array.from({ length: columns }).map((_, cIdx) => (
            <div
              key={cIdx}
              className="h-4 bg-slate-200 dark:bg-slate-800/80 rounded"
              style={{ width: `${Math.max(15, 100 / columns - (cIdx * 5))}%` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
};

export const CardSkeleton: React.FC<{ count?: number }> = ({ count = 4 }) => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 animate-pulse">
      {Array.from({ length: count }).map((_, idx) => (
        <div
          key={idx}
          className="bg-white dark:bg-[#0e1522] p-5 rounded-2xl border border-slate-200/90 dark:border-slate-800/90 shadow-2xs space-y-3"
        >
          <div className="h-3 bg-slate-200 dark:bg-slate-800 rounded w-1/2" />
          <div className="h-8 bg-slate-200 dark:bg-slate-800 rounded w-3/4" />
          <div className="h-3 bg-slate-200 dark:bg-slate-800 rounded w-2/3" />
        </div>
      ))}
    </div>
  );
};

export const EmptyState: React.FC<{
  title?: string;
  description?: string;
  icon?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}> = ({
  title = 'No records found',
  description = 'There are no active items matching this view.',
  icon = 'inbox',
  action,
}) => {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center space-y-3">
      <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800/60 flex items-center justify-center text-slate-400">
        <GoogleIcon name={icon} className="text-2xl" />
      </div>
      <h3 className="text-base font-semibold text-slate-900 dark:text-white">
        {title}
      </h3>
      <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm">
        {description}
      </p>
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="mt-2 px-4 py-2 text-xs font-medium rounded-lg bg-[#0066cc] hover:bg-blue-600 text-white shadow-sm cursor-pointer transition-colors"
        >
          {action.label}
        </button>
      )}
    </div>
  );
};

export const ErrorState: React.FC<{
  title?: string;
  message?: string;
  onRetry?: () => void;
}> = ({
  title = 'Unable to load data',
  message = 'An unexpected error occurred while communicating with the PayResolve backend.',
  onRetry,
}) => {
  return (
    <div className="flex flex-col items-center justify-center py-12 px-4 text-center space-y-3 bg-red-50/50 dark:bg-red-950/20 border border-red-200/80 dark:border-red-900/40 rounded-2xl">
      <div className="w-10 h-10 rounded-full bg-red-100 dark:bg-red-900/40 flex items-center justify-center text-red-600 dark:text-red-400">
        <GoogleIcon name="error" className="text-2xl" />
      </div>
      <h3 className="text-sm font-bold text-red-900 dark:text-red-200">
        {title}
      </h3>
      <p className="text-xs text-red-700/80 dark:text-red-300/80 max-w-md">
        {message}
      </p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 px-3.5 py-1.5 text-xs font-medium rounded-lg bg-red-600 hover:bg-red-700 text-white shadow-2xs cursor-pointer transition-colors"
        >
          Try again
        </button>
      )}
    </div>
  );
};

export const PageLoadingState: React.FC<{ label?: string }> = ({ label = 'Loading PayResolve workspace...' }) => {
  return (
    <div className="min-h-[50vh] flex items-center justify-center">
      <PayResolveSpinner size={44} label={label} />
    </div>
  );
};

