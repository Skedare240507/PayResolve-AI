import { Link } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';

export default function ServerErrorPage() {
  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-6 font-sans relative">
      <div className="absolute top-6 right-6">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-lg text-center space-y-6">
        {/* Error pill */}
        <div className="flex justify-center">
          <span className="inline-flex items-center text-xs font-medium px-3.5 py-1 rounded-full border border-rose-300 dark:border-rose-800 bg-rose-50/70 dark:bg-rose-950/30 text-rose-600 dark:text-rose-400">
            Error
          </span>
        </div>

        {/* 500 Bold Number */}
        <h1 className="text-7xl sm:text-8xl font-black text-[#0066cc] tracking-tight">
          500
        </h1>

        {/* Title & subtitle */}
        <div className="space-y-2">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Something broke on our side
          </h2>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed max-w-md mx-auto">
            Your data is safe. Webhooks are stored before processing, so nothing was lost. Try again, and quote this id if it keeps happening.
          </p>
        </div>

        {/* Incident ID pill */}
        <div className="flex justify-center">
          <div className="px-4 py-2 rounded-lg bg-[#edf3fa] dark:bg-[#131b26] border border-slate-200/60 dark:border-slate-800 text-xs font-mono text-slate-700 dark:text-slate-300">
            Incident id: 7f3a-91c2
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-center gap-3 pt-2">
          <button
            onClick={() => window.location.reload()}
            className="px-5 py-2.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all"
          >
            Try again
          </button>
          <Link
            to="/queue"
            className="px-5 py-2.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-xs cursor-pointer transition-all"
          >
            Go to the queue
          </Link>
        </div>
      </div>
    </div>
  );
}
