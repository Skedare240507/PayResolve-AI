import { Link, useNavigate } from 'react-router-dom';
import { PayResolveLogo } from '../components/PayResolveLogo';
import { ThemeToggle } from '../components/ThemeToggle';

export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-6 font-sans relative">
      <div className="absolute top-6 right-6">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-md text-center space-y-6">
        {/* Top Logo */}
        <div className="flex justify-center">
          <Link to="/" className="cursor-pointer">
            <PayResolveLogo markSize={32} />
          </Link>
        </div>

        {/* 404 Bold Number */}
        <h1 className="text-7xl sm:text-8xl font-black text-[#0066cc] tracking-tight">
          404
        </h1>

        {/* Title & subtitle */}
        <div className="space-y-2">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            We could not find that page
          </h2>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed max-w-sm mx-auto">
            The case or page may have been deleted, or the link is incomplete. Cases are never removed, so check the id.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-center gap-3 pt-2">
          <Link
            to="/queue"
            className="px-5 py-2.5 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-xs sm:text-sm shadow-sm cursor-pointer transition-all"
          >
            Go to the queue
          </Link>
          <button
            onClick={() => navigate('/queue')}
            className="px-5 py-2.5 rounded-lg bg-white dark:bg-[#131b26] border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] font-medium text-xs sm:text-sm shadow-xs cursor-pointer transition-all"
          >
            Search cases
          </button>
        </div>
      </div>
    </div>
  );
}
