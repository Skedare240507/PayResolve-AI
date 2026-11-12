import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useAuth } from '../lib/authStore';
import { toast } from '../lib/toast';
import { GoogleIcon } from './GoogleIcon';

function formatCountdown(resetsAt: string | null | undefined, nowMs: number): string {
  if (!resetsAt) return '10:00';
  const targetMs = new Date(resetsAt).getTime();
  if (Number.isNaN(targetMs)) return '10:00';
  const remainingSeconds = Math.max(0, Math.floor((targetMs - nowMs) / 1000));
  const mins = Math.floor(remainingSeconds / 60);
  const secs = remainingSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function DemoFaultBanner() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isOperator = user?.role === 'operator';

  const { data: faults } = useQuery({
    queryKey: ['demo-faults'],
    queryFn: api.faults.get,
    enabled: Boolean(user),
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
  });

  const activeNames: string[] = [];
  if (faults?.failWebhookHandler) activeNames.push('Fail webhook handler');
  if (faults?.dropWebhook) activeNames.push('Drop webhook');
  if (faults?.alterOrderTotal) activeNames.push('Alter order total');
  if (faults?.duplicateDelivery) activeNames.push('Duplicate delivery');

  const isAnyActive = activeNames.length > 0;
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (!isAnyActive) return;
    const id = window.setInterval(() => {
      const current = Date.now();
      setNowMs(current);
      if (faults?.resetsAt && current >= new Date(faults.resetsAt).getTime()) {
        queryClient.invalidateQueries({ queryKey: ['demo-faults'] });
      }
    }, 1000);
    return () => window.clearInterval(id);
  }, [isAnyActive, faults?.resetsAt, queryClient]);

  const resetMutation = useMutation({
    mutationFn: api.faults.reset,
    onSuccess: (updated) => {
      queryClient.setQueryData(['demo-faults'], updated);
      toast.success('All demo fault switches reset to off.');
    },
    onError: (err: unknown) => {
      toast.error(err instanceof Error ? err.message : 'Failed to reset fault switches.');
    },
  });

  if (!user || !isAnyActive) {
    return null;
  }

  const countdown = formatCountdown(faults?.resetsAt, nowMs);

  return (
    <div
      role="status"
      className="mb-5 px-4 py-2.5 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800/80 flex flex-wrap items-center justify-between gap-3 text-xs text-amber-950 dark:text-amber-200 shadow-2xs"
    >
      <div className="flex items-center gap-2 min-w-0">
        <GoogleIcon name="warning" size={16} className="text-amber-600 dark:text-amber-400 shrink-0" />
        <span className="font-medium truncate">
          Demo fault active: <strong className="font-semibold">{activeNames.join(', ')}</strong>, resets in{' '}
          <span className="font-mono font-semibold">{countdown}</span>
        </span>
      </div>
      {isOperator && (
        <button
          type="button"
          onClick={() => resetMutation.mutate()}
          disabled={resetMutation.isPending}
          className="px-3 py-1 rounded-lg bg-amber-600 hover:bg-amber-700 active:scale-[0.98] text-white text-xs font-semibold cursor-pointer transition-all disabled:opacity-60 shrink-0"
        >
          {resetMutation.isPending ? 'Resetting...' : 'Reset all'}
        </button>
      )}
    </div>
  );
}
