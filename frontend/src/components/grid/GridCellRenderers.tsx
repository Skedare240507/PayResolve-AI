import { GoogleIcon } from '../GoogleIcon';
import { toast } from '../../lib/toast';

export function CopyableIdCell({
  value,
  copyValue,
  displayText,
}: {
  value?: string | null;
  copyValue?: string;
  displayText?: string;
}) {
  const text = displayText ?? value ?? '';
  const targetCopy = copyValue ?? value ?? '';
  if (!targetCopy) return <span className="text-slate-400">—</span>;

  return (
    <div className="flex items-center gap-1.5 group min-w-0 font-mono text-xs">
      {text && (
        <span title={text} className="truncate">
          {text}
        </span>
      )}
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          navigator.clipboard?.writeText(targetCopy);
          toast.info(`Copied: ${targetCopy}`);
        }}
        title={`Copy ${targetCopy}`}
        aria-label={`Copy ID ${targetCopy}`}
        className="opacity-70 sm:opacity-0 group-hover:opacity-100 focus-visible:opacity-100 hover:text-blue-600 dark:hover:text-blue-400 text-slate-400 p-0.5 rounded cursor-pointer transition-opacity shrink-0"
      >
        <GoogleIcon name="content_copy" size={13} />
      </button>
    </div>
  );
}

/**
 * Icon + Text pill for severity (never relies on color alone for accessibility).
 */
export function SeverityPillCell({ severity }: { severity?: string | null }) {
  const norm = (severity || 'Low').toLowerCase();
  if (norm === 'high') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-rose-300 dark:border-rose-800 bg-rose-50/80 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 text-xs font-medium">
        <GoogleIcon name="error" size={13} className="shrink-0" />
        <span>High</span>
      </span>
    );
  }
  if (norm === 'medium') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-amber-300 dark:border-amber-800 bg-amber-50/80 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 text-xs font-medium">
        <GoogleIcon name="warning" size={13} className="shrink-0" />
        <span>Medium</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-slate-300 dark:border-slate-700 bg-slate-100/80 dark:bg-slate-800/60 text-slate-700 dark:text-slate-300 text-xs font-medium">
      <GoogleIcon name="info" size={13} className="shrink-0" />
      <span>Low</span>
    </span>
  );
}

/**
 * Icon + Text pill for status (never relies on color alone for accessibility).
 */
export function StatusPillCell({ status }: { status?: string | null }) {
  const norm = (status || 'Pending').toLowerCase();

  if (['resolved', 'processed', 'approved', 'verified', 'executed', 'confirmed'].includes(norm)) {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-emerald-300 dark:border-emerald-800 bg-emerald-50/80 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 text-xs font-medium">
        <GoogleIcon name="check_circle" size={13} className="shrink-0" />
        <span>{status}</span>
      </span>
    );
  }

  if (['investigating', 'received', 'pending', 'awaiting approval'].includes(norm)) {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-blue-300 dark:border-blue-800 bg-blue-50/80 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300 text-xs font-medium">
        <GoogleIcon name="sync" size={13} className="shrink-0" />
        <span>{status}</span>
      </span>
    );
  }

  if (['open', 'proposed', 'duplicate', 'missing'].includes(norm)) {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-amber-300 dark:border-amber-800 bg-amber-50/80 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 text-xs font-medium">
        <GoogleIcon name="schedule" size={13} className="shrink-0" />
        <span>{status}</span>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-rose-300 dark:border-rose-800 bg-rose-50/80 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 text-xs font-medium">
      <GoogleIcon name="cancel" size={13} className="shrink-0" />
      <span>{status}</span>
    </span>
  );
}
