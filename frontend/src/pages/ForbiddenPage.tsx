import { Link, useNavigate } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';

export default function ForbiddenPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-6 font-sans relative">
      <div className="absolute top-6 right-6">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-md text-center space-y-6">
        {/* Viewer access pill */}
        <div className="flex justify-center">
          <span className="inline-flex items-center text-xs font-medium px-3.5 py-1 rounded-full border border-amber-300 dark:border-amber-700/70 bg-amber-50/50 dark:bg-amber-950/30 text-amber-800 dark:text-amber-300">
            Viewer access
          </span>
        </div>

        {/* 403 Bold Number */}
        <h1 className="text-7xl sm:text-8xl font-black text-[#0066cc] tracking-tight">
          403
        </h1>

        {/* Title & subtitle */}
        <div className="space-y-2">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            This needs an operator
          </h2>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed max-w-sm mx-auto">
            You are signed in as a viewer, so approvals are read-only. Ask an operator to approve this action, or sign in with an operator account.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-center gap-3 pt-2">
          <Link
            to="/case/PR-0142"
            className="px-5 py-2.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all"
          >
            Back to Case PR-0142
          </Link>
          <button
            onClick={() => navigate('/signin')}
            className="px-5 py-2.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-xs cursor-pointer transition-all"
          >
            Switch account
          </button>
        </div>
      </div>
    </div>
  );
}
