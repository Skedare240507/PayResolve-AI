import { useEffect, useState } from 'react';

interface LastUpdatedLabelProps {
  dataUpdatedAt: number;
  isFetching?: boolean;
  className?: string;
}

function formatUpdatedAge(dataUpdatedAt: number, now: number): string {
  if (!dataUpdatedAt || dataUpdatedAt <= 0) {
    return 'waiting for data';
  }
  const diffSec = Math.max(0, Math.floor((now - dataUpdatedAt) / 1000));
  if (diffSec < 5) {
    return 'updated just now';
  }
  if (diffSec < 60) {
    return `updated ${diffSec}s ago`;
  }
  const mins = Math.floor(diffSec / 60);
  return `updated ${mins}m ago`;
}

export function LastUpdatedLabel({
  dataUpdatedAt,
  isFetching = false,
  className = '',
}: LastUpdatedLabelProps) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <span
      className={`inline-flex items-center gap-1.5 text-[11px] font-medium text-slate-500 dark:text-slate-400 ${className}`}
      aria-live="polite"
    >
      <span
        className={`w-1.5 h-1.5 rounded-full ${
          isFetching
            ? 'bg-blue-500 animate-ping'
            : 'bg-emerald-500'
        }`}
      />
      <span>{isFetching && !dataUpdatedAt ? 'refreshing...' : formatUpdatedAge(dataUpdatedAt, now)}</span>
    </span>
  );
}
