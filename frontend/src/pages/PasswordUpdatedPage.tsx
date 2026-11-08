import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';

export default function PasswordUpdatedPage() {
  const [seconds, setSeconds] = useState(3);
  const navigate = useNavigate();

  useEffect(() => {
    if (seconds <= 0) {
      navigate('/signin');
      return;
    }
    const timer = setInterval(() => {
      setSeconds((prev) => prev - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [seconds, navigate]);

  const progressPercent = Math.min(100, Math.max(0, ((3 - seconds) / 3) * 100));

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Password updated
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            You can sign in with your new password. Other sessions were signed out.
          </p>
        </div>

        <div className="space-y-3 pt-2">
          <div className="text-xs font-normal text-slate-600 dark:text-slate-400">
            Redirecting to sign in in {seconds} s
          </div>

          {/* Progress Bar */}
          <div className="w-full h-1 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-[#0066cc] transition-all duration-1000 ease-linear"
              style={{ width: `${Math.max(15, progressPercent)}%` }}
            />
          </div>
        </div>

        <div className="pt-2">
          <button
            onClick={() => navigate('/signin')}
            className="w-full py-2.5 px-4 rounded-lg bg-[#0066cc] hover:bg-blue-600 active:scale-[0.98] text-white font-medium text-sm shadow-sm cursor-pointer transition-all"
          >
            Sign in now
          </button>
        </div>
      </div>
    </AuthLayout>
  );
}
