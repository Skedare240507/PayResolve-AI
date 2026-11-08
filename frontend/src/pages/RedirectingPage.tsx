import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PayResolveMark } from '../components/PayResolveLogo';
import { ThemeToggle } from '../components/ThemeToggle';

export default function RedirectingPage() {
  const navigate = useNavigate();
  const [progress, setProgress] = useState(25);

  useEffect(() => {
    const timer = setTimeout(() => {
      setProgress(100);
      setTimeout(() => {
        navigate('/case/PR-0142');
      }, 500);
    }, 1500);

    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <div className="min-h-screen bg-[#f8fafc] dark:bg-[#080c14] text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-6 font-sans relative">
      <div className="absolute top-6 right-6">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-md text-center space-y-6">
        {/* Centered Mark */}
        <div className="flex justify-center">
          <PayResolveMark size={56} />
        </div>

        {/* Title & Subtitle */}
        <div className="space-y-2">
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Taking you to Case PR-0142
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400">
            Checking your session and loading the evidence graph.
          </p>
        </div>

        {/* Progress bar */}
        <div className="w-64 mx-auto h-1 bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-[#0066cc] transition-all duration-1000 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>

        {/* Fallback link */}
        <div className="pt-2">
          <Link
            to="/case/PR-0142"
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            Not redirected? Go now
          </Link>
        </div>
      </div>
    </div>
  );
}

