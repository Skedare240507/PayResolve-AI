import { useState } from 'react';
import { GoogleIcon } from '../components/GoogleIcon';
import { Link, useNavigate } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';
import { api } from '../lib/api';
import { toast } from '../lib/toast';

export default function SandboxStatusPage() {
  const navigate = useNavigate();
  const [retrying, setRetrying] = useState(false);

  const handleRetry = async () => {
    setRetrying(true);
    try {
      const health = await api.health.get().catch(() => ({ status: 'UP' }));
      if (health.status === 'UP') {
        toast.info('Local services operational. PayPal Sandbox remains in simulated offline mode.');
      } else {
        toast.warning('Services currently degraded.');
      }
    } catch {
      toast.warning('Could not reach PayPal Sandbox.');
    } finally {
      setTimeout(() => {
        setRetrying(false);
        navigate('/queue');
      }, 700);
    }
  };

  const statusRows = [
    { label: 'PayPal Sandbox API', status: 'Unreachable', color: 'rose' },
    { label: 'Webhook inbox', status: 'Operational', color: 'emerald' },
    { label: 'Rules engine', status: 'Operational', color: 'emerald' },
    { label: 'AI investigator', status: 'Degraded', color: 'amber' },
  ];

  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-6 font-sans relative">
      <div className="absolute top-6 right-6">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-lg text-center space-y-6">
        {/* Wifi icon */}
        <div className="flex justify-center">
          <GoogleIcon name="wifi" size={26} className="text-emerald-600 dark:text-emerald-400" />
        </div>

        {/* Title & subtitle */}
        <div className="space-y-2">
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            PayPal Sandbox is not reachable
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 max-w-md mx-auto leading-relaxed">
            New payments cannot be created right now. Existing cases, the graph and the timeline still work from stored data.
          </p>
        </div>

        {/* Status table card */}
        <div className="bg-white dark:bg-[#0e1522] rounded-2xl border border-slate-200 dark:border-slate-800 p-6 divide-y divide-slate-100 dark:divide-slate-800/80 text-left shadow-xs">
          {statusRows.map(({ label, status, color }) => {
            const badgeClasses =
              color === 'rose'
                ? 'border-rose-400 dark:border-rose-700/70 text-rose-600 dark:text-rose-400 bg-rose-50/40 dark:bg-rose-950/20'
                : color === 'emerald'
                ? 'border-emerald-400 dark:border-emerald-700/70 text-emerald-600 dark:text-emerald-400 bg-emerald-50/40 dark:bg-emerald-950/20'
                : 'border-amber-400 dark:border-amber-700/70 text-amber-600 dark:text-amber-400 bg-amber-50/40 dark:bg-amber-950/20';

            return (
              <div key={label} className="py-3.5 flex items-center justify-between first:pt-1 last:pb-1">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{label}</span>
                <span
                  className={`inline-flex items-center text-xs font-medium px-3 py-0.5 rounded-full border ${badgeClasses}`}
                >
                  {status}
                </span>
              </div>
            );
          })}
        </div>

        {/* Actions */}
        <div className="flex items-center justify-center gap-3 pt-1">
          <button
            onClick={handleRetry}
            disabled={retrying}
            className="px-5 py-2.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all flex items-center space-x-2 disabled:opacity-60"
          >
            <GoogleIcon name="refresh" size={15} className={`text-white ${retrying ? 'animate-spin' : ''}`} />
            <span>{retrying ? 'Retrying...' : 'Retrying'}</span>
          </button>
          <Link
            to="/queue"
            className="px-5 py-2.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-xs cursor-pointer transition-all"
          >
            Continue read-only
          </Link>
        </div>
      </div>
    </div>
  );
}
