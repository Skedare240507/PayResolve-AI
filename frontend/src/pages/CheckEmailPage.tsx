import { useState, useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AuthLayout } from '../components/AuthLayout';
import { requestPasswordReset } from '../lib/authStore';
import { toast } from '../lib/toast';
import { GoogleIcon } from '../components/GoogleIcon';

export default function CheckEmailPage() {
  const [countdown, setCountdown] = useState(28);
  const [searchParams] = useSearchParams();
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const email = searchParams.get('email') || 'operator@payresolve.dev';
  const token = searchParams.get('token');

  useEffect(() => {
    intervalRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  const handleResend = async () => {
    try {
      await requestPasswordReset(email);
      toast.success('Reset link resent successfully.');
    } catch {
      // ignore
    }
    setCountdown(30);
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
  };

  return (
    <AuthLayout>
      <div className="space-y-6">
        <div>
          <h2 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            Check your email
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">
            If an account exists for {email}, a reset link is on its way.
          </p>
        </div>

        {/* Demo Mode On-Screen Reset Notice */}
        {token && (
          <div className="p-3.5 rounded-xl bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800/80 space-y-1.5">
            <div className="flex items-center space-x-2 text-xs font-semibold text-blue-800 dark:text-blue-300">
              <GoogleIcon name="info" className="text-sm" />
              <span>Demo Mode: Direct Reset Link</span>
            </div>
            <p className="text-xs text-blue-700 dark:text-blue-400 leading-relaxed">
              No live email service is configured in sandbox. You can reset your password directly:
            </p>
            <div className="pt-1">
              <Link
                to={`/reset-password?token=${encodeURIComponent(token)}&email=${encodeURIComponent(email)}`}
                className="inline-flex items-center text-xs font-semibold text-blue-600 dark:text-blue-300 hover:underline"
              >
                Proceed to reset password &rarr;
              </Link>
            </div>
          </div>
        )}

        {/* Central Illustration Area */}
        <div className="bg-[#edf3fa] dark:bg-[#131b26] rounded-2xl p-8 flex flex-col items-center justify-center space-y-5 border border-slate-200/60 dark:border-slate-800/80">
          <div className="w-56 h-36 bg-black dark:bg-[#070b12] rounded-2xl shadow-xl flex items-center justify-center relative border border-slate-800/40">
            {/* Minimal email envelope glyph */}
            <div className="w-16 h-11 border-2 border-slate-400/50 rounded-lg flex items-center justify-center relative overflow-hidden">
              <div className="absolute top-0 inset-x-0 h-4 border-b border-slate-400/50 transform rotate-12 origin-top-left" />
              <div className="absolute top-0 inset-x-0 h-4 border-b border-slate-400/50 transform -rotate-12 origin-top-right" />
            </div>
          </div>
          <p className="text-xs text-slate-600 dark:text-slate-400 font-medium">
            It can take a minute. Check spam too.
          </p>
        </div>

        {/* Resend button box */}
        <div>
          <button
            type="button"
            disabled={countdown > 0}
            onClick={handleResend}
            className="w-full py-2.5 px-4 rounded-lg bg-white dark:bg-[#131b26] text-slate-600 dark:text-slate-300 font-medium text-xs sm:text-sm border border-slate-200 dark:border-slate-800 cursor-pointer disabled:cursor-default text-center hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
          >
            {countdown > 0 ? `Resend in ${countdown} s` : 'Resend reset link'}
          </button>
        </div>

        <div className="pt-1">
          <Link
            to="/forgot-password"
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            Use a different email
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}
